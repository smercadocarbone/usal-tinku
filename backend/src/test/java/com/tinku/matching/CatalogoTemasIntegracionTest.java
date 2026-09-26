package com.tinku.matching;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.CargarCredencialRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.CredencialService;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del contrato 2b de M2-F (chunk en curso): catálogo
 * granular de temas (GET /api/catalogos) y perfil de temas del Tutor
 * (GET/PUT /api/tutores/me/temas), con el acotamiento por nombre/materia de
 * POST /api/busquedas. Mismo patrón Testcontainers que MatchingFlujosIntegracionTest.
 *
 * El SEED real del catálogo entra en V13 (FASE 3); acá se siembran fixtures
 * en @BeforeEach vía repositorios (idempotente: los trayectos/temas persisten
 * entre tests de la clase, los perfiles de matching se limpian para aislar los
 * candidatos de cada caso).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogoTemasIntegracionTest {

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
    @Autowired TrayectoRepository trayectoRepository;
    @Autowired TemaRepository temaRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired com.tinku.admin.repository.AdminRepository adminRepository;

    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;
    @MockitoBean ReputacionSignalProvider reputacion;

    private UUID divisionId;
    private UUID cuentoId;
    private UUID factorizacionId;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        // Aislar candidatos entre tests: solo los tutores que pongan temas en el
        // propio test tienen fila con tema_ids (los trayectos/temas sí persisten).
        jdbcTemplate.update("DELETE FROM matching.perfiles_tutor_matching");
        jdbcTemplate.update("DELETE FROM matching.temas_sugeridos");
        seedCatalogo();
    }

    private void seedCatalogo() {
        UUID primario4Mat = trayecto(NivelTrayecto.primario, "4°", "Matemática");
        UUID primario4Len = trayecto(NivelTrayecto.primario, "4°", "Lengua");
        UUID secundario4Mat = trayecto(NivelTrayecto.secundario, "4°", "Matemática");
        divisionId = tema(primario4Mat, "División", "Dividir números enteros", 0);
        UUID multiplicacionId = tema(primario4Mat, "Multiplicación", "Multiplicar números naturales", 1);
        cuentoId = tema(primario4Len, "El cuento", "Narración y comprensión lectora", 0);
        factorizacionId = tema(secundario4Mat, "Factorización", "Factoreo de polinomios", 0);
    }

    private UUID trayecto(NivelTrayecto nivel, String curso, String materia) {
        Trayecto existente = trayectoRepository.findAll().stream()
                .filter(t -> t.getNivel() == nivel)
                .filter(t -> t.getAnioOCarrera().equals(curso))
                .filter(t -> t.getMateria().equals(materia))
                .findFirst().orElse(null);
        if (existente != null) return existente.getId();
        Trayecto nuevo = new Trayecto();
        nuevo.setNivel(nivel);
        nuevo.setAnioOCarrera(curso);
        nuevo.setMateria(materia);
        return trayectoRepository.save(nuevo).getId();
    }

    private UUID tema(UUID trayectoId, String nombre, String descripcion, int orden) {
        Tema existente = temaRepository.findAll().stream()
                .filter(t -> t.getTrayecto().getId().equals(trayectoId))
                .filter(t -> t.getNombre().equals(nombre))
                .findFirst().orElse(null);
        if (existente != null) return existente.getId();
        Tema nuevo = new Tema();
        nuevo.setTrayecto(trayectoRepository.findById(trayectoId).orElseThrow());
        nuevo.setNombre(nombre);
        nuevo.setDescripcion(descripcion);
        nuevo.setOrden(orden);
        return temaRepository.save(nuevo).getId();
    }

    // ------------------------------------------------ helpers de usuarios

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    private String registrarAdultoYToken(String dni, boolean capEst, boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, "Ana", "Lopez", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, "Ana", "Lopez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, capEst, capAr)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private String registrarTutorYToken(String dni) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, "Pablo", "Sosa", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, "Pablo", "Sosa", LocalDate.of(1990, 5, 15),
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
                        .file(jsonPart("datos", new CargarCredencialRequest(TipoCredencial.TITULO)))
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

    // ------------------------------------------------ helpers de temas / búsqueda

    private UUID tutorConTemas(String dni, List<UUID> temaIds) throws Exception {
        String token = registrarTutorYToken(dni);
        aprobarCredencialDe(token);
        putTemas(token, temaIds);
        return usuarioPorDni(dni).getId();
    }

    private void putTemas(String token, List<UUID> temaIds) throws Exception {
        mockMvc.perform(put("/api/tutores/me/temas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tema_ids", temaIds.stream().map(UUID::toString).toList()))))
                .andExpect(status().isOk());
    }

    private List<UUID> getTemas(String token) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/tutores/me/temas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ids = objectMapper.readTree(res.getResponse().getContentAsString()).get("tema_ids");
        List<UUID> out = new ArrayList<>();
        ids.forEach(n -> out.add(UUID.fromString(n.asText())));
        return out;
    }

    private MvcResult buscar(String texto, String nombre, String materia, String token) throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("texto_busqueda", texto);
        body.put("nombre", nombre);
        body.put("filtro_materia", materia);
        return mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<UUID> candidatosRecibidos() {
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(matchingClient).match(captor.capture(), anyString());
        return captor.getValue();
    }

    // ------------------------------------------------ GET /api/catalogos

    @Test
    void getCatalogos_sinFiltros_devuelveElArbolCompleto() throws Exception {
        String token = registrarAdultoYToken("30111111", true, false);

        mockMvc.perform(get("/api/catalogos").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nivel").value("primario"))
                .andExpect(jsonPath("$[1].nivel").value("secundario"))
                .andExpect(jsonPath("$[2].nivel").value("universitario"))
                .andExpect(jsonPath("$[0].cursos.length()").value(6))
                .andExpect(jsonPath("$[0].cursos[0].nombre").value("1°"))
                .andExpect(jsonPath("$[0].cursos[5].nombre").value("6°"))
                .andExpect(jsonPath("$[1].cursos.length()").value(6))
                .andExpect(jsonPath("$[2].cursos.length()").value(13))
                .andExpect(jsonPath("$[0].cursos[0].materias[0].temas[0].nombre").isNotEmpty());
    }

    @Test
    void getCatalogos_conFiltrosNivelCursoMateria_filtraLaRama() throws Exception {
        String token = registrarAdultoYToken("30122222", true, false);

        mockMvc.perform(get("/api/catalogos")
                        .param("nivel", "secundario")
                        .param("curso", "4°")
                        .param("materia", "Matemática")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nivel").value("secundario"))
                .andExpect(jsonPath("$[0].cursos.length()").value(1))
                .andExpect(jsonPath("$[0].cursos[0].nombre").value("4°"))
                .andExpect(jsonPath("$[0].cursos[0].materias.length()").value(1))
                .andExpect(jsonPath("$[0].cursos[0].materias[0].nombre").value("Matemática"))
                .andExpect(jsonPath("$[0].cursos[0].materias[0].temas[0].nombre").value("Factorización"));
    }

    @Test
    void getCatalogos_filtroSoloNivelYMateria_podaLasOtrasRamas() throws Exception {
        String token = registrarAdultoYToken("30133333", true, false);

        mockMvc.perform(get("/api/catalogos")
                        .param("nivel", "primario")
                        .param("materia", "Lengua")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nivel").value("primario"))
                .andExpect(jsonPath("$[0].cursos[0].materias.length()").value(1))
                .andExpect(jsonPath("$[0].cursos[0].materias[0].nombre").value("Lengua"));
    }

    // ------------------------------------------------ GET/PUT /api/tutores/me/temas

    @Test
    void putTemas_tutorGuarda_yGetLosDevuelve() throws Exception {
        String token = registrarTutorYToken("30144444");
        aprobarCredencialDe(token);

        assertThat(getTemas(token)).isEmpty();

        putTemas(token, List.of(divisionId, factorizacionId));

        assertThat(getTemas(token)).containsExactly(divisionId, factorizacionId);
    }

    /** Producción 2026-09-25: el perfil público leía las materias de la columna legacy
     *  (V7, materias_niveles_ids), que el flujo de temas nunca llena → todo Tutor real
     *  se veía "sin materias" y el checklist nunca tildaba "Elegí qué materias enseñás". */
    @Test
    void putTemas_elPerfilPublicoMuestraLasMateriasDeEsosTemas() throws Exception {
        String token = registrarTutorYToken("30177701");
        aprobarCredencialDe(token);
        UUID tutorId = usuarioPorDni("30177701").getId();

        putTemas(token, List.of(divisionId, cuentoId, factorizacionId));

        mockMvc.perform(get("/api/tutores/" + tutorId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias.length()").value(2))
                .andExpect(jsonPath("$.materias[0]").value("Matemática"))
                .andExpect(jsonPath("$.materias[1]").value("Lengua"))
                .andExpect(jsonPath("$.nivel").value("primario"));
        mockMvc.perform(get("/api/tutores/me/estado-perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneMaterias").value(true));
    }

    /** Producción 2026-09-25: nadie pedía el recompute de embeddings (contrato 2c), así
     *  que un Tutor real que elegía temas nunca aparecía en la búsqueda semántica. */
    @Test
    void putTemas_pideElRecomputeDeEmbeddingsDespuesDelCommit() throws Exception {
        String token = registrarTutorYToken("30177702");
        aprobarCredencialDe(token);

        putTemas(token, List.of(divisionId));

        verify(matchingClient, timeout(5000)).recomputarEmbeddings();
    }

    @Test
    void putTemas_noTutor_devuelve403() throws Exception {
        String token = registrarAdultoYToken("30155555", true, false);

        mockMvc.perform(put("/api/tutores/me/temas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tema_ids", List.of(divisionId.toString())))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void putTemas_temaInexistente_devuelve404() throws Exception {
        String token = registrarTutorYToken("30166666");
        aprobarCredencialDe(token);

        mockMvc.perform(put("/api/tutores/me/temas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("tema_ids", List.of(UUID.randomUUID().toString())))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void putTemas_idNoUuid_devuelve422() throws Exception {
        String token = registrarTutorYToken("30177777");
        aprobarCredencialDe(token);

        mockMvc.perform(put("/api/tutores/me/temas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tema_ids\":[\"no-es-un-uuid\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void putTemas_listaVacia_limpiaLosTemas() throws Exception {
        String token = registrarTutorYToken("30188888");
        aprobarCredencialDe(token);

        putTemas(token, List.of(divisionId));
        assertThat(getTemas(token)).containsExactly(divisionId);

        putTemas(token, List.of());
        assertThat(getTemas(token)).isEmpty();
    }

    // ------------------------------------------------ búsqueda por nombre/materia

    @Test
    void busquedaSoloNombre_acotaCandidatos_toleranteaTildes() throws Exception {
        String token = registrarAdultoYToken("30199999", true, false);

        UUID tutorA = tutorConTemas("30211111", List.of(divisionId));
        UUID tutorB = tutorConTemas("30222222", List.of(cuentoId));
        UUID tutorC = tutorConTemas("30233333", List.of(divisionId));

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorA, 0.9),
                        new MatchingServiceClient.ResultadoMatch(tutorC, 0.8)));

        // "division" sin tilde matchea el tema "División" (unaccent) → los dos
        // tutores con ese tema quedan candidatos; el de "El cuento" no.
        buscar("", "division", "", token);

        ArgumentCaptor<String> textoCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> candidatosCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchingClient).match(candidatosCaptor.capture(), textoCaptor.capture());
        assertThat(textoCaptor.getValue()).isEqualTo("division");
        assertThat(candidatosCaptor.getValue())
                .contains(tutorA, tutorC)
                .doesNotContain(tutorB);
    }

    @Test
    void busquedaSoloMateria_acotaCandidatos() throws Exception {
        String token = registrarAdultoYToken("30244444", true, false);

        UUID tutorA = tutorConTemas("30255555", List.of(divisionId));
        UUID tutorB = tutorConTemas("30266666", List.of(cuentoId));
        UUID tutorC = tutorConTemas("30277777", List.of(factorizacionId));

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorA, 0.9),
                        new MatchingServiceClient.ResultadoMatch(tutorC, 0.8)));

        // Men cumple la misma rama Matemática (División y Factorización); el de
        // Lengua queda fuera del acotamiento.
        buscar("", "", "Matemática", token);

        assertThat(candidatosRecibidos())
                .contains(tutorA, tutorC)
                .doesNotContain(tutorB);
    }

    /** Revisión 2026-09-26: el nivel elegido en /buscar no llegaba al backend, y "Primario +
     *  Matemática" traía tutores de Matemática de cualquier nivel. */
    @Test
    void busquedaMateriaYNivel_soloTutoresDeEseNivel() throws Exception {
        String token = registrarAdultoYToken("30881111", true, false);
        UUID primario = tutorConTemas("30882222", List.of(divisionId));
        UUID secundario = tutorConTemas("30883333", List.of(factorizacionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(primario, 0.9)));

        Map<String, String> body = new LinkedHashMap<>();
        body.put("filtro_materia", "Matemática");
        body.put("filtro_nivel", "primario");
        mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        assertThat(candidatosRecibidos()).contains(primario).doesNotContain(secundario);
    }

    @Test
    void busquedaConNivelInvalido_devuelve422() throws Exception {
        String token = registrarAdultoYToken("30884444", true, false);
        mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto_busqueda\":\"fracciones\",\"filtro_nivel\":\"jardin\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void busquedaSinNingunCampo_devuelve422() throws Exception {
        String token = registrarAdultoYToken("30288888", true, false);

        mockMvc.perform(post("/api/busquedas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto_busqueda\":\"\",\"nombre\":\"\",\"filtro_materia\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ------------------------------------------------ US-2 / FR-MATCH-004/005 + filtro por nombre

    @Test
    void menorConAutorizados_filtroPorNombre_soloSusAutorizados() throws Exception {
        String tokenAr = registrarAdultoYToken("30299999", true, true);

        UUID tutorAutorizado = tutorConTemas("30311111", List.of(divisionId));
        UUID tutorNoAutorizado = tutorConTemas("30322222", List.of(divisionId));

        UUID menorId = registrarMenor("30333333", tokenAr);
        autorizar(tutorAutorizado, menorId, tokenAr);
        String tokenMenor = login("30333333");

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorAutorizado, 0.85)));

        MvcResult res = buscar("", "division", "", tokenMenor);

        // (FR-MATCH-004) el no autorizado jamás llega al motor, aunque matchee el
        // tema; (FR-MATCH-005) el resultado del autorizado NO se marca.
        assertThat(candidatosRecibidos())
                .contains(tutorAutorizado)
                .doesNotContain(tutorNoAutorizado);
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()))
                .hasSize(1)
                .first().satisfies(item -> {
                    assertThat(item.get("tutorId").asText()).isEqualTo(tutorAutorizado.toString());
                    assertThat(item.get("noAutorizado").asBoolean()).isFalse();
                });
    }

    @Test
    void menorSinAutorizados_filtroPorNombre_todoMarcadoNoAutorizado() throws Exception {
        String tokenAr = registrarAdultoYToken("30344444", true, true);

        UUID tutorA = tutorConTemas("30355555", List.of(divisionId));
        UUID tutorB = tutorConTemas("30366666", List.of(divisionId));

        registrarMenor("30377777", tokenAr);
        String tokenMenor = login("30377777");

        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutorA, 0.9),
                        new MatchingServiceClient.ResultadoMatch(tutorB, 0.8)));

        MvcResult res = buscar("", "division", "", tokenMenor);

        // Sin autorizados busca el universo y todo resultado queda marcado para
        // "Solicitar autorización" (FR-MATCH-005) — incluso con filtro por nombre.
        assertThat(objectMapper.readTree(res.getResponse().getContentAsString()))
                .hasSize(2)
                .allSatisfy(item -> assertThat(item.get("noAutorizado").asBoolean()).isTrue());
    }

    // ------------------------------------------------ FR-MATCH-011: recomendación por área

    /** Nadie da lo que se escribió: se reconoce el área (materia y nivel del tema del catálogo
     *  más parecido) y se recomiendan tutores de esa área, marcados como tales. */
    @Test
    void sinTutorDirecto_recomiendaTutoresDelAreaReconocida() throws Exception {
        String token = registrarAdultoYToken("30891111", true, false);
        UUID primario = tutorConTemas("30892222", List.of(divisionId));
        UUID secundario = tutorConTemas("30893333", List.of(factorizacionId));
        UUID lengua = tutorConTemas("30894444", List.of(cuentoId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(secundario, 0.2),
                        new MatchingServiceClient.ResultadoMatch(primario, 0.15),
                        new MatchingServiceClient.ResultadoMatch(lengua, 0.05)));
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new MatchingServiceClient.SugerenciaTema(factorizacionId.toString(), 0.6)));

        MvcResult res = buscar("ecuaciones cuadráticas con discriminante", null, null, token);

        JsonNode lista = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("tutorId").asText()).isEqualTo(secundario.toString());
        assertThat(lista.get(0).get("porArea").asBoolean()).isTrue();
        assertThat(lista.get(0).get("area").asText()).isEqualTo("Matemática de secundario");
    }

    @Test
    void recomendacionPorArea_sinTutoresDeEseNivel_usaLaMismaMateriaEnOtroNivel() throws Exception {
        String token = registrarAdultoYToken("30895555", true, false);
        UUID primario = tutorConTemas("30896666", List.of(divisionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(primario, 0.1)));
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new MatchingServiceClient.SugerenciaTema(factorizacionId.toString(), 0.6)));

        JsonNode lista = objectMapper.readTree(buscar("polinomios de grado cinco", null, null, token)
                .getResponse().getContentAsString());

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("tutorId").asText()).isEqualTo(primario.toString());
        assertThat(lista.get(0).get("porArea").asBoolean()).isTrue();
    }

    @Test
    void temaNoReconocido_noRecomiendaNada() throws Exception {
        String token = registrarAdultoYToken("30897777", true, false);
        UUID primario = tutorConTemas("30898888", List.of(divisionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(primario, 0.1)));
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new MatchingServiceClient.SugerenciaTema(divisionId.toString(), 0.1)));

        assertThat(objectMapper.readTree(buscar("guitarra electrica", null, null, token)
                .getResponse().getContentAsString())).isEmpty();
    }

    /** Producción 2026-09-26: "divisiones en primario" no traía a nadie. Ahora se entiende el nivel
     *  escrito y el tema por sus palabras aunque el modelo no reconozca el área (catálogo sin
     *  embeber todavía), y el tutor de Matemática de primario aparece como recomendación. */
    @Test
    void divisionesEnPrimario_sinAreaPorSimilitud_reconoceTemaYNivelPorLasPalabras() throws Exception {
        String token = registrarAdultoYToken("30911111", true, false);
        UUID primario = tutorConTemas("30912222", List.of(divisionId));
        UUID secundario = tutorConTemas("30913333", List.of(factorizacionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(secundario, 0.2),
                        new MatchingServiceClient.ResultadoMatch(primario, 0.1)));
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of()); // catálogo todavía sin embeber

        JsonNode lista = objectMapper.readTree(buscar("divisiones en primario", null, null, token)
                .getResponse().getContentAsString());

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("tutorId").asText()).isEqualTo(primario.toString());
        assertThat(lista.get(0).get("porArea").asBoolean()).isTrue();
        assertThat(lista.get(0).get("area").asText()).isEqualTo("Matemática de primario");
    }

    /** El nivel escrito vale como el chip: un tutor de secundario no aparece como resultado
     *  directo de "división en primario", aunque su puntaje sea más alto. */
    @Test
    void nivelEscritoEnElTexto_acotaLosResultadosDirectos() throws Exception {
        String token = registrarAdultoYToken("30914444", true, false);
        UUID primario = tutorConTemas("30915555", List.of(divisionId));
        UUID secundario = tutorConTemas("30916666", List.of(factorizacionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(secundario, 0.8),
                        new MatchingServiceClient.ResultadoMatch(primario, 0.6)));

        JsonNode lista = objectMapper.readTree(buscar("división en primaria", null, null, token)
                .getResponse().getContentAsString());

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("tutorId").asText()).isEqualTo(primario.toString());
        assertThat(lista.get(0).get("porArea").asBoolean()).isFalse();
    }

    /** Un tutor del área sin puntaje semántico (su embedding todavía no se calculó) igual se
     *  recomienda: antes quedaba afuera y la búsqueda volvía vacía. */
    @Test
    void recomendacionPorArea_incluyeTutoresDelAreaSinPuntajeTodavia() throws Exception {
        String token = registrarAdultoYToken("30917777", true, false);
        UUID primario = tutorConTemas("30918888", List.of(divisionId));
        when(matchingClient.match(any(), anyString())).thenReturn(List.of());
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        JsonNode lista = objectMapper.readTree(buscar("divisiones", null, null, token)
                .getResponse().getContentAsString());

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("tutorId").asText()).isEqualTo(primario.toString());
        assertThat(lista.get(0).get("porArea").asBoolean()).isTrue();
    }

    // ------------------------------------------------ FR-MATCH-012 / FR-ADM-009: temas sugeridos

    @Test
    void temasSugeridos_contadorSinUsuario_elAdminSoloVeLosRepetidos_yLosResuelve() throws Exception {
        String token = registrarAdultoYToken("30901111", true, false);
        UUID tutor = tutorConTemas("30902222", List.of(divisionId));
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutor, 0.1)));
        when(matchingClient.temasCercanos(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(new MatchingServiceClient.SugerenciaTema(factorizacionId.toString(), 0.6)));

        buscar("Logaritmos", null, null, token);
        buscar("logaritmos ", null, null, token);
        buscar("LOGARITMOS", null, null, token);
        buscar("guitarra", null, null, token);

        // Una fila por tema, con su contador; ninguna columna del usuario.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT veces FROM matching.temas_sugeridos WHERE texto = 'logaritmos'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM matching.temas_sugeridos", Integer.class))
                .isEqualTo(2);

        com.tinku.identidad.model.Usuario moderador = usuarioPorDni("30901111");
        com.tinku.admin.model.Admin fila = new com.tinku.admin.model.Admin();
        fila.setUsuario(moderador);
        fila.setRol(com.tinku.admin.model.RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);

        JsonNode lista = objectMapper.readTree(mockMvc.perform(get("/api/admin/temas-sugeridos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        // Solo lo pedido varias veces (minimo-veces = 3): "guitarra" (1 vez) no aparece.
        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).get("texto").asText()).isEqualTo("logaritmos");
        assertThat(lista.get(0).get("veces").asInt()).isEqualTo(3);
        assertThat(lista.get(0).get("materia").asText()).isEqualTo("Matemática");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/temas-sugeridos/{id}", lista.get(0).get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM matching.temas_sugeridos WHERE texto = 'logaritmos'", Integer.class))
                .isZero();
    }

    @Test
    void temasSugeridos_noAdmin_403_yLasBusquedasDeMenoresNoSeRegistran() throws Exception {
        String tokenAr = registrarAdultoYToken("30903333", true, true);
        UUID tutor = tutorConTemas("30904444", List.of(divisionId));
        registrarMenor("30905555", tokenAr);
        String tokenMenor = login("30905555");
        when(matchingClient.match(any(), anyString()))
                .thenReturn(List.of(new MatchingServiceClient.ResultadoMatch(tutor, 0.1)));

        buscar("tema que no existe en el catalogo", null, null, tokenMenor);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM matching.temas_sugeridos", Integer.class))
                .isZero();
        mockMvc.perform(get("/api/admin/temas-sugeridos").header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ asistente de "Mis materias"

    /** Sugiere temas del catálogo (filtrados por nivel en Java) en el orden del modelo; solo Tutor. */
    @Test
    void sugerencias_ordenDelModelo_filtroDeNivel_soloTutor() throws Exception {
        String tokenTutor = registrarTutorYToken("30900001");
        when(matchingClient.sugerirTemas(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(inv -> {
                    List<MatchingServiceClient.TemaCandidato> temas = inv.getArgument(1);
                    // El "modelo" pone la división primero.
                    return temas.stream()
                            .sorted(java.util.Comparator.comparing(t -> t.id().equals(divisionId.toString()) ? 0 : 1))
                            .map(t -> new MatchingServiceClient.SugerenciaTema(t.id(), 0.5))
                            .toList();
                });

        mockMvc.perform(post("/api/tutores/me/temas/sugerencias").header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Enseño a dividir a chicos de cuarto grado\",\"nivel\":\"primario\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(divisionId.toString()))
                .andExpect(jsonPath("$[0].materia").value("Matemática"))
                .andExpect(jsonPath("$[?(@.id == '" + factorizacionId + "')]").isEmpty());

        mockMvc.perform(post("/api/tutores/me/temas/sugerencias").header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"corto\"}"))
                .andExpect(status().isUnprocessableEntity());

        String tokenAdulto = registrarAdultoYToken("30900002", true, false);
        mockMvc.perform(post("/api/tutores/me/temas/sugerencias").header("Authorization", "Bearer " + tokenAdulto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Enseño a dividir a chicos de cuarto grado\"}"))
                .andExpect(status().isForbidden());
    }
}
