package com.tinku.identidad;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.VerificarDniRequest;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.port.NotificadorResetPassword;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.CredencialNoPendienteException;
import com.tinku.identidad.service.CredencialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de T-M1-13: cada Historia de Usuario del Spec de M1
 * (US-1..US-6) se ejercita de punta a punta sobre el stack real (HTTP +
 * Spring Security + JPA + Flyway + PostgreSQL via Testcontainers).
 *
 * El OCR (ADR-M1-01) se mockea a nivel de puerto {@link OcrService}: el
 * Tesseract real y el {@code StubOcrService} (resultados fijos para la foto
 * de un adulto) no bastan para cubrir los casos de edad de MENOR ni para
 * variar DNI entre registros. Así, cada test controla DNI y fecha de
 * nacimiento extraídos para ejercitar adulto/tutor/menor/edad/duplicado.
 *
 * Se usa MockMvc (flujo HTTP completo) contra el contexto Spring completo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class IdentidadFlujosIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;

    @Autowired org.springframework.jdbc.core.JdbcTemplate capJdbc; // T02: CAP vigente de los tutores de menores
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CredencialAcademicaRepository credencialRepo;
    @Autowired CredencialService credencialService;

    @MockitoBean OcrService ocrService;
    @MockitoBean NotificadorResetPassword notificadorResetPassword;
    @org.springframework.beans.factory.annotation.Value("${tinku.jwt.secret}") String jwtSecret;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void programarOcr() {
        // Valor por defecto: documento legible; cada test sobreescribe el
        // resultado que necesita.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
    }

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    // ------------------------------------------------ helpers

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3});
    }

    private MvcResult registrarAdulto(String dni, String nombre, String apellido, boolean capEst,
                                      boolean capAr) throws Exception {
        return mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, capEst, capAr)))
                        .file(foto()))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String registrarAdultoYToken(String dni, String nombre, String apellido,
                                         boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        registrarAdulto(dni, nombre, apellido, true, capAr);
        return login(dni);
    }

    /** Registra (y loguea) un Tutor de verdad, necesario para cargar credencial/CAP. */
    private String registrarTutorYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    // ------------------------------------------------ US-1: adulto

    @Test
    void us1_adultoSeRegistra_yLoguea_yQuedaActivo() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("12345678", "Ana", "Gomez", LocalDate.of(1990, 5, 15)));

        registrarAdulto("12345678", "Ana", "Gomez", true, false);

        Usuario u = usuarioPorDni("12345678");
        assertThat(u.getTipo()).isEqualTo(TipoUsuario.ADULTO);
        assertThat(u.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(u.isCapacidadEstudiante()).isTrue();
        assertThat(u.isCapacidadAdultoResponsable()).isFalse();
        assertThat(u.getEmail()).isEqualTo("12345678@tinku.test");

        // El adulto puede loguearse con su DNI+password.
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest("12345678", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(res.getResponse().getContentAsString()).contains("token");
    }

    @Test
    void us1_cuentaSuspendida_loginRechazado_403() throws Exception {
        // Auditoría 2026-09-18: regresión del bug donde AuthService.login no
        // chequeaba estadoCuenta — una cuenta SUSPENDIDA por sanción de M9
        // (kill-switch, denuncia fundada) podía loguearse con normalidad.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("11223344", "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        registrarAdulto("11223344", "Ana", "Gomez", true, false);

        Usuario u = usuarioPorDni("11223344");
        u.setEstadoCuenta(EstadoCuenta.SUSPENDIDA);
        usuarioRepository.save(u);

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest("11223344", PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void us1_rechazaRegistroPorDniDuplicado_cuandoElOcrExtraeUnDniYaRegistrado() throws Exception {
        // Primer adulto: el OCR extrae 87654321.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("87654321", "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        registrarAdulto("87654321", "Ana", "Gomez", true, false);

        // Segundo intento con el MISMO DNI que el documento real (87654321), que
        // ya está registrado: la unicidad es contra el DNI EXTRAÍDO
        // (FR-ID-001/018) -> 409. Declara el mismo número, si declarara uno
        // distinto al del documento caería antes en "datos no coinciden" (422).
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("87654321", "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                "87654321", "Ana", "Gomez", LocalDate.of(1990, 5, 15),
                                "87654321@tinku.test", PASSWORD, true, false)))
                        .file(foto()))
                .andExpect(status().isConflict());
    }

    @Test
    void us1_rechazaRegistroPorEdadMenorDe18() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("11111111", "Tomas", "Lopez", LocalDate.of(2012, 6, 1))); // 14 anios

        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                "11111111", "Tomas", "Lopez", LocalDate.of(2012, 6, 1),
                                "11111111@tinku.test", PASSWORD, true, false)))
                        .file(foto()))
                .andExpect(status().isForbidden()); // EdadInsuficienteException -> 403
    }

    // ------------------------------------------------ verificación previa del DNI (wizard)

    @Test
    void verificarDni_ok_noCreaCuentaPorqueEsSoloLaCompuertaPrevia() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("42424242", "Ana", "Gomez", LocalDate.of(1985, 3, 10)));

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "42424242", "Ana", "Gomez", LocalDate.of(1985, 3, 10))))
                        .file(foto()))
                .andExpect(status().isNoContent());

        // La cuenta se crea recién en el alta final (email/password aún no se pidieron).
        assertThat(usuarioRepository.findByDni("42424242")).isEmpty();
    }

    @Test
    void verificarDni_403_cuandoElOcrDetectaMenorDeEdad_yNoCreaCuenta() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("43434343", "Tomas", "Lopez", LocalDate.of(2012, 6, 1)));

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "43434343", "Tomas", "Lopez", LocalDate.of(2012, 6, 1))))
                        .file(foto()))
                .andExpect(status().isForbidden());
        assertThat(usuarioRepository.findByDni("43434343")).isEmpty();
    }

    @Test
    void verificarDni_422_cuandoLaFechaDeclaradaNoCoincideConLaDelDocumento() throws Exception {
        // El OCR leyó el documento y su fecha de nacimiento es otra: el
        // rechazo es "datos no coinciden" (422), NUNCA "menor de edad" (403)
        // aunque la fecha del documento corresponda a un menor.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("45555555", "Tomas", "Lopez", LocalDate.of(2012, 6, 1)));

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "45555555", "Tomas", "Lopez", LocalDate.of(1985, 3, 10))))
                        .file(foto()))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "45555555", "Tomas", "Lopez", LocalDate.of(2012, 6, 1))))
                        .file(foto()))
                .andExpect(status().isForbidden()); // con la fecha correcta, sí es menor
        assertThat(usuarioRepository.findByDni("45555555")).isEmpty();
    }

    @Test
    void verificarDni_422_cuandoElDniDeclaradoNoCoincideConElDelDocumento() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("46666666", "Ana", "Gomez", LocalDate.of(1985, 3, 10)));

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "12345678", "Ana", "Gomez", LocalDate.of(1985, 3, 10))))
                        .file(foto()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(usuarioRepository.findByDni("46666666")).isEmpty();
    }

    @Test
    void verificarDni_503_cuandoElOcrNoEstaDisponible_yNoConsumeReintentos() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenThrow(new com.tinku.identidad.service.OcrNoDisponibleException());

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "47777777", "Ana", "Gomez", LocalDate.of(1985, 3, 10))))
                        .file(foto()))
                .andExpect(status().isServiceUnavailable());
        assertThat(usuarioRepository.findByDni("47777777")).isEmpty();
    }

    @Test
    void verificarDni_409_cuandoElDniYaTieneCuenta() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("45454545", "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        registrarAdulto("45454545", "Ana", "Gomez", true, false);

        mockMvc.perform(multipart("/api/usuarios/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "45454545", "Ana", "Gomez", LocalDate.of(1990, 5, 15))))
                        .file(foto()))
                .andExpect(status().isConflict());
    }

    @Test
    void us2_verificarDniDeTutor_ok_yNoCreaCuenta() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("46464646", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1)));

        mockMvc.perform(multipart("/api/tutores/verificar-dni")
                        .file(jsonPart("datos", new VerificarDniRequest(
                                "46464646", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1))))
                        .file(foto()))
                .andExpect(status().isNoContent());

        assertThat(usuarioRepository.findByDni("46464646")).isEmpty();
    }

    // ------------------------------------------------ US-2: tutor

    @Test
    void us2_tutorSeRegistra() throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("22222222", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1)));

        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                "22222222", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1),
                                "22222222@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());

        assertThat(usuarioPorDni("22222222").getTipo()).isEqualTo(TipoUsuario.TUTOR);
    }

    @Test
    void registroTutor_sinEmail_responde400ConElCampo() throws Exception {
        // UX-02 B2: un body inválido le llegaba al usuario como 403 ("no
        // autorizado") — spring reenviaba MethodArgumentNotValidException a
        // /error, que no está en el permitAll de SecurityConfig, así que la
        // cadena de seguridad lo cortaba. Un 400 de validación no puede
        // convertirse en "no autorizado".
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                "22222222", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1),
                                null, PASSWORD)))
                        .file(foto()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.campos.email").exists());
    }

    // ------------------------------------------------ US-3: menor (por su Adulto Responsable)

    @Test
    @org.springframework.transaction.annotation.Transactional
    void us3_menorSeRegistraPorSuAdultoResponsable_autenticado() throws Exception {
        String token = registrarAdultoYToken("33333333", "Maria", "Perez", true);

        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("44444444", "Sofia", "Perez", LocalDate.of(2015, 7, 20)));

        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroMenorRequest(
                                "44444444", "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        Usuario menor = usuarioPorDni("44444444");
        assertThat(menor.getTipo()).isEqualTo(TipoUsuario.MENOR);
        assertThat(menor.getAdultoResponsable().getDni()).isEqualTo("33333333");
        assertThat(menor.isCapacidadAdultoResponsable()).isFalse();
    }

    @Test
    void us3_listarMenores_soloLosDelPropioAdultoResponsable() throws Exception {
        // Auditoría 2026-09-18 (gap del frontend): antes solo había alta (POST)
        // y baja por id (DELETE), sin forma de listar los menores ya cargados.
        String tokenAr1 = registrarAdultoYToken("60000001", "Marta", "Ruiz", true);
        String tokenAr2 = registrarAdultoYToken("60000002", "Nora", "Diaz", true);

        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("60000011", "Tomas", "Ruiz", LocalDate.of(2016, 3, 10)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroMenorRequest(
                                "60000011", "Tomas", "Ruiz", LocalDate.of(2016, 3, 10),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr1))
                .andExpect(status().isCreated());

        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("60000012", "Iara", "Diaz", LocalDate.of(2017, 8, 2)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroMenorRequest(
                                "60000012", "Iara", "Diaz", LocalDate.of(2017, 8, 2),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr2))
                .andExpect(status().isCreated());

        // AR1 solo ve a Tomás, nunca a Iara (que es de AR2).
        mockMvc.perform(get("/api/usuarios/menores")
                        .header("Authorization", "Bearer " + tokenAr1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Tomas"));
    }

    // ------------------------------------------------ US-4: credencial + backoff escalado

    @Test
    void us4_credencialRechazadaAlTercerIntento_disparaBackoff_ySiguienteCargaDa429() throws Exception {
        String token = registrarTutorYToken("55555555", "Diego", "Mendez");

        // Carga inicial -> PENDIENTE, intento 1.
        UUID cred1 = cargarCredencial(token, 201);
        credencialService.marcarRechazada(cred1, null);

        UUID cred2 = cargarCredencial(token, 201);
        assertEsIntento(cred2, 2);
        credencialService.marcarRechazada(cred2, null);

        UUID cred3 = cargarCredencial(token, 201);
        assertEsIntento(cred3, 3);
        credencialService.marcarRechazada(cred3, null); // dispara backoff (FR-ID-012)

        // Ya en espera escalada -> la carga siguiente responde 429.
        mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "titulo.pdf",
                                MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 credencial de prueba".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests()); // CredencialEnBackoffException -> 429
    }

    @Test
    void aud007_subidaDeCredencialQueNoEsPdfNiImagen_422() throws Exception {
        String token = registrarTutorYToken("55555577", "Rocio", "Diaz");
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        // El Content-Type declarado miente: lo que manda es el contenido real (magic bytes).
        mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "titulo.pdf", "application/pdf", html))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());
        assertThat(credencialRepo.findFirstByTutorIdOrderByCreatedAtDesc(
                usuarioPorDni("55555577").getId())).isEmpty();
    }

    @Test
    void aud007_subidaDeCredencialMayorAlLimite_413() throws Exception {
        String token = registrarTutorYToken("55555588", "Marta", "Ruiz");
        byte[] grande = new byte[6 * 1024 * 1024];
        System.arraycopy("%PDF-1.4".getBytes(), 0, grande, 0, 8); // PDF válido, solo que enorme

        mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "titulo.pdf", "application/pdf", grande))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void aud033_credencialYaRechazada_noSePuedeAprobarNiReRechazar() throws Exception {
        String token = registrarTutorYToken("55555566", "Lucia", "Paz");
        UUID cred = cargarCredencial(token, 201);
        credencialService.marcarRechazada(cred, null);

        assertThatThrownBy(() -> credencialService.marcarAprobada(cred, null))
                .isInstanceOf(CredencialNoPendienteException.class);
        assertThatThrownBy(() -> credencialService.marcarRechazada(cred, null))
                .isInstanceOf(CredencialNoPendienteException.class);
        assertThat(credencialRepo.findById(cred).orElseThrow().getEstado())
                .isEqualTo(EstadoCredencial.RECHAZADO);
    }

    private UUID cargarCredencial(String token, int expectedStatus) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "titulo.pdf",
                                MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 credencial de prueba".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private void assertEsIntento(UUID id, int esperado) {
        assertThat(credencialRepo.findById(id).orElseThrow().getNumeroIntento())
                .isEqualTo(esperado);
    }

    // ------------------------------------------------ US-5: autorización de tutor

    @Test
    void us5_adultoResponsableAutorizatutorParaSuMenor() throws Exception {
        String tokenAr = registrarAdultoYToken("66666666", "Laura", "Diaz", true);

        // Menor a cargo.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("77777777", "Leo", "Diaz", LocalDate.of(2016, 2, 2)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroMenorRequest(
                                "77777777", "Leo", "Diaz", LocalDate.of(2016, 2, 2),
                                PASSWORD, true, "v1")))
                        .file(foto()).header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());

        // Tutor a autorizar.
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("88888888", "Pablo", "Sosa", LocalDate.of(1988, 9, 9)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                "88888888", "Pablo", "Sosa", LocalDate.of(1988, 9, 9),
                                "88888888@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());

        UUID menorId = usuarioPorDni("77777777").getId();
        UUID tutorId = usuarioPorDni("88888888").getId();
        com.tinku.testsupport.CapVigente.para(capJdbc, tutorId); // FR-ID-026

        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------ US-4: credencial aprobada habilita matching

    @Test
    void us4_credencialAprobada_habilitaMatching_delTutor() throws Exception {
        String token = registrarTutorYToken("12121212", "Ramiro", "Vega");
        UUID credencialId = cargarCredencial(token, 201);
        assertThat(credencialRepo.findById(credencialId).orElseThrow()
                .getEstado()).isEqualTo(EstadoCredencial.PENDIENTE);

        credencialService.marcarAprobada(credencialId, null);

        assertThat(credencialRepo.findById(credencialId).orElseThrow()
                .getEstado()).isEqualTo(EstadoCredencial.APROBADO);
        // FR-ID-025 (heredado de CAP retirado): la credencial aprobada activa matching.
        assertThat(usuarioPorDni("12121212").isActivoParaMatching()).isTrue();
    }

    // ------------------------------------------------ "Editar cuenta": email y contraseña

    @Test
    void editarCuenta_actualizaEmail_yRechazaDuplicado() throws Exception {
        String token1 = registrarAdultoYToken("20202020", "Marta", "Lopez", false);
        registrarAdultoYToken("21212121", "Nora", "Ruiz", false);

        mockMvc.perform(patch("/api/usuarios/me/email")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ActualizarEmailRequest("nuevo@tinku.test"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("nuevo@tinku.test"));

        // El email que ya usa OTRO usuario -> 409, sin importar quién lo pide.
        mockMvc.perform(patch("/api/usuarios/me/email")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ActualizarEmailRequest("21212121@tinku.test"))))
                .andExpect(status().isConflict());
    }

    @Test
    void editarCuenta_cambiaPassword_conActualCorrecta_yRechazaConIncorrecta() throws Exception {
        String token = registrarAdultoYToken("22222299", "Sol", "Aguirre", false);

        mockMvc.perform(patch("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.CambiarPasswordRequest("passwordMala", "nuevaPassword1"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.CambiarPasswordRequest(PASSWORD, "nuevaPassword1"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest("22222299", PASSWORD))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest("22222299", "nuevaPassword1"))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------ AUD-027 (FASE3-03): el JWT sin DNI y con cv

    @Test
    void aud027_tokenEmitido_noContieneElDni() throws Exception {
        String dni = "55556601";
        String token = registrarAdultoYToken(dni, "Ana", "Test", false);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain(dni);
        assertThat(payload).contains(usuarioPorDni(dni).getId().toString());
    }

    @Test
    void aud027_cambiarPassword_invalidaElTokenAnterior() throws Exception {
        String token = registrarAdultoYToken("55556602", "Ana", "Test", false);
        mockMvc.perform(patch("/api/usuarios/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"OtraClave123\"}"))
                .andExpect(status().isNoContent());
        // Sin sesión válida la app responde 403 (no configura authenticationEntryPoint).
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aud027_resetearPassword_invalidaElTokenAnterior() throws Exception {
        String token = registrarAdultoYToken("55556603", "Ana", "Test", false);
        mockMvc.perform(post("/api/usuarios/recuperar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.SolicitarResetPasswordRequest("55556603"))))
                .andExpect(status().isNoContent());
        ArgumentCaptor<String> tokenReset = ArgumentCaptor.forClass(String.class);
        verify(notificadorResetPassword, atLeastOnce()).notificar(any(), tokenReset.capture());

        mockMvc.perform(post("/api/usuarios/resetear-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ResetearPasswordRequest(tokenReset.getValue(), "OtraClave456"))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void aud027_tokenConSubjectDni_quedaSinSesionY403() throws Exception {
        registrarAdultoYToken("55556604", "Ana", "Test", false);
        // Un token con el formato viejo (sub = DNI, sin cv), bien firmado.
        String viejo = io.jsonwebtoken.Jwts.builder().subject("55556604").claim("tipo", "ADULTO")
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + viejo))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ "Olvidé mi contraseña"

    @Test
    void resetPassword_flujoCompleto_generaTokenYCambiaPassword() throws Exception {
        registrarAdultoYToken("23232323", "Tomas", "Bravo", false);

        mockMvc.perform(post("/api/usuarios/recuperar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.SolicitarResetPasswordRequest("23232323"))))
                .andExpect(status().isNoContent());

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificadorResetPassword).notificar(any(), tokenCaptor.capture());
        String tokenPlano = tokenCaptor.getValue();

        mockMvc.perform(post("/api/usuarios/resetear-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ResetearPasswordRequest(tokenPlano, "otraPassword2"))))
                .andExpect(status().isNoContent());

        // De un solo uso: reusar el mismo token ya no funciona.
        mockMvc.perform(post("/api/usuarios/resetear-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ResetearPasswordRequest(tokenPlano, "otraPassword3"))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest("23232323", "otraPassword2"))))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_dniInexistente_respondeIgualQueSiExistiera() throws Exception {
        // FR-ID-018: nunca confirmar/negar la existencia de un DNI.
        mockMvc.perform(post("/api/usuarios/recuperar-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.SolicitarResetPasswordRequest("99999999"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void resetPassword_tokenInexistente_422() throws Exception {
        mockMvc.perform(post("/api/usuarios/resetear-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.ResetearPasswordRequest("token-trucho", "otraPassword2"))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------ Estado real de la credencial (propio Tutor)

    @Test
    void credencialPropia_sinCargarNinguna_204() throws Exception {
        String token = registrarTutorYToken("24242424", "Rocio", "Paz");

        mockMvc.perform(get("/api/tutores/me/credencial")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void credencialPropia_conCargaPendiente_devuelveEstadoReal() throws Exception {
        String token = registrarTutorYToken("25252525", "Ivan", "Nunez");
        cargarCredencial(token, 201);

        mockMvc.perform(get("/api/tutores/me/credencial")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tieneAprobada").value(false));
    }

    @Test
    void credencialPropia_conAprobadaMasAntigua_yPendienteActual_diceQueEstaVerificado() throws Exception {
        // B12: el tutor Jorge verificado que vuelve a subir una credencial nueva.
        String token = registrarTutorYToken("26262626", "Jorge", "Estrada");
        UUID aprobada = cargarCredencial(token, 201);
        credencialService.marcarAprobada(aprobada, null);
        cargarCredencial(token, 201); // la nueva, en revisión

        mockMvc.perform(get("/api/tutores/me/credencial")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.tieneAprobada").value(true));
    }
}
