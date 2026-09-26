package com.tinku.matching;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del Chunk M2-E (T-M2-10): Historias de Usuario del
 * Spec M2 ejercitadas de punta a punta (HTTP + Spring Security + JPA + Flyway
 * + PostgreSQL via Testcontainers).
 *
 * El proceso Python (servicio externo) se mockea a nivel de
 * {@link MatchingServiceClient} (puerto del monolito): el test controla el
 * ranking semántico que devuelve y verifica qué conjunto de candidatos le
 * llega — así se prueba la parte de negocio (contexto de autorización,
 * exclusión de suspendidos, marcado no_autorizado, reordenamiento) que es lo
 * que este módulo Java debe garantizar. La similitud real se verifica aparte
 * (matching-service/test_main.py, verificado end-to-end con pgvector real).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class MatchingFlujosIntegracionTest {

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
    @Autowired CredencialService credencialService;

    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;
    @MockitoBean ReputacionSignalProvider reputacion; // reemplaza el stub del Chunk M2-C

    private static final String PASSWORD = "password123";

    @BeforeEach
    void programarMocks() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        // ReputacionSignalProvider neutro salvo que el test stubbee (BR-MATCH-01, FR-MATCH-003).
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
    }

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    // ------------------------------------------------ helpers

    private String registrarAdultoYToken(String dni, String nombre, String apellido,
                                         boolean capEst, boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, capEst, capAr)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    /** Registra y loguea un Tutor real (necesario para cargar CAP y quedar activo). */
    private String registrarTutorYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3});
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

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    /** Carga la credencial del Tutor y la aprueba: queda activo_para_matching=true
     * (la Credencial aprobada reemplazó al CAP retirado del onboarding). */
    private void aprobarCredencialDe(String token) throws Exception {
        MvcResult cargada = mockMvc.perform(multipart("/api/tutores/credenciales")
                        .file(jsonPart("datos", new com.tinku.identidad.dto.CargarCredencialRequest(
                                com.tinku.identidad.model.TipoCredencial.TITULO)))
                        .file(new MockMultipartFile("archivo", "credencial.pdf",
                                MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 credencial de prueba".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        UUID credencialId = UUID.fromString(objectMapper.readTree(
                cargada.getResponse().getContentAsString()).get("id").asText());
        credencialService.marcarAprobada(credencialId, null);
        subirFotoDe(token);
    }

    /** FR-ID-028: la foto es obligatoria para aparecer en búsquedas. PNG mínimo real. */
    private void subirFotoDe(String token) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        mockMvc.perform(multipart("/api/tutores/me/foto")
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png))
                        .with(req -> { req.setMethod("PUT"); return req; })
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private UUID registrarMenor(String dniMenor, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
        return usuarioPorDni(dniMenor).getId();
    }

    private void autorizar(UUID tutorId, UUID menorId, String tokenAr) throws Exception {
        com.tinku.testsupport.CapVigente.para(capJdbc, tutorId); // FR-ID-026 (T02)
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    @SuppressWarnings("unchecked")
    private List<UUID> candidatosRecibidos() {
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchingClient).match(captor.capture(), anyString());
        return captor.getValue();
    }

    private MvcResult buscar(String texto, String token) throws Exception {
        return mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BusquedaRequest(texto))))
                .andExpect(status().isOk())
                .andReturn();
    }

    // ------------------------------------------------ US-1 + FR-MATCH-007: adulto busca

    @Test
    void us1_adultoRecibeRankingSemantico_soloDeCandidatosActivos() throws Exception {
        String tokenEstudiante = registrarAdultoYToken("20111111", "Ana", "Lopez", true, false);

        String tokenTutorA = registrarTutorYToken("20122222", "Pablo", "Sosa");
        aprobarCredencialDe(tokenTutorA);
        String tokenTutorB = registrarTutorYToken("20133333", "Diego", "Mendez");
        aprobarCredencialDe(tokenTutorB);
        UUID tutorA = usuarioPorDni("20122222").getId();
        UUID tutorB = usuarioPorDni("20133333").getId();

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorB, 0.9),
                        new MatchingServiceClient.ResultadoMatch(tutorA, 0.7)));

        MvcResult res = buscar("algebra lineal", tokenEstudiante);

        // Orden descendente por score, sin marcas de autorización para un adulto.
        assertThat(res.getResponse().getContentAsString())
                .contains(tutorB.toString()).contains(tutorA.toString());
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item.get("noAutorizado").asBoolean()).isFalse());
    }

    @Test
    void frMatch007_tutorSuspendidoQuedaFueraDeLosCandidatos_antesDelCalculo() throws Exception {
        String tokenEstudiante = registrarAdultoYToken("20144444", "Ana", "Lopez", true, false);

        String tokenActivo = registrarTutorYToken("20155555", "Pablo", "Sosa");
        aprobarCredencialDe(tokenActivo);
        String tokenSuspendido = registrarTutorYToken("20166666", "Diego", "Mendez");
        aprobarCredencialDe(tokenSuspendido);
        UUID tutorActivo = usuarioPorDni("20155555").getId();
        UUID tutorSuspendido = usuarioPorDni("20166666").getId();

        // M9/killswitch: activo_para_matching=false -> sale del matching (FR-MATCH-007).
        Usuario suspendido = usuarioPorDni("20166666");
        suspendido.setActivoParaMatching(false);
        usuarioRepository.save(suspendido);
        assertThat(usuarioPorDni("20166666").isActivoParaMatching()).isFalse();

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorActivo, 0.8)));

        buscar("fisica", tokenEstudiante);

        // La exclusión ocurre ANTES del cálculo semántico (FR-MATCH-007): el
        // tutor suspendido nunca llega al servicio Python.
        List<UUID> candidatos = candidatosRecibidos();
        assertThat(candidatos).contains(tutorActivo).doesNotContain(tutorSuspendido);
    }

    @Test
    void frId028_tutorSinFotoQuedaFueraDeLosCandidatos() throws Exception {
        String tokenEstudiante = registrarAdultoYToken("20144445", "Ana", "Lopez", true, false);
        String tokenConFoto = registrarTutorYToken("20155556", "Pablo", "Sosa");
        aprobarCredencialDe(tokenConFoto);
        String tokenSinFoto = registrarTutorYToken("20166667", "Diego", "Mendez");
        aprobarCredencialDe(tokenSinFoto);
        UUID conFoto = usuarioPorDni("20155556").getId();
        Usuario sinFoto = usuarioPorDni("20166667");
        sinFoto.setFotoRef(null); // credencial aprobada y todo completo, menos la foto
        usuarioRepository.save(sinFoto);

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(conFoto, 0.8)));
        buscar("fisica", tokenEstudiante);

        assertThat(candidatosRecibidos()).contains(conFoto).doesNotContain(sinFoto.getId());
    }

    // ------------------------------------------------ US-2 / T-M2-10: menor

    @Test
    void tM2_10_menorSinAutorizados_devuelveResultadosMarcados_noAutorizado() throws Exception {
        String tokenAr = registrarAdultoYToken("20177777", "Maria", "Perez", true, true);

        String tokenTutor = registrarTutorYToken("20188888", "Pablo", "Sosa");
        aprobarCredencialDe(tokenTutor);
        UUID tutor = usuarioPorDni("20188888").getId();

        registrarMenor("20199999", tokenAr);
        String tokenMenor = login("20199999");

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutor, 0.75)));

        MvcResult res = buscar("matematica", tokenMenor);

        // Sin autorizados: busca el universo igual que cualquier perfil y TODO
        // resultado queda marcado para mostrar "Solicitar autorización" (FR-MATCH-005).
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()))
                .hasSize(1)
                .first().satisfies(item -> {
                    assertThat(item.get("tutorId").asText()).isEqualTo(tutor.toString());
                    assertThat(item.get("noAutorizado").asBoolean()).isTrue();
                });
    }

    @Test
    void us2_menorConAutorizados_soloRecibeSuLista_marcadosComoAutorizados() throws Exception {
        String tokenAr = registrarAdultoYToken("20211111", "Maria", "Perez", true, true);

        String tokenAutorizado = registrarTutorYToken("20222222", "Pablo", "Sosa");
        aprobarCredencialDe(tokenAutorizado);
        String tokenOtro = registrarTutorYToken("20233333", "Diego", "Mendez");
        aprobarCredencialDe(tokenOtro);
        UUID autorizado = usuarioPorDni("20222222").getId();
        UUID otro = usuarioPorDni("20233333").getId();

        UUID menorId = registrarMenor("20244444", tokenAr);
        autorizar(autorizado, menorId, tokenAr);
        String tokenMenor = login("20244444");

        // El proceso Python solo recibe y devuelve lo de la lista del menor
        // (FR-MATCH-004): el Tutor no autorizado nunca le llega.
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(autorizado, 0.8)));

        MvcResult res = buscar("quimica", tokenMenor);

        List<UUID> candidatos = candidatosRecibidos();
        assertThat(candidatos).contains(autorizado).doesNotContain(otro);
        List<UUID> vistos = java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(res.getResponse().getContentAsString()).spliterator(), false)
                .map(item -> UUID.fromString(item.get("tutorId").asText()))
                .toList();
        assertThat(vistos).containsExactly(autorizado).doesNotContain(otro);
    }

    // ------------------------------------------------ US-3 / US-4: reputación

    /** FR-MATCH-007 + Artículo II: si el AR marca "no confiable" al único Tutor autorizado, la
     *  lista queda vacía y el menor pasaba a buscar el universo, donde ese Tutor volvía a aparecer. */
    @Test
    void frMatch007_menor_tutorNoConfiableNuncaVuelveComoCandidato_aunqueNoQuedenAutorizados() throws Exception {
        String tokenAr = registrarAdultoYToken("29771111", "Maria", "Perez", true, true);
        String tokenTutor = registrarTutorYToken("29772222", "Pablo", "Sosa");
        aprobarCredencialDe(tokenTutor);
        UUID tutor = usuarioPorDni("29772222").getId();
        UUID menorId = registrarMenor("29773333", tokenAr);
        autorizar(tutor, menorId, tokenAr);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/autorizaciones/no-confiable")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.MarcarNoConfiableRequest(tutor, true))))
                .andExpect(status().isNoContent());
        String tokenMenor = login("29773333");

        when(matchingClient.match(any(), anyString())).thenReturn(List.of());
        buscar("matematica", tokenMenor);

        assertThat(candidatosRecibidos()).doesNotContain(tutor);
    }

    @Test
    void us3_brMatch01_tutorConMalaCalificacionReciente_quedaEnSombra() throws Exception {
        String tokenEstudiante = registrarAdultoYToken("20255555", "Ana", "Lopez", true, false);

        String tokenA = registrarTutorYToken("20266666", "Pablo", "Sosa");
        aprobarCredencialDe(tokenA);
        String tokenB = registrarTutorYToken("20277777", "Diego", "Mendez");
        aprobarCredencialDe(tokenB);
        UUID tutorA = usuarioPorDni("20266666").getId();
        UUID tutorB = usuarioPorDni("20277777").getId();

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorA, 0.9),
                        new MatchingServiceClient.ResultadoMatch(tutorB, 0.8)));
        // M7 (stub provisto por el test): el Tutor A tiene 1-2 estrellas en las
        // últimas 24hs -> sombra de BR-MATCH-01.
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of(tutorA));

        MvcResult res = buscar("historia", tokenEstudiante);

        // El de peor score (B) queda como único resultado: A no se sugiere.
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()))
                .hasSize(1)
                .first().satisfies(item ->
                        assertThat(item.get("tutorId").asText()).isEqualTo(tutorB.toString()));
    }

    @Test
    void us4_senalesImplicitas_deM7_reordenanEntreSimilares() throws Exception {
        String tokenEstudiante = registrarAdultoYToken("20288888", "Ana", "Lopez", true, false);

        String tokenA = registrarTutorYToken("20299999", "Pablo", "Sosa");
        aprobarCredencialDe(tokenA);
        String tokenB = registrarTutorYToken("20311111", "Diego", "Mendez");
        aprobarCredencialDe(tokenB);
        UUID tutorA = usuarioPorDni("20299999").getId();
        UUID tutorB = usuarioPorDni("20311111").getId();

        // Relevancia semántica EQUIVALENTE (FR-MATCH-003): la señales de M7 deciden.
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorA, 0.5),
                        new MatchingServiceClient.ResultadoMatch(tutorB, 0.5)));
        when(reputacion.senalesImplicitas(anyCollection()))
                .thenReturn(Map.of(tutorB, 0.35, tutorA, 0.0));

        MvcResult res = buscar("ingles", tokenEstudiante);

        assertThat(java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(res.getResponse().getContentAsString()).spliterator(), false)
                .map(item -> item.get("tutorId").asText())
                .toList())
                .containsExactly(tutorB.toString(), tutorA.toString());
    }

    // ------------------------------------------------ US-6: búsquedas guardadas

    @Test
    void us6_guardarListarYReEjecutar_usaResultadosActualizados() throws Exception {
        String token = registrarAdultoYToken("20322222", "Ana", "Lopez", true, false);

        String tokenTutor = registrarTutorYToken("20333333", "Pablo", "Sosa");
        aprobarCredencialDe(tokenTutor);
        UUID tutor = usuarioPorDni("20333333").getId();

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutor, 0.6)));

        MvcResult save = mockMvc.perform(post("/api/busquedas/guardadas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BusquedaRequest("frances"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.textoBusqueda").value("frances"))
                .andReturn();
        UUID guardadaId = UUID.fromString(objectMapper.readTree(
                save.getResponse().getContentAsString()).get("id").asText());

        mockMvc.perform(get("/api/busquedas/guardadas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(guardadaId.toString()));

        // Re-ejecutar corre el flujo completo y devuelve resultado fresco con el
        // texto guardado ("frances"), no una lista congelada (FR-MATCH-008).
        mockMvc.perform(post("/api/busquedas/guardadas/" + guardadaId + "/ejecutar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tutorId").value(tutor.toString()));
        verify(matchingClient).match(any(), eq("frances"));
    }

    @Test
    void us6_noSePuedeReEjecutarUnaBusquedaDeOtroUsuario() throws Exception {
        String tokenOwner = registrarAdultoYToken("20344444", "Ana", "Lopez", true, false);
        String tokenOtro = registrarAdultoYToken("20355555", "Leo", "Rios", true, false);

        MvcResult save = mockMvc.perform(post("/api/busquedas/guardadas")
                        .header("Authorization", "Bearer " + tokenOwner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BusquedaRequest("algo"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID guardadaId = UUID.fromString(objectMapper.readTree(
                save.getResponse().getContentAsString()).get("id").asText());

        // El texto y el id no se filtran: para el que no es dueño es 404.
        mockMvc.perform(post("/api/busquedas/guardadas/" + guardadaId + "/ejecutar")
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------ degradación honesta

    @Test
    void servicioPythonCaido_responde503_sinFabricarRanking() throws Exception {
        String token = registrarAdultoYToken("20366666", "Ana", "Lopez", true, false);

        when(matchingClient.match(any(), anyString()))
                .thenThrow(new MatchingNoDisponibleException());

        mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BusquedaRequest("filosofia"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ------------------------------------------------ US-5: perfil de matching (FR-MATCH-006)

    @Test
    void us5_tutorCargaMateriaDelCatalogo_yQuedaEnSuPerfilPublico() throws Exception {
        String tokenTutor = registrarTutorYToken("20377777", "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni("20377777").getId();

        mockMvc.perform(put("/api/perfil-matching")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nivel", "secundario", "materias", List.of("Matemática", "Física")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias[0]").value("Matemática"))
                .andExpect(jsonPath("$.materias[1]").value("Física"))
                .andExpect(jsonPath("$.nivel").value("secundario"));

        // Sin credencial aprobada el Tutor no aparece en ranking, pero su perfil
        // público (GET /api/tutores/{id}) ya refleja el catálogo configurado.
        mockMvc.perform(get("/api/tutores/" + tutorId)
                        .header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias[0]").value("Matemática"))
                .andExpect(jsonPath("$.nivel").value("secundario"));
    }

    @Test
    void us5_materiaFueraDelCatalogo_422() throws Exception {
        String tokenTutor = registrarTutorYToken("20388888", "Diego", "Mendez");

        mockMvc.perform(put("/api/perfil-matching")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nivel", "secundario", "materias", List.of("Astrología")))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void us5_noTutor_noPuedeCargarPerfil_403() throws Exception {
        String tokenEst = registrarAdultoYToken("20399999", "Ana", "Lopez", true, false);

        mockMvc.perform(put("/api/perfil-matching")
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("nivel", "secundario", "materias", List.of("Matemática")))))
                .andExpect(status().isForbidden());
    }
}