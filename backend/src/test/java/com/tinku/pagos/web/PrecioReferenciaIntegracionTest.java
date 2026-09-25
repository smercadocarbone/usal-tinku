package com.tinku.pagos.web;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.repository.PrecioReferenciaRegionalRepository;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-6 — sugerencia de precio de referencia regional (T-M5-09) de punta a punta:
 * {@code GET /api/pagos/precio-referencia/{provincia}} devuelve la versión
 * vigente (mayor {@code version}) de {@code pagos.precios_referencia_regional}.
 * La respuesta es un SNAPSHOT que el consumidor copia al perfil del Tutor — no
 * hay FK a la fila de configuración (FR-PAG-005), y la sugerencia solo cambia
 * cuando se publica una versión nueva (revisión trimestral de M8, FR-PAG-006),
 * nunca a pedido individual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PrecioReferenciaIntegracionTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PrecioReferenciaRegionalRepository precioReferenciaRepository;

    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;
    @MockitoBean ReputacionSignalProvider reputacion;
    @MockitoBean ReputacionBloqueoProveedor reputacionBloqueo;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 40_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
    }

    // ------------------------------------------------ helpers

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
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
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private void cargarReferencia(String provincia, int version, String valor, Instant vigenteDesde) {
        PrecioReferenciaRegional p = new PrecioReferenciaRegional();
        p.setProvincia(provincia);
        p.setVersion(version);
        p.setValorSugerido(new BigDecimal(valor));
        p.setVigenteDesde(vigenteDesde);
        precioReferenciaRepository.save(p);
    }

    // ------------------------------------------------ tests

    @Test
    void us6_devuelveLaVersionVigente_paraLaProvincia() throws Exception {
        String provincia = "Buenos Aires";
        cargarReferencia(provincia, 1, "12000.00", Instant.parse("2025-01-01T00:00:00Z"));
        cargarReferencia(provincia, 2, "13000.00", Instant.parse("2025-07-01T00:00:00Z"));
        // Otra provincia no contamina la consulta.
        cargarReferencia("Córdoba", 1, "11000.00", Instant.parse("2025-01-01T00:00:00Z"));
        String token = registrarTutorYToken(dniUnico());

        // FR-PAG-006: la vigente es la de mayor version (recalculo trimestral M8);
        // el snapshot se copia al perfil sin FK (FR-PAG-005).
        mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", provincia)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provincia").value(provincia))
                .andExpect(jsonPath("$.valorSugerido").value(13000.00))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.vigenteDesde").isNotEmpty());
    }

    @Test
    void us6_alPublicarUnaVersionNueva_cambiaLaSugerencia_soloPorRecalculo() throws Exception {
        String provincia = "Tucumán";
        cargarReferencia(provincia, 1, "9000.00", Instant.parse("2025-01-01T00:00:00Z"));
        String token = registrarTutorYToken(dniUnico());

        mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", provincia)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // La revisión trimestral agrega una fila (FR-ADM-007): desde ahí la
        // sugerencia para quien configure su perfil es la nueva; lo ya fijado
        // (copia) no se toca.
        cargarReferencia(provincia, 2, "10500.00", Instant.parse("2025-10-01T00:00:00Z"));

        mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", provincia)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorSugerido").value(10500.00))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void us6_provinciaSinReferencia_204_sinCuerpo() throws Exception {
        String token = registrarTutorYToken(dniUnico());

        // FR-PAG-005: la sugerencia es no vinculante y opcional — sin fila, el
        // Tutor configura su precio igual. 204 (estado vacío esperado, B11),
        // no un 404 que ensucia la red de cada /cuenta/precio.
        mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", "Chubut")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void us6_sinAutenticacion_403() throws Exception {
        mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", "Buenos Aires"))
                .andExpect(status().isForbidden());
    }

    @Test
    void us6_elSnapshotTieneElValorCompleto_sinFk() throws Exception {
        String provincia = "Salta";
        Instant vigenteDesde = Instant.parse("2025-03-01T00:00:00Z");
        cargarReferencia(provincia, 3, "9500.00", vigenteDesde);
        String token = registrarTutorYToken(dniUnico());
        assertThat(usuarioRepository.findAll()).isNotEmpty();

        MvcResult res = mockMvc.perform(get("/api/pagos/precio-referencia/{provincia}", provincia)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("\"provincia\":\"Salta\"",
                "\"valorSugerido\":9500.00",
                "\"version\":3",
                "\"vigenteDesde\":\"2025-03-01T00:00:00Z\"");
        // Sin FK: la respuesta es autónoma (copia), no un enlace a la fila de
        // configuración — documentado en FR-PAG-005/006.
        assertThat(body).doesNotContain("_links", "href", "fk");
    }
}