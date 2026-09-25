package com.tinku.pagos.web;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.pagos.model.CuentaMpTutor;
import com.tinku.pagos.model.EstadoCuentaMp;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoOAuthClient;
import com.tinku.pagos.port.MercadoPagoOAuthClient.TokensMp;
import com.tinku.pagos.repository.CuentaMpTutorRepository;
import com.tinku.pagos.service.CuentasMpService;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.service.ReservasZonaHoraria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3 / ADR-M5-02 (modelo A): con la app de marketplace configurada, cada Tutor conecta su
 * MercadoPago por OAuth, los tokens se guardan cifrados, la preferencia sale con SU token, y sin
 * cuenta conectada no se le puede reservar. El OAuth de MP está mockeado (no hay sandbox en CI).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "tinku.mercadopago.oauth.client-id=app-123",
        "tinku.mercadopago.oauth.client-secret=secreto",
        "tinku.mercadopago.oauth.redirect-uri=https://api.tinku.test/api/pagos/mp/callback",
        "tinku.app.url-publica=https://tinku.test",
        // 32 bytes en cero, base64: solo para test.
        "tinku.pagos.clave-cifrado=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
class CuentaMpIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired CuentaMpTutorRepository cuentaRepo;
    @Autowired CuentasMpService cuentasMp;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;
    @MockitoBean ReputacionSignalProvider reputacion;
    @MockitoBean ReputacionBloqueoProveedor reputacionBloqueo;
    @MockitoBean MercadoPagoClient mercadopago;
    @MockitoBean MercadoPagoOAuthClient oauth;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @BeforeEach
    void mocks() {
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
        when(mercadopago.crearPreferencia(any(), any()))
                .thenReturn(new PreferenciaPago("pref-oauth", "https://mercadopago.com/mock", false));
    }

    // ------------------------------------------------ helpers

    private String dniUnico() {
        return String.format("%08d", 36_000_000 + CONTADOR.incrementAndGet());
    }

    private String registrar(String ruta, Object datos, String dni) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, "Pablo", "Sosa", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart(ruta)
                        .file(new MockMultipartFile("datos", "datos", MediaType.APPLICATION_JSON_VALUE,
                                objectMapper.writeValueAsBytes(datos)))
                        .file(new MockMultipartFile("fotoDni", "dni.png", "application/octet-stream", new byte[]{1})))
                .andExpect(status().isCreated());
        MvcResult res = mockMvc.perform(post("/api/usuarios/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String tutor(String dni) throws Exception {
        return registrar("/api/tutores/registro", new RegistroTutorRequest(dni, "Pablo", "Sosa",
                LocalDate.of(1990, 5, 15), dni + "@tinku.test", PASSWORD), dni);
    }

    private String estudiante(String dni) throws Exception {
        return registrar("/api/usuarios/registro", new RegistroAdultoRequest(dni, "Pablo", "Sosa",
                LocalDate.of(1990, 5, 15), dni + "@tinku.test", PASSWORD, true, false), dni);
    }

    private UUID idDe(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow().getId();
    }

    /** Conecta la cuenta de MP del Tutor por el flujo real (conectar → callback con el state). */
    private void conectar(String tokenTutor, String mpUserId, String accessToken) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/pagos/mp/conectar").header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isOk()).andReturn();
        String url = objectMapper.readTree(res.getResponse().getContentAsString()).get("url").asText();
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        when(oauth.canjearCodigo(eq("code-" + mpUserId), any(), any()))
                .thenReturn(new TokensMp(accessToken, "refresh-" + mpUserId, mpUserId, "pk", 15_552_000));
        mockMvc.perform(get("/api/pagos/mp/callback").param("code", "code-" + mpUserId).param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://tinku.test/cuenta/cobros?mp=ok"));
    }

    private MvcResult reservar(String tokenEst, UUID tutorId, int dias) throws Exception {
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(dias);
        Instant horario = ZonedDateTime.of(fecha, LocalTime.of(15, 0), ReservasZonaHoraria.ZONA).toInstant();
        return mockMvc.perform(post("/api/reservas").header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tutorId", tutorId.toString(),
                                "beneficiarioId", "", "horario", horario.toString(), "duracionMinutos", 60))))
                .andReturn();
    }

    private void franja(String tokenTutor, int dias) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas").header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(dias).toString(),
                                "horaInicio", "15:00", "horaFin", "16:00"))))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------ tests

    @Test
    void conectar_urlConStateYPkce_callbackGuardaTokensCifrados() throws Exception {
        String dni = dniUnico();
        String tk = tutor(dni);
        MvcResult res = mockMvc.perform(get("/api/pagos/mp/conectar").header("Authorization", "Bearer " + tk))
                .andExpect(status().isOk()).andReturn();
        var q = UriComponentsBuilder.fromUriString(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("url").asText()).build().getQueryParams();
        assertThat(q.getFirst("client_id")).isEqualTo("app-123");
        assertThat(q.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(q.getFirst("state")).hasSizeGreaterThan(30);

        conectar(tk, "9001", "APP_USR-secreto-9001");

        CuentaMpTutor cuenta = cuentaRepo.findById(idDe(dni)).orElseThrow();
        assertThat(cuenta.getEstado()).isEqualTo(EstadoCuentaMp.CONECTADA);
        assertThat(new String(cuenta.getAccessTokenCifrado(), StandardCharsets.ISO_8859_1)).doesNotContain("secreto");
        assertThat(cuentasMp.tokenParaTutor(idDe(dni))).isEqualTo("APP_USR-secreto-9001");
        mockMvc.perform(get("/api/pagos/mp/estado").header("Authorization", "Bearer " + tk))
                .andExpect(jsonPath("$.requerida").value(true))
                .andExpect(jsonPath("$.estado").value("CONECTADA"));
    }

    @Test
    void callbackConStateInvalido_noConectaNada() throws Exception {
        mockMvc.perform(get("/api/pagos/mp/callback").param("code", "x").param("state", "inventado"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://tinku.test/cuenta/cobros?mp=vencido"));
        org.mockito.Mockito.verifyNoInteractions(oauth);
    }

    @Test
    void soloUnTutorConecta_403() throws Exception {
        String tk = estudiante(dniUnico());
        mockMvc.perform(get("/api/pagos/mp/conectar").header("Authorization", "Bearer " + tk))
                .andExpect(status().isForbidden());
    }

    /** Sin cuenta conectada no se reserva; conectada, la preferencia sale con el token del Tutor. */
    @Test
    void tutorSinCuenta_noReservable_422_yConCuentaLaPreferenciaUsaSuToken() throws Exception {
        String dniTutor = dniUnico();
        String tkTutor = tutor(dniTutor);
        franja(tkTutor, 2);
        String tkEst = estudiante(dniUnico());

        assertThat(reservar(tkEst, idDe(dniTutor), 2).getResponse().getStatus()).isEqualTo(422);

        conectar(tkTutor, "9002", "APP_USR-tutor-9002");
        MvcResult ok = reservar(tkEst, idDe(dniTutor), 2);
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        String reservaId = objectMapper.readTree(ok.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/pagos/preferencia").header("Authorization", "Bearer " + tkEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId))))
                .andExpect(status().isOk());
        verify(mercadopago).crearPreferencia(any(), eq("APP_USR-tutor-9002"));

        // Con una reserva pendiente no se puede desconectar (Tinku podría tener que reembolsar).
        mockMvc.perform(delete("/api/pagos/mp/conexion").header("Authorization", "Bearer " + tkTutor))
                .andExpect(status().isConflict());
        jdbc.update("UPDATE reservas.reservas SET estado = 'cancelada', motivo_cancelacion = 'voluntaria' WHERE id = ?::uuid",
                reservaId);
        mockMvc.perform(delete("/api/pagos/mp/conexion").header("Authorization", "Bearer " + tkTutor))
                .andExpect(status().isNoContent());
        assertThat(cuentaRepo.findById(idDe(dniTutor)).orElseThrow().getEstado()).isEqualTo(EstadoCuentaMp.REVOCADA);
    }

    @Test
    void refresco_renuevaLosQueVencenPronto_yUnFalloDejaErrorYAvisa() throws Exception {
        String dniA = dniUnico();
        String dniB = dniUnico();
        conectar(tutor(dniA), "9003", "APP_USR-viejo-a");
        conectar(tutor(dniB), "9004", "APP_USR-viejo-b");
        jdbc.update("UPDATE pagos.cuentas_mp_tutor SET expira_at = now() + interval '5 days' WHERE tutor_id IN (?, ?)",
                idDe(dniA), idDe(dniB));
        when(oauth.refrescar("refresh-9003"))
                .thenReturn(new TokensMp("APP_USR-nuevo-a", "refresh-9003b", "9003", "pk", 15_552_000));
        when(oauth.refrescar("refresh-9004")).thenThrow(new MercadoPagoNoDisponibleException());

        cuentasMp.refrescarPorVencer();

        assertThat(cuentasMp.tokenParaTutor(idDe(dniA))).isEqualTo("APP_USR-nuevo-a");
        assertThat(cuentaRepo.findById(idDe(dniA)).orElseThrow().getExpiraAt())
                .isAfter(Instant.now().plus(170, ChronoUnit.DAYS));
        assertThat(cuentaRepo.findById(idDe(dniB)).orElseThrow().getEstado()).isEqualTo(EstadoCuentaMp.ERROR);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin.notificaciones WHERE destinatario_id = ? "
                + "AND tipo = 'MP_CUENTA_DESCONECTADA'", Integer.class, idDe(dniB))).isEqualTo(1);
    }

    @Test
    void unaCuentaDeMpNoSeConectaADosTutores() throws Exception {
        conectar(tutor(dniUnico()), "9005", "APP_USR-a");
        String tkOtro = tutor(dniUnico());
        MvcResult res = mockMvc.perform(get("/api/pagos/mp/conectar").header("Authorization", "Bearer " + tkOtro))
                .andReturn();
        String state = UriComponentsBuilder.fromUriString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("url").asText()).build().getQueryParams().getFirst("state");
        mockMvc.perform(get("/api/pagos/mp/callback").param("code", "code-9005").param("state", state))
                .andExpect(header().string("Location", "https://tinku.test/cuenta/cobros?mp=otra-cuenta"));
    }
}
