package com.tinku.identidad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.CertificadoService;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T02 — CAP para tutores de menores (DT6, FR-ID-021 a 026, BR-CAP-01/02, PT1, PT10), de punta
 * a punta con base real: subida, revisión con control de rol, habilitación calculada, los
 * puntos fail-closed y el vencimiento que cancela solo las clases con menores.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CapIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
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
    @Autowired ReservaRepository reservaRepository;
    @Autowired CertificadoService certificadoService;
    @Autowired JdbcTemplate jdbc;

    @MockBean ReembolsoProveedor reembolso;

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final byte[] PDF = "%PDF-1.4 certificado de antecedentes".getBytes();

    private Usuario usuario(TipoUsuario tipo, Usuario adultoResponsable) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 43_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Nombre" + CONTADOR.get());
        u.setApellido("Paz");
        u.setFechaNacimiento(tipo == TipoUsuario.MENOR ? LocalDate.now().minusYears(12) : LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        if (tipo == TipoUsuario.MENOR) {
            u.setCapacidadEstudiante(true);
            u.setAdultoResponsable(adultoResponsable);
        }
        if (tipo == TipoUsuario.TUTOR) {
            u.setActivoParaMatching(true);
        }
        return usuarioRepository.save(u);
    }

    private Usuario usuario(TipoUsuario tipo) {
        return usuario(tipo, null);
    }

    private Usuario admin(RolAdmin rol) {
        Usuario u = usuario(TipoUsuario.ADULTO);
        Admin fila = new Admin();
        fila.setUsuario(u);
        fila.setRol(rol);
        adminRepository.save(fila);
        return u;
    }

    private String token(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    private ResultActions subir(Usuario tutor, byte[] archivo) throws Exception {
        return mvc.perform(multipart("/api/tutores/antecedentes-penales")
                .file(new MockMultipartFile("datos", "", MediaType.APPLICATION_JSON_VALUE,
                        objectMapper.writeValueAsBytes(Map.of("fechaEmision", LocalDate.now().minusDays(10).toString()))))
                .file(new MockMultipartFile("archivo", "cap.pdf", "application/pdf", archivo))
                .header("Authorization", "Bearer " + token(tutor)));
    }

    private UUID capSubido(Usuario tutor) throws Exception {
        String json = subir(tutor, PDF).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private ResultActions revisar(Usuario quien, UUID capId, String accion, String categoria) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("accion", accion);
        body.put("categoria", categoria);
        return mvc.perform(patch("/api/admin/moderacion/antecedentes-penales/{id}", capId)
                .header("Authorization", "Bearer " + token(quien))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private ResultActions autorizar(Usuario ar, Usuario menor, Usuario tutor) throws Exception {
        return mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token(ar))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "menorId", menor.getId().toString(), "tutorId", tutor.getId().toString()))));
    }

    @Test
    void subida_pdf201_yNoPdf422() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        subir(tutor, PDF).andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        subir(usuario(TipoUsuario.TUTOR), "<html>no soy un pdf</html>".getBytes())
                .andExpect(status().isUnprocessableEntity());
    }

    /** TR3: la versión retirada dejaba a cualquiera aprobar su propio CAP. */
    @Test
    void revision_soloModeracion() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        UUID capId = capSubido(tutor);

        revisar(tutor, capId, "APROBAR", null).andExpect(status().isForbidden());
        revisar(admin(RolAdmin.SOPORTE_FINANCIERO), capId, "APROBAR", null).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/moderacion/antecedentes-penales/{id}/archivo", capId)
                        .header("Authorization", "Bearer " + token(tutor)))
                .andExpect(status().isForbidden());

        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        mvc.perform(get("/api/admin/moderacion/antecedentes-penales/{id}/archivo", capId)
                        .header("Authorization", "Bearer " + token(moderador)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Security-Policy", "sandbox"));
        revisar(moderador, capId, "APROBAR", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));

        // FR-ID-026 visible en el perfil público; nada más del CAP (T03 §2.2).
        mvc.perform(get("/api/tutores/{id}", tutor.getId()).header("Authorization", "Bearer " + token(usuario(TipoUsuario.ADULTO))))
                .andExpect(jsonPath("$.habilitadoParaMenores").value(true));
    }

    /** BR-CAP-01 rechaza aunque se pida aprobar; BR-CAP-02/PT1: otro antecedente → revisión legal, no habilita. */
    @Test
    void revision_categoriaMandaSobreLaAccion() throws Exception {
        Usuario moderador = admin(RolAdmin.MODERACION_SEGURIDAD);
        Usuario t1 = usuario(TipoUsuario.TUTOR);
        revisar(moderador, capSubido(t1), "APROBAR", "INTEGRIDAD_SEXUAL")
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
        Usuario t2 = usuario(TipoUsuario.TUTOR);
        revisar(moderador, capSubido(t2), "APROBAR", "OTRO")
                .andExpect(jsonPath("$.estado").value("EN_REVISION_LEGAL"));

        assertThat(certificadoService.habilitadoParaMenores(t1.getId())).isFalse();
        assertThat(certificadoService.habilitadoParaMenores(t2.getId())).isFalse();
    }

    /** FR-ID-026: autorizar un Tutor para un menor exige CAP aprobado y vigente. */
    @Test
    void autorizar_sinCap409_conCap201() throws Exception {
        Usuario ar = usuario(TipoUsuario.ADULTO);
        Usuario menor = usuario(TipoUsuario.MENOR, ar);
        Usuario tutor = usuario(TipoUsuario.TUTOR);

        autorizar(ar, menor, tutor).andExpect(status().isConflict());

        revisar(admin(RolAdmin.MODERACION_SEGURIDAD), capSubido(tutor), "APROBAR", null).andExpect(status().isOk());
        autorizar(ar, menor, tutor).andExpect(status().is2xxSuccessful());
    }

    /** FR-ID-025 + PT10: el CAP vence → se cancelan solo las clases futuras con menores; el Tutor
     *  sigue visible para adultos (TR2). */
    @Test
    void vencimiento_cancelaSoloClasesConMenores_yNoTocaElMatching() {
        Usuario tutor = usuario(TipoUsuario.TUTOR);
        jdbc.update("""
                INSERT INTO identidad.certificados_antecedentes_penales
                    (tutor_id, archivo_url, fecha_emision, vence_at, estado)
                VALUES (?, 'test/cap.pdf', CURRENT_DATE - 400, CURRENT_DATE - 35, 'APROBADO')
                """, tutor.getId());
        Usuario ar = usuario(TipoUsuario.ADULTO);
        Reserva conMenor = reserva(ar, usuario(TipoUsuario.MENOR, ar), tutor, 3);
        Usuario adulto = usuario(TipoUsuario.ADULTO);
        Reserva conAdulto = reserva(adulto, adulto, tutor, 5);

        certificadoService.marcarVencidos();

        Reserva r1 = reservaRepository.findById(conMenor.getId()).orElseThrow();
        assertThat(r1.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r1.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.CAP_VENCIDO);
        assertThat(reservaRepository.findById(conAdulto.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(usuarioRepository.findById(tutor.getId()).orElseThrow().isActivoParaMatching()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin.notificaciones WHERE destinatario_id = ? AND tipo = 'CLASE_CANCELADA_TUTOR_SIN_HABILITACION'",
                Integer.class, ar.getId())).isEqualTo(1);
    }

    private Reserva reserva(Usuario pagador, Usuario beneficiario, Usuario tutor, int enDias) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(beneficiario);
        r.setTutor(tutor);
        r.setHorario(Instant.now().plusSeconds(enDias * 86_400L));
        r.setHorarioFin(r.getHorario().plusSeconds(3600));
        r.setDuracionMinutos(60);
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(EstadoReserva.CONFIRMADA);
        return reservaRepository.save(r);
    }
}
