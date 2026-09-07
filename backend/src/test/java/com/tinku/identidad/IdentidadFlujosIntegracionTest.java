package com.tinku.identidad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.CertificadoService;
import com.tinku.identidad.service.CredencialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CertificadoAntecedentesPenalesRepository capRepo;
    @Autowired CredencialAcademicaRepository credencialRepo;
    @Autowired CredencialService credencialService;
    @Autowired CertificadoService certificadoService;

    @MockBean OcrService ocrService;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void programarOcr() {
        // Valor por defecto: documento legible; cada test sobreescribe el
        // resultado que necesita.
        when(ocrService.procesarDocumento(any()))
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
                                PASSWORD, capEst, capAr)))
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
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        registrarAdulto(dni, nombre, apellido, true, capAr);
        return login(dni);
    }

    /** Registra (y loguea) un Tutor de verdad, necesario para cargar credencial/CAP. */
    private String registrarTutorYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15), PASSWORD)))
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
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("12345678", "Ana", "Gomez", LocalDate.of(1985, 3, 10)));

        registrarAdulto("12345678", "Ana", "Gomez", true, false);

        Usuario u = usuarioPorDni("12345678");
        assertThat(u.getTipo()).isEqualTo(TipoUsuario.ADULTO);
        assertThat(u.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(u.isCapacidadEstudiante()).isTrue();
        assertThat(u.isCapacidadAdultoResponsable()).isFalse();

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
    void us1_rechazaRegistroPorDniDuplicado_cuandoElOcrExtraeUnDniYaRegistrado() throws Exception {
        // Primer adulto: el OCR extrae 87654321.
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("87654321", "Ana", "Gomez", LocalDate.of(1985, 3, 10)));
        registrarAdulto("87654321", "Ana", "Gomez", true, false);

        // Segundo intento con DNI declarado distinto, pero el OCR vuelve a
        // extraer el mismo 87654321 (documento real del mismo titular):
        // la unicidad es contra el DNI EXTRAÍDO (FR-ID-001/018) -> 409.
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("87654321", "Ana", "Gomez", LocalDate.of(1985, 3, 10)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                "99999999", "Ana", "Gomez", LocalDate.of(1985, 3, 10),
                                PASSWORD, true, false)))
                        .file(foto()))
                .andExpect(status().isConflict());
    }

    @Test
    void us1_rechazaRegistroPorEdadMenorDe18() throws Exception {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("11111111", "Tomas", "Lopez", LocalDate.of(2012, 6, 1))); // 14 anios

        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                "11111111", "Tomas", "Lopez", LocalDate.of(2012, 6, 1),
                                PASSWORD, true, false)))
                        .file(foto()))
                .andExpect(status().isForbidden()); // EdadInsuficienteException -> 403
    }

    // ------------------------------------------------ US-2: tutor

    @Test
    void us2_tutorSeRegistra() throws Exception {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("22222222", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1)));

        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                "22222222", "Carlos", "Ruiz", LocalDate.of(1980, 1, 1), PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());

        assertThat(usuarioPorDni("22222222").getTipo()).isEqualTo(TipoUsuario.TUTOR);
    }

    // ------------------------------------------------ US-3: menor (por su Adulto Responsable)

    @Test
    @org.springframework.transaction.annotation.Transactional
    void us3_menorSeRegistraPorSuAdultoResponsable_autenticado() throws Exception {
        String token = registrarAdultoYToken("33333333", "Maria", "Perez", true);

        when(ocrService.procesarDocumento(any()))
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
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{9, 9}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests()); // CredencialEnBackoffException -> 429
    }

    private UUID cargarCredencial(String token, int expectedStatus) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "titulo.pdf",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{9, 9}))
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
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("77777777", "Leo", "Diaz", LocalDate.of(2016, 2, 2)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroMenorRequest(
                                "77777777", "Leo", "Diaz", LocalDate.of(2016, 2, 2),
                                PASSWORD, true, "v1")))
                        .file(foto()).header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());

        // Tutor a autorizar.
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("88888888", "Pablo", "Sosa", LocalDate.of(1988, 9, 9)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.RegistroTutorRequest(
                                "88888888", "Pablo", "Sosa", LocalDate.of(1988, 9, 9), PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());

        UUID menorId = usuarioPorDni("77777777").getId();
        UUID tutorId = usuarioPorDni("88888888").getId();

        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------ US-6: CAP

    private UUID cargarCap(String token, int expectedStatus) throws Exception {
        MvcResult res = mockMvc.perform(multipart("/api/tutores/antecedentes-penales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCapRequest(LocalDate.now())))
                        .file(new MockMultipartFile("archivo", "cap.pdf",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{7, 7}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private MvcResult revisarCap(UUID capId, String token, String accion, String categoria) throws Exception {
        return mockMvc.perform(patch("/api/admin/moderacion/antecedentes-penales/" + capId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.RevisarCapRequest(
                                        com.tinku.identidad.dto.AccionRevisionCap.valueOf(accion),
                                        categoria))))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void us6_capAprobadoHabilitaMatching_delTutor() throws Exception {
        String token = registrarTutorYToken("12121212", "Ramiro", "Vega");
        UUID capId = cargarCap(token, 201);
        assertThat(capRepo.findById(capId).orElseThrow().getEstado()).isEqualTo(EstadoCap.PENDIENTE);

        revisarCap(capId, token, "APROBAR", null);

        CertificadoAntecedentesPenales cap = capRepo.findById(capId).orElseThrow();
        assertThat(cap.getEstado()).isEqualTo(EstadoCap.APROBADO);
        assertThat(usuarioPorDni("12121212").isActivoParaMatching()).isTrue(); // FR-ID-025
    }

    @Test
    void us6_capRechazadoPorBrCap01_disparaBackoffEnElTercerIntento() throws Exception {
        String token = registrarTutorYToken("13131313", "Nico", "Flores");

        UUID c1 = cargarCap(token, 201);
        revisarCap(c1, token, "RECHAZAR", "grooming"); // BR-CAP-01

        UUID c2 = cargarCap(token, 201);
        revisarCap(c2, token, "RECHAZAR", "grooming");

        UUID c3 = cargarCap(token, 201);
        assertThat(capRepo.findById(c3).orElseThrow().getNumeroIntento()).isEqualTo(3);
        revisarCap(c3, token, "RECHAZAR", "grooming");

        // 3er rechazo -> backoff escalado (FR-ID-021/012) -> 429.
        mockMvc.perform(multipart("/api/tutores/antecedentes-penales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCapRequest(LocalDate.now())))
                        .file(new MockMultipartFile("archivo", "cap.pdf",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{7, 7}))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isTooManyRequests());

        assertThat(usuarioPorDni("13131313").isActivoParaMatching()).isFalse();
    }

    @Test
    void us6_capEnRevisionLegal_brCap02_quedaEsperandoDecisionManual() throws Exception {
        String token = registrarTutorYToken("14141414", "Seba", "Rios");
        UUID capId = cargarCap(token, 201);

        revisarCap(capId, token, "EN_REVISION_LEGAL", "tenencia"); // BR-CAP-02

        CertificadoAntecedentesPenales cap = capRepo.findById(capId).orElseThrow();
        assertThat(cap.getEstado()).isEqualTo(EstadoCap.EN_REVISION_LEGAL);
        assertThat(cap.isTieneAntecedentes()).isTrue();
        assertThat(cap.getCategoriaAntecedente()).isEqualTo("tenencia");

        // No se auto-resuelve: sigue esperando al Admin (BR-CAP-02).
        certificadoService.marcarVencidos();
        assertThat(capRepo.findById(capId).orElseThrow().getEstado())
                .isEqualTo(EstadoCap.EN_REVISION_LEGAL);
        assertThat(usuarioPorDni("14141414").isActivoParaMatching()).isFalse();
    }

    @Test
    void us6_capVencidoAlos12Meses_suspendeMatchingDelTutor() throws Exception {
        String token = registrarTutorYToken("15151515", "Alan", "Paz");
        UUID capId = cargarCap(token, 201);
        revisarCap(capId, token, "APROBAR", null);

        // Simula el paso del tiempo: el CAP ya venció (vence_at en el pasado).
        CertificadoAntecedentesPenales cap = capRepo.findById(capId).orElseThrow();
        cap.setVenceAt(LocalDate.now().minusDays(1));
        capRepo.save(cap);

        assertThat(certificadoService.marcarVencidos()).isEqualTo(1);

        cap = capRepo.findById(capId).orElseThrow();
        assertThat(cap.getEstado()).isEqualTo(EstadoCap.VENCIDO); // FR-ID-025
        assertThat(usuarioPorDni("15151515").isActivoParaMatching()).isFalse(); // sacado del matching
        assertThat(usuarioPorDni("15151515").getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA); // no la cuenta
    }
}
