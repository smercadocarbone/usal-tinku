package com.tinku.pagos.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.pagos.model.TarifaTutor;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.pagos.repository.TarifaTutorRepository;
import com.tinku.pagos.service.MercadoPagoNoConfiguradoException;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.ReservasZonaHoraria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del Chunk M5-A (T-M5-02): {@code POST /api/pagos/preferencia}
 * ejercitado de punta a punta (HTTP + Spring Security + JPA + Flyway + PostgreSQL
 * via Testcontainers). El cliente de MercadoPago va mockeado — la llamada real
 * al provider se cubre en {@code MercadoPagoClientHttpTest} con un stub HTTP local
 * (ADR-M5-01 del Plan no bloquea estos tests: no pegan contra el provider real).
 *
 * El escenario de datos replica el de ReservasFlujosIntegracionTest: tarifa 15000
 * vía la implementación real de M5-H (fallback de dev en application-test.yml).
 * BR-PAG-01 se verifica con
 * ArgumentCaptor sobre la PreferenciaRequest: comisión = 27% del monto congelado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// T-TES-10/DT7: crea Reservas con beneficiario menor para ejercitar la
// preferencia de pago del AR; la flag en true mantiene el escenario activo
// (el corte por defecto lo cubre GateMenoresPilotoIntegracionTest).
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class PagosFlujosIntegracionTest {

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

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaService reservaService;
    @Autowired TarifaTutorRepository tarifaTutorRepository;
    @org.springframework.beans.factory.annotation.Value("${tinku.tarifa.piso-hora-ars}") BigDecimal pisoHora;
    @Autowired ReservaRepository reservaRepository;

    @MockBean OcrService ocrService;
    @MockBean MatchingServiceClient matchingClient;
    @MockBean ReputacionSignalProvider reputacion;
    @MockBean ReputacionBloqueoProveedor reputacionBloqueo;
    @MockBean MercadoPagoClient mercadopago;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 30_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
        when(mercadopago.crearPreferencia(any()))
                .thenReturn(new PreferenciaPago("pref-mock", "https://mercadopago.com/mock", false));
    }

    // ------------------------------------------------ helpers

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
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

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    private void registrarMenor(String dniMenor, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
    }

    private void autorizar(UUID tutorId, UUID menorId, String tokenAr) throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    private void publicarFranjaPuntual(String tokenTutor, LocalDate fecha) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", "15:00",
                                "horaFin", "16:00"))))
                .andExpect(status().isCreated());
    }

    private Instant dentroDeFranja(LocalDate fecha) {
        // D6: 15:00 por 60 min ocupa la franja 15-16 entera → precio = tarifa por hora.
        return ZonedDateTime.of(fecha, LocalTime.of(15, 0), ReservasZonaHoraria.ZONA).toInstant();
    }

    /** AR con un menor a cargo + Tutor con franja puntual 15:00-16:00 en `fecha`. */
    private record EscenarioPago(String dniAr, String tokenAr, String tokenMenor, String tokenTutor,
                                 UUID menorId, UUID tutorId, LocalDate fecha, Instant horario) {
    }

    private EscenarioPago escenarioPago() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        registrarMenor(dniMenor, tokenAr);
        UUID menorId = usuarioPorDni(dniMenor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        autorizar(tutorId, menorId, tokenAr);
        String tokenMenor = login(dniMenor);
        return new EscenarioPago(dniAr, tokenAr, tokenMenor, tokenTutor, menorId, tutorId, fecha,
                dentroDeFranja(fecha));
    }

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

    private UUID crearReservaDirecta(String token, UUID tutorId, UUID beneficiarioId, Instant horario)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", beneficiarioId == null ? "" : beneficiarioId.toString(),
                                "horario", horario.toString(),
                                "duracionMinutos", 60))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private void pedirPreferencia(String token, UUID reservaId) throws Exception {
        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------ tests

    @Test
    void us1_adultoResponsable_generaPreferencia_reservaDeSuMenor_conSplit15() throws Exception {
        EscenarioPago e = escenarioPago();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenAr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenciaId").value("pref-mock"))
                .andExpect(jsonPath("$.initPoint").value("https://mercadopago.com/mock"));

        // BR-PAG-01: la comisión de la plataforma llega como marketplace_fee sobre
        // el precio congelado de la Reserva (FR-PAG-013): 15000 → 4050.00 (27%).
        ArgumentCaptor<PreferenciaRequest> captor = ArgumentCaptor.forClass(PreferenciaRequest.class);
        verify(mercadopago).crearPreferencia(captor.capture());
        PreferenciaRequest pedido = captor.getValue();
        assertThat(pedido.reservaId()).isEqualTo(reservaId);
        assertThat(pedido.montoBruto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(pedido.comisionPlataforma()).isEqualByComparingTo(new BigDecimal("4050.00"));
    }

    @Test
    void us1_estudianteAdulto_generaPreferencia_suPropiaReserva() throws Exception {
        String dniEst = dniUnico();
        String dniTutor = dniUnico();
        String tokenEst = registrarAdultoYToken(dniEst, "Lucas", "Diaz", true, false);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        Instant horario = dentroDeFranja(fecha);

        UUID reservaId = crearReservaDirecta(tokenEst, tutorId, null, horario);
        pedirPreferencia(tokenEst, reservaId);
    }

    @Test
    void us1_menor_noGeneraPreferencia_403() throws Exception {
        EscenarioPago e = escenarioPago();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void us1_tutor_noGeneraPreferencia_403() throws Exception {
        EscenarioPago e = escenarioPago();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenTutor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void us1_reservaInexistente_404() throws Exception {
        EscenarioPago e = escenarioPago();
        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenAr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "reservaId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void us1_reservaYaConfirmada_noGeneraPreferencia_422() throws Exception {
        EscenarioPago e = escenarioPago();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());
        reservaService.confirmarPagoSimulado(reservaId);

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenAr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void us1_sinCredencialesMercadoPago_503() throws Exception {
        EscenarioPago e = escenarioPago();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());
        when(mercadopago.crearPreferencia(any()))
                .thenThrow(new MercadoPagoNoConfiguradoException());

        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenAr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void us1_cuerpoSinReservaId_400() throws Exception {
        EscenarioPago e = escenarioPago();
        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + e.tokenAr())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------- US-6 (M5-H, tarifa del Tutor)

    @Test
    void us6_tutor_configuraTarifaPorHora_yLaReservaLaCongela() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        // El Tutor fija su precio por hora (FR-PAG-006, upsert sobre tarifas_tutor).
        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("precioHora", 22000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorId").value(tutorId.toString()))
                .andExpect(jsonPath("$.precioHora").value(new BigDecimal("22000")));
        assertThat(tarifaTutorRepository.findByTutorId(tutorId).orElseThrow().getPrecioHora())
                .isEqualByComparingTo(new BigDecimal("22000"));

        // Actualizar de nuevo = upsert, no una fila duplicada. (Se cuenta antes/después:
        // la base es compartida con los demás tests de la clase, T06 incluidos.)
        long filasAntes = tarifaTutorRepository.count();
        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("precioHora", 23000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(new BigDecimal("23000")));
        assertThat(tarifaTutorRepository.count()).isEqualTo(filasAntes);

        // La Reserva congela ESE precio (FR-PAG-013): 22000+, no el stub de dev.
        String dniEst = dniUnico();
        String tokenEst = registrarAdultoYToken(dniEst, "Lucas", "Diaz", true, false);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        Instant horario = dentroDeFranja(fecha);
        UUID reservaId = crearReservaDirecta(tokenEst, tutorId, null, horario);
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();
        // se resuelve via carga batch más abajo
        assertThat(reserva.getPrecio()).isEqualByComparingTo(new BigDecimal("23000"));

        // y la preferencia de pago se arma sobre ese precio congelado.
        pedirPreferencia(tokenEst, reservaId);
        ArgumentCaptor<PreferenciaRequest> captor = ArgumentCaptor.forClass(PreferenciaRequest.class);
        verify(mercadopago).crearPreferencia(captor.capture());
        assertThat(captor.getValue().montoBruto()).isEqualByComparingTo(new BigDecimal("23000"));
    }

    /** D6 + T4: el contrato es {@code precioHora} en camelCase (el frontend mandaba
     *  snake_case y el backend lo rechazaba siempre) y la Reserva se cotiza por hora. */
    @Test
    void putTarifa_conPrecioHoraCamelCase_200_yLaReservaSeCotizaPorHora() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioHora\": 10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(10000));
        mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(10000));

        String tokenEst = registrarAdultoYToken(dniUnico(), "Lucas", "Diaz", true, false);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horario", dentroDeFranja(fecha).toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reservaId = UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        // 10000 por hora × 30 minutos.
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getPrecio())
                .isEqualByComparingTo(new BigDecimal("5000"));
    }

    // ------------------------------------------------ T06: piso de tarifa (DT2, PT3/PT4)

    private org.springframework.test.web.servlet.ResultActions putTarifa(String token, BigDecimal precio) throws Exception {
        return mockMvc.perform(put("/api/pagos/tarifa")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("precioHora", precio))));
    }

    private UUID reservar30min(String tokenTutor, UUID tutorId, int diasAdelante) throws Exception {
        String tokenEst = registrarAdultoYToken(dniUnico(), "Lucas", "Diaz", true, false);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(diasAdelante);
        publicarFranjaPuntual(tokenTutor, fecha);
        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horario", dentroDeFranja(fecha).toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void actualizarTarifa_bajoPiso_422() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");

        putTarifa(tokenTutor, pisoHora.subtract(new BigDecimal("0.01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.pisoHora").value(pisoHora.doubleValue()));
        assertThat(tarifaTutorRepository.findByTutorId(usuarioPorDni(dniTutor).getId())).isEmpty();
    }

    @Test
    void actualizarTarifa_igualAlPiso_ok() throws Exception {
        String tokenTutor = registrarTutorYToken(dniUnico(), "Pablo", "Sosa");

        putTarifa(tokenTutor, pisoHora)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(pisoHora.doubleValue()))
                .andExpect(jsonPath("$.pisoHora").value(pisoHora.doubleValue()));
    }

    /** PT4: el piso no es retroactivo — una tarifa ya guardada por debajo sigue vigente
     *  (y cotizando) hasta que el Tutor la edite; el GET la marca con el piso al lado. */
    @Test
    void tarifaExistenteBajoPiso_noSeModificaAlDesplegar() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        BigDecimal vieja = pisoHora.subtract(new BigDecimal("100"));
        TarifaTutor anterior = new TarifaTutor();
        anterior.setTutorId(tutorId);
        anterior.setPrecioHora(vieja);
        tarifaTutorRepository.save(anterior);

        mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(vieja.doubleValue()))
                .andExpect(jsonPath("$.pisoHora").value(pisoHora.doubleValue()));
        UUID reservaId = reservar30min(tokenTutor, tutorId, 2);
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getPrecio())
                .isEqualByComparingTo(vieja.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP));
        assertThat(tarifaTutorRepository.findByTutorId(tutorId).orElseThrow().getPrecioHora())
                .isEqualByComparingTo(vieja);
    }

    /** Un Tutor nuevo (sin tarifa) también tiene que conocer el piso antes de guardar. */
    @Test
    void getTarifa_sinTarifa_devuelveElPisoConPrecioNulo() throws Exception {
        String tokenTutor = registrarTutorYToken(dniUnico(), "Pablo", "Sosa");

        mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").doesNotExist())
                .andExpect(jsonPath("$.pisoHora").value(pisoHora.doubleValue()));
    }

    @Test
    void precioReserva_seCalculaConPrecioHoraVigente() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        putTarifa(tokenTutor, new BigDecimal("8000")).andExpect(status().isOk());
        UUID primera = reservar30min(tokenTutor, tutorId, 2);
        putTarifa(tokenTutor, new BigDecimal("9000")).andExpect(status().isOk());
        UUID segunda = reservar30min(tokenTutor, tutorId, 3);

        assertThat(reservaRepository.findById(primera).orElseThrow().getPrecio()).isEqualByComparingTo("4000");
        assertThat(reservaRepository.findById(segunda).orElseThrow().getPrecio()).isEqualByComparingTo("4500");
    }

    @Test
    void us6_noTutor_noPuedeFijarTarifa_403() throws Exception {
        String dniEst = dniUnico();
        String tokenEst = registrarAdultoYToken(dniEst, "Lucas", "Diaz", true, false);

        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("precioHora", 22000))))
                .andExpect(status().isForbidden());
    }

    @Test
    void us6_precioInvalido_400() throws Exception {
        String dniTutor = dniUnico();
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");

        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("precioHora", 0))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest());
    }
}