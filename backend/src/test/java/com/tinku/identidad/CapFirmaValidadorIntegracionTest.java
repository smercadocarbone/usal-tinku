package com.tinku.identidad;

import tools.jackson.databind.ObjectMapper;
import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoFirmaCap;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.ValidadorFirmasPdf;
import com.tinku.identidad.port.ValidadorFirmasPdf.EstadoValidacion;
import com.tinku.identidad.port.ValidadorFirmasPdf.Firma;
import com.tinku.identidad.port.ValidadorFirmasPdf.Resultado;
import com.tinku.identidad.port.ValidadorNoDisponibleException;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.VerificacionFirmaCapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-M1-10 — la firma digital del CAP se verifica con el validador oficial
 * (validadordefirmas.gob.ar) a través de una cola persistida que manda los pedidos de a uno y
 * espaciados. Sin firma válida del Registro Nacional de Reincidencia, el CAP no se puede aprobar.
 * El validador está simulado: acá se prueba la cola, el espaciado y la regla de aprobación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "tinku.cap.validador.habilitado=true",
        // el job no corre solo durante el test: cada paso de la cola se dispara a mano
        "tinku.cap.validador.intervalo-segundos=86400"
})
class CapFirmaValidadorIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired CertificadoAntecedentesPenalesRepository capRepo;
    @Autowired VerificacionFirmaCapService verificacion;

    @MockitoBean ValidadorFirmasPdf validador;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @BeforeEach
    void limpiarCola() {
        // cada test arranca con la cola vacía: los CAP de otros tests no compiten por el turno
        capRepo.findAll().forEach(c -> {
            c.setFirmaEstado(EstadoFirmaCap.NO_VERIFICADA);
            capRepo.save(c);
        });
        reset(validador);
    }

    // ---- helpers ----

    private Usuario usuario(TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 44_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Nombre" + CONTADOR.get());
        u.setApellido("Firma");
        u.setFechaNacimiento(LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
        }
        if (tipo == TipoUsuario.TUTOR) {
            u.setActivoParaMatching(true);
        }
        return usuarioRepository.save(u);
    }

    private Usuario moderador() {
        Usuario u = usuario(TipoUsuario.ADULTO);
        Admin fila = new Admin();
        fila.setUsuario(u);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);
        return u;
    }

    private String token(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    private byte[] pdf() {
        return ("%PDF-1.4 certificado " + CONTADOR.incrementAndGet()).getBytes();
    }

    private static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    private UUID subir(Usuario tutor, byte[] archivo) throws Exception {
        String json = mvc.perform(multipart("/api/tutores/antecedentes-penales")
                        .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(Map.of("fechaEmision",
                                        LocalDate.now().minusDays(10).toString()))))
                        .file(new MockMultipartFile("archivo", "cap.pdf", "application/pdf", archivo))
                        .header("Authorization", "Bearer " + token(tutor)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private ResultActions revisar(Usuario admin, UUID capId, String accion) throws Exception {
        return mvc.perform(patch("/api/admin/moderacion/antecedentes-penales/{id}", capId)
                .header("Authorization", "Bearer " + token(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accion\":\"" + accion + "\"}"));
    }

    private CertificadoAntecedentesPenales cap(UUID id) {
        return capRepo.findById(id).orElseThrow();
    }

    private static Resultado valido(String sha, String organizacion) {
        return new Resultado(EstadoValidacion.TERMINADO, true, "", sha,
                List.of(new Firma(true, "FUNCIONARIO DE PRUEBA", organizacion, "Registro Nacional de Reincidencia")));
    }

    // ---- tests ----

    @Test
    void alSubir_quedaEnCola_yNoSePuedeAprobarSinFirmaVerificada() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());

        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.PENDIENTE);
        revisar(moderador(), id, "APROBAR")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("firma")));
        assertThat(cap(id).getEstado().name()).isEqualTo("PENDIENTE");
    }

    @Test
    void firmaValidaDeReincidencia_habilitaLaAprobacion() throws Exception {
        byte[] archivo = pdf();
        UUID id = subir(usuario(TipoUsuario.TUTOR), archivo);
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenReturn("pedido-1");

        verificacion.procesarSiguiente();
        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.EN_PROCESO);
        verify(validador, times(1)).enviar(archivo);

        when(validador.consultar("pedido-1")).thenReturn(valido(sha256(archivo), "Ministerio de Justicia"));
        verificacion.procesarSiguiente();

        CertificadoAntecedentesPenales c = cap(id);
        assertThat(c.getFirmaEstado()).isEqualTo(EstadoFirmaCap.VALIDA);
        assertThat(c.getFirmaFirmante()).contains("FUNCIONARIO DE PRUEBA");
        assertThat(c.getFirmaVerificadaAt()).isNotNull();
        revisar(moderador(), id, "APROBAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void firmaInvalida_bloqueaAprobar_peroSePuedeRechazar() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenReturn("pedido-2");
        when(validador.consultar("pedido-2")).thenReturn(new Resultado(EstadoValidacion.TERMINADO, false,
                "El pdf no contiene firmas que verificar. ", "abc", List.of()));

        verificacion.procesarSiguiente();
        verificacion.procesarSiguiente();

        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.INVALIDA);
        assertThat(cap(id).getFirmaDetalle()).contains("no contiene firmas");
        Usuario admin = moderador();
        revisar(admin, id, "APROBAR").andExpect(status().isConflict());
        revisar(admin, id, "RECHAZAR").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
    }

    @Test
    void firmaValidaDeOtroFirmante_noSeReconoce() throws Exception {
        byte[] archivo = pdf();
        UUID id = subir(usuario(TipoUsuario.TUTOR), archivo);
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenReturn("pedido-3");
        when(validador.consultar("pedido-3")).thenReturn(new Resultado(EstadoValidacion.TERMINADO, true, "",
                sha256(archivo), List.of(new Firma(true, "UNA PERSONA", "Empresa SA", "Ventas"))));

        verificacion.procesarSiguiente();
        verificacion.procesarSiguiente();

        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.FIRMANTE_NO_RECONOCIDO);
        revisar(moderador(), id, "APROBAR").andExpect(status().isConflict());
    }

    @Test
    void elValidadorDevuelveOtroArchivo_noSeDaPorValido() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenReturn("pedido-4");
        when(validador.consultar("pedido-4")).thenReturn(valido("0".repeat(64), "Reincidencia"));

        verificacion.procesarSiguiente();
        verificacion.procesarSiguiente();

        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.INVALIDA);
    }

    @Test
    void conLaColaDelSitioLlena_noSeManda() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());
        when(validador.pedidosEnCola()).thenReturn(300);

        verificacion.procesarSiguiente();

        verify(validador, never()).enviar(any());
        assertThat(cap(id).getFirmaEstado()).isEqualTo(EstadoFirmaCap.PENDIENTE);
    }

    @Test
    void cadaPaso_mandaComoMucho_unPedido() throws Exception {
        UUID primero = subir(usuario(TipoUsuario.TUTOR), pdf());
        UUID segundo = subir(usuario(TipoUsuario.TUTOR), pdf());
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenReturn("pedido-a", "pedido-b");
        when(validador.consultar(anyString())).thenReturn(
                new Resultado(EstadoValidacion.EN_COLA, false, "", null, List.of()));

        verificacion.procesarSiguiente();
        verify(validador, times(1)).enviar(any());
        assertThat(cap(primero).getFirmaEstado()).isEqualTo(EstadoFirmaCap.EN_PROCESO);
        assertThat(cap(segundo).getFirmaEstado()).isEqualTo(EstadoFirmaCap.PENDIENTE);

        // mientras haya un pedido en curso, el paso siguiente solo lo consulta: no manda otro
        verificacion.procesarSiguiente();
        verify(validador, times(1)).enviar(any());
        verify(validador, times(1)).consultar("pedido-a");
    }

    @Test
    void siElValidadorFalla_reintentaMasTarde_conEspera() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());
        when(validador.pedidosEnCola()).thenReturn(0);
        when(validador.enviar(any())).thenThrow(new ValidadorNoDisponibleException("conexión cortada"));

        verificacion.procesarSiguiente();

        CertificadoAntecedentesPenales c = cap(id);
        assertThat(c.getFirmaEstado()).isEqualTo(EstadoFirmaCap.PENDIENTE);
        assertThat(c.getFirmaIntentos()).isEqualTo(1);
        assertThat(c.getFirmaProximoIntentoAt()).isAfter(Instant.now());

        // todavía no le toca: el paso siguiente no vuelve a intentar
        verificacion.procesarSiguiente();
        verify(validador, times(1)).enviar(any());
    }

    @Test
    void elModeradorPuedeVolverAVerificar() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());
        CertificadoAntecedentesPenales c = cap(id);
        c.setFirmaEstado(EstadoFirmaCap.ERROR);
        c.setFirmaIntentos(6);
        capRepo.save(c);

        mvc.perform(post("/api/admin/moderacion/antecedentes-penales/{id}/verificar-firma", id)
                        .header("Authorization", "Bearer " + token(moderador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firmaEstado").value("PENDIENTE"));
        assertThat(cap(id).getFirmaIntentos()).isZero();
    }

    @Test
    void conElValidadorEncendido_elCapTieneQueSerElPdfOriginal() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        mvc.perform(multipart("/api/tutores/antecedentes-penales")
                        .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(Map.of("fechaEmision",
                                        LocalDate.now().minusDays(10).toString()))))
                        .file(new MockMultipartFile("archivo", "cap.png", "image/png", png))
                        .header("Authorization", "Bearer " + token(usuario(TipoUsuario.TUTOR))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("PDF original")));
    }

    @Test
    void laColaDelModerador_muestraElEstadoDeLaFirma() throws Exception {
        UUID id = subir(usuario(TipoUsuario.TUTOR), pdf());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/admin/moderacion/antecedentes-penales")
                        .header("Authorization", "Bearer " + token(moderador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].firmaEstado").value("PENDIENTE"));
    }
}
