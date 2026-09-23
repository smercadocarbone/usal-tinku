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
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook de MercadoPago (T-M5-03) de punta a punta: {@code POST
 * /api/webhooks/mercadopago} con firma x-signature válida sobre el manifest
 * (id/request-id/ts), reconciliación contra el pago pasada por {@code getPago},
 * creación del escrow y confirmación de la Reserva. Mismos mirrors que
 * PagosFlujosIntegracionTest; el HTTP hacia MP real se cubre en
 * MercadoPagoClientHttpTest (ADR-M5-01 no bloquea: nada pega contra el provider).
 *
 * Pago aprobado → 200 + transaccion retenido_escrow (comisión BR-PAG-01) +
 * reserva confirmada (+ sesión agendada por M3 vía ReservaConfirmadaEvent).
 * Cualquier desvío (firma inválida 401, pago no aprobado, external_reference
 * desconocida, reenvío idempotente, monto inconsistente 500 fail-closed) →
 * sin efectos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class PagosWebhookIntegracionTest {

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
    @Autowired ReservaRepository reservaRepository;
    @Autowired TransaccionRepository transaccionRepository;

    @Value("${tinku.mercadopago.webhook-secret}") String webhookSecret;

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
        return ZonedDateTime.of(fecha, LocalTime.of(15, 30), ReservasZonaHoraria.ZONA).toInstant();
    }

    private String registrarAdultoYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, true, true)))
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

    private UUID crearReservaEnPendiente() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez");
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        registrarMenor(dniMenor, tokenAr);
        UUID menorId = usuarioPorDni(dniMenor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        autorizar(tutorId, menorId, tokenAr);

        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", menorId.toString(),
                                "horario", dentroDeFranja(fecha).toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    private void cancelarReserva(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();
        reserva.setEstado(EstadoReserva.CANCELADA);
        reserva.setMotivoCancelacion(MotivoCancelacion.VOLUNTARIA);
        reservaRepository.save(reserva);
    }

    private String cuerpoNotificacion(String mpPaymentId) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("action", "payment.updated");
        payload.put("api_version", "v1");
        payload.put("type", "payment");
        payload.putObject("data").put("id", mpPaymentId);
        return objectMapper.writeValueAsString(payload);
    }

    /** Firma el manifest de MP tal como lo hace el provider (id/request-id/ts). */
    private String firma(String mpPaymentId, String xRequestId) {
        long ts = Instant.now().toEpochMilli();
        StringBuilder manifest = new StringBuilder();
        manifest.append("id:").append(mpPaymentId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return "ts=" + ts + ",v1=" + hmacHex(manifest.toString());
    }

    private String hmacHex(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(texto.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void pagoAprobado(String mpPaymentId, UUID reservaId, BigDecimal monto) {
        when(mercadopago.getPago(mpPaymentId)).thenReturn(
                new PagoMercadoPago(mpPaymentId, "approved", reservaId.toString(), monto));
    }

    /** Igual que el POST del webhook, pero esperando una largada común (para
     * disparar dos requests al unísono desde hilos distintos, AUD-010). */
    private int postWebhookStatus(String mpPaymentId, String cuerpo, String firma,
                                  CountDownLatch largada) throws Exception {
        largada.await(30, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma)
                        .content(cuerpo))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------ tests

    @Test
    void webhookFirmado_pagoAprobado_creaEscrowYConfirmaLaReserva() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-2026";
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("15000"));

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, "req-abc"))
                        .header("x-request-id", "req-abc")
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isOk());

        // Escrow retenido con el split de BR-PAG-01 (precio congelado 15000).
        Transaccion transaccion = transaccionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(transaccion.getMpPaymentId()).isEqualTo(mpPaymentId);
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(transaccion.getMontoBruto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(transaccion.getComisionPlataforma()).isEqualByComparingTo(new BigDecimal("4050.00"));
        assertThat(transaccion.getLiberarAt()).isNull();

        // Transición pendiente_pago → confirmada (y con ella M3 agenda la Sesión).
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void webhookFirmaInvalida_401_sinEfectos() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-malicioso";

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", "ts=1,v1=firma-que-no-coincide")
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isUnauthorized());

        assertThat(transaccionRepository.findByReservaId(reservaId)).isNotPresent();
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verify(mercadopago, times(0)).getPago(any());
    }

    @Test
    void webhook_pagoAunNoAprobado_ackSinEfectos() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-pendiente";
        when(mercadopago.getPago(mpPaymentId))
                .thenReturn(new PagoMercadoPago(mpPaymentId, "pending",
                        reservaId.toString(), new BigDecimal("15000")));

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, null))
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isOk());

        assertThat(transaccionRepository.findByReservaId(reservaId)).isNotPresent();
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    void webhook_reenvioDelMismoPago_idempotente() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-reeenviado";
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("15000"));
        String cuerpo = cuerpoNotificacion(mpPaymentId);
        String firma = firma(mpPaymentId, null);

        mockMvc.perform(post("/api/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId).header("x-signature", firma)
                        .content(cuerpo))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId).header("x-signature", firma)
                        .content(cuerpo))
                .andExpect(status().isOk());

        // Una sola fila de escrow y una sola reconciliación contra el provider.
        assertThat(transaccionRepository.findAll().stream()
                .filter(t -> t.getReservaId().equals(reservaId)).toList()).hasSize(1);
        verify(mercadopago, times(1)).getPago(mpPaymentId);
    }

    @Test
    void webhook_dosNotificacionesConcurrentesMismoMpPaymentId_unaSolaFilaYAmbas2xx() throws Exception {
        // AUD-010: MP reintenta agresivamente los webhooks que tardan (>22s) o
        // que no responden 2xx, y esos reintentos pueden solaparse. El guard de
        // aplicación (findByMpPaymentId antes de insertar) es un check-then-act
        // sin protección de base: dos hilos lo pasan antes de que el primero
        // commitee. Sin la unicidad de V24, esto crea DOS filas para la misma
        // Reserva y rompe para siempre cualquier findByReservaId de ese escrow
        // (ver javadoc de EscrowService.reembolsarPagoTardio).
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-concurrente";
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("15000"));
        String cuerpo = cuerpoNotificacion(mpPaymentId);
        String firma = firma(mpPaymentId, null);

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> res1 = pool.submit(() -> postWebhookStatus(mpPaymentId, cuerpo, firma, largada));
            Future<Integer> res2 = pool.submit(() -> postWebhookStatus(mpPaymentId, cuerpo, firma, largada));
            largada.countDown();

            int s1 = res1.get(30, TimeUnit.SECONDS);
            int s2 = res2.get(30, TimeUnit.SECONDS);

            // Nunca un 5xx: MP reintenta los no-2xx y entra en loop (mismo
            // javadoc de la clase). El hilo que pierde la carrera debe tratar
            // la violación del índice único como no-op idempotente.
            assertThat(s1).isEqualTo(200);
            assertThat(s2).isEqualTo(200);

            assertThat(transaccionRepository.findAll().stream()
                    .filter(t -> t.getReservaId().equals(reservaId)).toList()).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void webhook_dosPagosDistintosConcurrentesMismaReserva_unoConfirmaYElOtroSeReembolsa() throws Exception {
        // Efecto colateral de AUD-010: con uq_transacciones_reserva, dos pagos DISTINTOS
        // (ej. el pagador apretó "Pagar" dos veces en pestañas distintas) para la misma
        // Reserva llegaban juntos, los dos veían pendiente_pago, y el perdedor caía en
        // el catch del índice único como "no-op": su plata quedaba cobrada y nunca se
        // devolvía. El perdedor tiene que ir por el reembolso de pago tardío.
        UUID reservaId = crearReservaEnPendiente();
        pagoAprobado("pago-a", reservaId, new BigDecimal("15000"));
        pagoAprobado("pago-b", reservaId, new BigDecimal("15000"));
        String cuerpoA = cuerpoNotificacion("pago-a");
        String cuerpoB = cuerpoNotificacion("pago-b");
        String firmaA = firma("pago-a", null);
        String firmaB = firma("pago-b", null);

        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> resA = pool.submit(() -> postWebhookStatus("pago-a", cuerpoA, firmaA, largada));
            Future<Integer> resB = pool.submit(() -> postWebhookStatus("pago-b", cuerpoB, firmaB, largada));
            largada.countDown();

            assertThat(resA.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(resB.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        } finally {
            pool.shutdownNow();
        }

        // Una sola fila (la del ganador) y la Reserva confirmada una vez.
        List<Transaccion> filas = transaccionRepository.findAll().stream()
                .filter(t -> t.getReservaId().equals(reservaId)).toList();
        assertThat(filas).hasSize(1);
        String ganador = filas.get(0).getMpPaymentId();
        String perdedor = ganador.equals("pago-a") ? "pago-b" : "pago-a";
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        // El pago perdedor se devuelve entero; el ganador, nunca.
        verify(mercadopago, times(1)).reembolsarPago(perdedor);
        verify(mercadopago, never()).reembolsarPago(ganador);
    }

    @Test
    void webhook_montoQueNoCoincideConElPrecio_500_failClosed() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-fraudulento";
        // El ladrón paga de menos: el guard de integridad NO confirma.
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("14999.99"));

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, null))
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isInternalServerError());

        assertThat(transaccionRepository.findByReservaId(reservaId)).isNotPresent();
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    void webhook_externalReferenceDesconocida_ackSinEfectos() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-ajeno";
        when(mercadopago.getPago(mpPaymentId)).thenReturn(
                new PagoMercadoPago(mpPaymentId, "approved",
                        UUID.randomUUID().toString(), new BigDecimal("15000")));

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, null))
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isOk());

        assertThat(transaccionRepository.findByReservaId(reservaId)).isNotPresent();
    }

    @Test
    void webhook_topicQueNoEsPago_ackSinEfectos() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        String mpPaymentId = "pago-otro-topic";

        var payload = objectMapper.createObjectNode();
        payload.put("action", "something.created");
        payload.put("type", "preapproval");
        payload.putObject("data").put("id", mpPaymentId);

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, null))
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        assertThat(transaccionRepository.findByReservaId(reservaId)).isNotPresent();
        verify(mercadopago, times(0)).getPago(any());
    }

    // ------------------------------------------------ M5-D: pago tardío

    @Test
    void webhook_pagoAprobadoPeroReservaYaCancelada_reembolsaTotalYRegistraTransaccion() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        cancelarReserva(reservaId);
        String mpPaymentId = "pago-tardio";
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("15000"));

        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, null))
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isOk());

        // El dinero se cobró pero la sesión no va a existir → reembolso total
        // (Chunk M5-D). La fila REEMBOLSADO con comisión 0 es el ancla de
        // auditoría e idempotencia del reenvío.
        Transaccion transaccion = transaccionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(transaccion.getMpPaymentId()).isEqualTo(mpPaymentId);
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(transaccion.getMontoBruto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(transaccion.getComisionPlataforma()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
        verify(mercadopago).reembolsarPago(mpPaymentId);
    }

    @Test
    void webhook_reenvioDePagoTardio_noReEmbolsa() throws Exception {
        UUID reservaId = crearReservaEnPendiente();
        cancelarReserva(reservaId);
        String mpPaymentId = "pago-tardio-ree";
        pagoAprobado(mpPaymentId, reservaId, new BigDecimal("15000"));
        String cuerpo = cuerpoNotificacion(mpPaymentId);
        String firma = firma(mpPaymentId, null);

        mockMvc.perform(post("/api/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId).header("x-signature", firma)
                        .content(cuerpo))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/webhooks/mercadopago").contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId).header("x-signature", firma)
                        .content(cuerpo))
                .andExpect(status().isOk());

        // Una sola fila y un solo reembolso: el reenvío no re-embolsa.
        assertThat(transaccionRepository.findAll().stream()
                .filter(t -> t.getReservaId().equals(reservaId)).toList()).hasSize(1);
        verify(mercadopago, times(1)).reembolsarPago(mpPaymentId);
    }
}