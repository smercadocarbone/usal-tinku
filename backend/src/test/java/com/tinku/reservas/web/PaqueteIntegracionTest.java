package com.tinku.reservas.web;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.pagos.service.EscrowService;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Paquete;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.PaqueteRepository;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enmienda v2.5 — Spec_M4 US-14 y Spec_M5 US-12 (paquete mensual, FR-RES-032..037,
 * FR-PAG-021..023, ADR-M5-03), de punta a punta, con MercadoPago simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class PaqueteIntegracionTest extends FlujosReservaBase {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean MercadoPagoClient mercadopago;
    @Autowired EscrowService escrow;
    @Autowired ReservaRepository reservas;
    @Autowired PaqueteRepository paquetes;
    @Autowired TransaccionRepository transacciones;
    @Autowired Scheduler scheduler;

    @Override
    int baseDnis() {
        return 44_000_000;
    }

    @BeforeEach
    void mp() {
        when(mercadopago.crearPreferencia(any(), any())).thenReturn(new PreferenciaPago("pref-p", "https://mp/p", false));
    }

    private record Escenario(Adulto e, LocalDate dia) {
    }

    /** Tutor a $12.000 la hora con el paquete al 10 % y una franja semanal 16-18 (la puntual 15-16 del fixture no se pisa) en el día de la primera clase. */
    private Escenario escenario() throws Exception {
        Adulto e = adultoConTutor();
        tarifa(e.tokenTutor(), 12000);
        configurarPaquete(e.tokenTutor(), true, 10).andExpect(status().isOk());
        publicarFranjaSemanal(e.tokenTutor(), e.fecha(), "16:00", "18:00");
        return new Escenario(e, e.fecha());
    }

    private void tarifa(String token, int precioHora) throws Exception {
        mockMvc.perform(put("/api/pagos/tarifa").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("precioHora", precioHora))))
                .andExpect(status().isOk());
    }

    private ResultActions configurarPaquete(String token, boolean habilitado, int descuento) throws Exception {
        return mockMvc.perform(put("/api/pagos/tarifa/paquete").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("habilitado", habilitado, "descuentoPorcentaje", descuento))));
    }

    private ResultActions pedirPaquete(String token, UUID tutorId, Instant horario) throws Exception {
        return mockMvc.perform(post("/api/reservas/paquete").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tutorId", tutorId.toString(), "horario", horario.toString(), "duracionMinutos", 60))));
    }

    private UUID crearPaquete(Escenario s) throws Exception {
        String cuerpo = pedirPaquete(s.e().tokenAlumno(), s.e().tutorId(), a(s.dia(), 16, 0))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(cuerpo).get("id").asText());
    }

    private List<Reserva> clases(UUID paqueteId) {
        return reservas.findByPaquete_IdOrderByHorario(paqueteId);
    }

    /** El webhook de MercadoPago con el pago del paquete (external_reference = la ancla). */
    private void pagar(UUID paqueteId, String mpId, String monto) {
        UUID ancla = paquetes.findById(paqueteId).orElseThrow().getReservaAnclaId();
        PagoMercadoPago pago = new PagoMercadoPago(mpId, "approved", ancla.toString(), new BigDecimal(monto));
        when(mercadopago.getPago(eq(mpId), any())).thenReturn(pago);
        escrow.procesarPagoAprobado(mpId, null);
    }

    private Transaccion transaccion(Reserva r) {
        return transacciones.findByReservaId(r.getId()).orElseThrow();
    }

    // ------------------------------------------------ armado y pago

    @Test
    void us14_seArmanCuatroClasesSemanales_conDescuento_yUnSoloPagoLasConfirma() throws Exception {
        Escenario s = escenario();
        mockMvc.perform(get("/api/reservas/paquete/oferta").param("tutorId", s.e().tutorId().toString())
                        .header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.descuentoPorcentaje").value(10));

        String cuerpo = pedirPaquete(s.e().tokenAlumno(), s.e().tutorId(), a(s.dia(), 16, 0))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioTotal").value(43200.00)) // 12.000 × 0,9 × 4
                .andExpect(jsonPath("$.fechas.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        UUID paqueteId = UUID.fromString(objectMapper.readTree(cuerpo).get("id").asText());
        List<Reserva> clases = clases(paqueteId);
        assertThat(clases).hasSize(4).allMatch(r -> r.getEstado() == EstadoReserva.PENDIENTE_PAGO);
        assertThat(clases.get(3).getHorario()).isEqualTo(a(s.dia().plusWeeks(3), 16, 0));
        assertThat(clases).allMatch(r -> r.getPrecio().compareTo(new BigDecimal("10800")) == 0);
        // Un solo timeout de pago: el de la ancla.
        assertThat(scheduler.checkExists(reservaService.triggerTimeoutPago(clases.get(0).getId()))).isTrue();
        assertThat(scheduler.checkExists(reservaService.triggerTimeoutPago(clases.get(1).getId()))).isFalse();

        // La preferencia es por el total, desde la ancla; marketplace_fee = 4 × 21 % de 10.800.
        mockMvc.perform(post("/api/pagos/preferencia").header("Authorization", "Bearer " + s.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", clases.get(0).getId().toString()))))
                .andExpect(status().isOk());
        ArgumentCaptor<PreferenciaRequest> pedido = ArgumentCaptor.forClass(PreferenciaRequest.class);
        verify(mercadopago).crearPreferencia(pedido.capture(), any());
        assertThat(pedido.getValue().montoBruto()).isEqualByComparingTo("43200");
        assertThat(pedido.getValue().comisionPlataforma()).isEqualByComparingTo("9072.00");
        // Pagar desde otra clase del paquete no corresponde: ni se ofrece ni se acepta.
        mockMvc.perform(get("/api/reservas/{id}", clases.get(0).getId()).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(jsonPath("$.puedePagar").value(true));
        mockMvc.perform(get("/api/reservas/{id}", clases.get(1).getId()).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(jsonPath("$.puedePagar").value(false));
        mockMvc.perform(post("/api/pagos/preferencia").header("Authorization", "Bearer " + s.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reservaId", clases.get(1).getId().toString()))))
                .andExpect(status().isUnprocessableEntity());

        pagar(paqueteId, "mp-paq-1", "43200");
        pagar(paqueteId, "mp-paq-1", "43200"); // reintento del webhook: no-op

        assertThat(clases(paqueteId)).allMatch(r -> r.getEstado() == EstadoReserva.CONFIRMADA);
        assertThat(paquetes.findById(paqueteId).orElseThrow().getEstado()).isEqualTo(Paquete.CONFIRMADO);
        assertThat(clases(paqueteId).stream().map(r -> transaccion(r).getMpPaymentId()))
                .containsExactly("mp-paq-1", "mp-paq-1#2", "mp-paq-1#3", "mp-paq-1#4");
        assertThat(clases(paqueteId).stream().map(r -> transaccion(r).idPagoMp())).containsOnly("mp-paq-1");

        mockMvc.perform(get("/api/reservas/{id}", clases.get(2).getId()).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(jsonPath("$.paqueteId").value(paqueteId.toString()))
                .andExpect(jsonPath("$.paqueteClase").value(3))
                .andExpect(jsonPath("$.paqueteTotal").value(43200.00))
                .andExpect(jsonPath("$.puedeCancelarPaquete").value(true));
    }

    @Test
    void frRes032_siUnaFechaChoca_422ConLasFechas_ySinOfertaNoHayPaquete() throws Exception {
        Escenario s = escenario();
        String tokenOtro = registrarAdulto(dniUnico(), "Otro", true, false);
        UUID ocupada = reservar(tokenOtro, s.e().tutorId(), null, a(s.dia().plusWeeks(2), 16, 0), 60);
        confirmarPago(ocupada);

        pedirPaquete(s.e().tokenAlumno(), s.e().tutorId(), a(s.dia(), 16, 0))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fechas.length()").value(1))
                .andExpect(jsonPath("$.fechas[0]").value(a(s.dia().plusWeeks(2), 16, 0).toString()));

        configurarPaquete(s.e().tokenTutor(), false, 0).andExpect(status().isOk());
        pedirPaquete(s.e().tokenAlumno(), s.e().tutorId(), a(s.dia(), 17, 0)).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes033_sinPagarVencenLasCuatroJuntas() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);

        reservaService.expirarPorTimeoutPago(clases(paqueteId).get(0).getId());

        assertThat(clases(paqueteId)).allMatch(r -> r.getEstado() == EstadoReserva.CANCELADA);
        assertThat(paquetes.findById(paqueteId).orElseThrow().getEstado()).isEqualTo(Paquete.CANCELADO);
    }

    // ------------------------------------------------ cancelaciones y devoluciones

    @Test
    void frRes037_siElTutorCancelaUnaClase_seDevuelveSoloEsaClase() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-2", "43200");
        Reserva segunda = clases(paqueteId).get(1);

        mockMvc.perform(post("/api/reservas/{id}/cancelar", segunda.getId()).header("Authorization", "Bearer " + s.e().tokenTutor()))
                .andExpect(status().isOk());

        verify(mercadopago).reembolsarPagoParcial(eq("mp-paq-2"), eq(new BigDecimal("10800.00")), any(),
                eq("clase-" + transaccion(segunda).getId()));
        verify(mercadopago, never()).reembolsarPago(anyString(), any());
        assertThat(transaccion(segunda).getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(transaccion(clases(paqueteId).get(0)).getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
    }

    @Test
    void frRes036_siElAlumnoCancelaUnaClase_noHayDevolucion_yElTutorLaCobra() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-3", "43200");
        Reserva tercera = clases(paqueteId).get(2);
        mockMvc.perform(get("/api/reservas/{id}", tercera.getId()).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(jsonPath("$.cancelarReembolsaTotal").value(false));

        mockMvc.perform(post("/api/reservas/{id}/cancelar", tercera.getId()).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(status().isOk());

        verify(mercadopago, never()).reembolsarPagoParcial(anyString(), any(), any(), any());
        assertThat(transaccion(tercera).getEstado()).isEqualTo(EstadoTransaccion.LIBERADO);
    }

    @Test
    void frPag022_siMercadoPagoRechazaLaDevolucion_laClaseSeCancelaIgual_yVaASoporte() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-4", "43200");
        doThrow(new RuntimeException("saldo insuficiente")).when(mercadopago)
                .reembolsarPagoParcial(anyString(), any(), any(), any());
        Reserva ultima = clases(paqueteId).get(3);

        mockMvc.perform(post("/api/reservas/{id}/cancelar", ultima.getId()).header("Authorization", "Bearer " + s.e().tokenTutor()))
                .andExpect(status().isOk());

        assertThat(reservas.findById(ultima.getId()).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        Transaccion t = transaccion(ultima);
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(t.getLiberarAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin.tickets_soporte WHERE detalle LIKE ?",
                Integer.class, "%" + ultima.getId() + "%")).isEqualTo(1);
    }

    @Test
    void frPag023_elPaqueteEnteroSeCancelaAntesDeLaPrimera_yVuelveTodoDeUnaVez() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-5", "43200");

        mockMvc.perform(post("/api/reservas/paquete/{id}/cancelar", paqueteId).header("Authorization", "Bearer " + s.e().tokenTutor()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/reservas/paquete/{id}/cancelar", paqueteId).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(status().isNoContent());

        verify(mercadopago).reembolsarPago(eq("mp-paq-5"), any());
        verify(mercadopago, never()).reembolsarPagoParcial(anyString(), any(), any(), any());
        assertThat(clases(paqueteId)).allMatch(r -> r.getEstado() == EstadoReserva.CANCELADA);
        assertThat(clases(paqueteId)).allMatch(r -> transaccion(r).getEstado() == EstadoTransaccion.REEMBOLSADO);
    }

    @Test
    void frRes034_conMenosDe24hsALaPrimeraYaNoSeCancelaEntero() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-6", "43200");
        Reserva primera = clases(paqueteId).get(0);
        Instant pronto = Instant.now().plus(Duration.ofHours(10));
        jdbc.update("UPDATE reservas.reservas SET horario = ?, horario_fin = ? WHERE id = ?",
                java.sql.Timestamp.from(pronto), java.sql.Timestamp.from(pronto.plus(Duration.ofHours(1))), primera.getId());

        mockMvc.perform(post("/api/reservas/paquete/{id}/cancelar", paqueteId).header("Authorization", "Bearer " + s.e().tokenAlumno()))
                .andExpect(status().isUnprocessableEntity());
        // Y esa clase, con menos de 24 hs, tampoco se mueve (Tabla de Tiempos).
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", primera.getId())
                        .header("Authorization", "Bearer " + s.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nuevoHorario", a(s.dia(), 17, 0).toString()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes035_unaClaseSeMueveDentroDeLaVigencia_yNoDespues() throws Exception {
        Escenario s = escenario();
        UUID paqueteId = crearPaquete(s);
        pagar(paqueteId, "mp-paq-7", "43200");
        Reserva primera = clases(paqueteId).get(0);
        LocalDate despues = s.dia().plusWeeks(4).plusDays(1); // la vigencia vence 4 semanas después de la primera
        publicarFranja(s.e().tokenTutor(), despues, "10:00", "11:00");

        mockMvc.perform(post("/api/reservas/{id}/reprogramar", primera.getId())
                        .header("Authorization", "Bearer " + s.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nuevoHorario", a(despues, 10, 0).toString()))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", primera.getId())
                        .header("Authorization", "Bearer " + s.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nuevoHorario", a(s.dia(), 17, 0).toString()))))
                .andExpect(status().isOk());
    }

    @Test
    void adrM503_configuracionDelTutor_topeYPiso() throws Exception {
        Adulto e = adultoConTutor();
        configurarPaquete(e.tokenTutor(), true, 10).andExpect(status().isUnprocessableEntity()); // sin tarifa
        tarifa(e.tokenTutor(), 550); // piso de test: 500
        configurarPaquete(e.tokenTutor(), true, 31).andExpect(status().isUnprocessableEntity());
        configurarPaquete(e.tokenTutor(), true, 20).andExpect(status().isUnprocessableEntity()); // 440 < 500
        configurarPaquete(e.tokenTutor(), true, 5).andExpect(status().isOk())
                .andExpect(jsonPath("$.paqueteHabilitado").value(true))
                .andExpect(jsonPath("$.paqueteDescuentoPorcentaje").value(5));
        configurarPaquete(e.tokenAlumno(), true, 5).andExpect(status().isForbidden());
    }
}
