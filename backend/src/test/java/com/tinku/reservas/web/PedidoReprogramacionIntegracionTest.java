package com.tinku.reservas.web;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.PedidoReprogramacionService;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Enmienda v2.5 — Spec_M4 US-13: pedido de reprogramación del Tutor (FR-RES-029..031), con la
 * devolución de M5 cuando termina en cancelación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class PedidoReprogramacionIntegracionTest extends FlujosReservaBase {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired Scheduler scheduler;
    @Autowired PedidoReprogramacionService pedidos;
    @Autowired ReservaRepository reservas;
    @Autowired TransaccionRepository transacciones;

    @Override
    int baseDnis() {
        return 42_000_000;
    }

    private ResultActions pedir(String token, UUID reservaId, Instant horario, String motivo) throws Exception {
        return mockMvc.perform(post("/api/reservas/{id}/pedido-reprogramacion", reservaId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(motivo == null
                        ? Map.of("nuevoHorario", horario.toString())
                        : Map.of("nuevoHorario", horario.toString(), "motivo", motivo))));
    }

    private ResultActions responder(String token, UUID reservaId, String accion) throws Exception {
        return mockMvc.perform(post("/api/reservas/{id}/pedido-reprogramacion/" + accion, reservaId)
                .header("Authorization", "Bearer " + token));
    }

    /** Escrow simulado (bypass) para ver que M5 devuelve cuando la clase se cancela. */
    private Transaccion escrow(UUID reservaId) {
        Transaccion t = new Transaccion();
        t.setReservaId(reservaId);
        t.setMpPaymentId("bypass-" + reservaId);
        t.setMontoBruto(new BigDecimal("15000"));
        t.setComisionPlataforma(new BigDecimal("4050.00"));
        t.setEnBypass(true);
        return transacciones.save(t);
    }

    private record Clase(Adulto e, UUID reservaId, LocalDate otroDia) {
    }

    private Clase claseConfirmada() throws Exception {
        Adulto e = adultoConTutor();
        UUID reservaId = reservar(e.tokenAlumno(), e.tutorId(), null, a(e.fecha(), 15, 0), 60);
        confirmarPago(reservaId);
        escrow(reservaId);
        LocalDate otroDia = enDias(3);
        publicarFranja(e.tokenTutor(), otroDia, "18:00", "20:00");
        return new Clase(e, reservaId, otroDia);
    }

    @Test
    void us13_elTutorPropone_elAlumnoAcepta_yLaClasePasaAlHorarioNuevo() throws Exception {
        Clase c = claseConfirmada();
        Instant propuesto = a(c.otroDia(), 18, 30);

        pedir(c.e().tokenTutor(), c.reservaId(), propuesto, "Tengo un turno médico, llamame al 1144445555")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente"))
                .andExpect(jsonPath("$.puedoRetirar").value(true));
        assertThat(avisosDe(c.e().alumnoId())).contains(TipoNotificacion.REPROGRAMACION_PEDIDA);
        UUID pedidoId = UUID.fromString(objectMapper.readTree(mockMvc.perform(get("/api/reservas/{id}/pedido-reprogramacion", c.reservaId())
                        .header("Authorization", "Bearer " + c.e().tokenAlumno()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedoResponder").value(true))
                .andExpect(jsonPath("$.motivo").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("1144445555"))))
                .andReturn().getResponse().getContentAsString()).get("id").asText());
        // FR-RES-031: vence a T-60 de la clase original.
        assertThat(scheduler.getTrigger(pedidos.triggerVencimiento(pedidoId)).getStartTime().toInstant())
                .isEqualTo(a(c.e().fecha(), 15, 0).minus(Duration.ofHours(1)));

        responder(c.e().tokenAlumno(), c.reservaId(), "aceptar").andExpect(status().isNoContent());

        var reserva = reservas.findById(c.reservaId()).orElseThrow();
        assertThat(reserva.getHorario()).isEqualTo(propuesto);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(avisosDe(c.e().tutorId())).contains(TipoNotificacion.REPROGRAMACION_ACEPTADA);
        assertThat(scheduler.checkExists(pedidos.triggerVencimiento(pedidoId))).isFalse();
        mockMvc.perform(get("/api/reservas/{id}/pedido-reprogramacion", c.reservaId())
                        .header("Authorization", "Bearer " + c.e().tokenAlumno()))
                .andExpect(status().isNoContent());
    }

    @Test
    void us13_elAlumnoPrefiereCancelar_yRecibeLaDevolucionCompleta() throws Exception {
        Clase c = claseConfirmada();
        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 18, 0), null).andExpect(status().isCreated());

        responder(c.e().tokenAlumno(), c.reservaId(), "rechazar").andExpect(status().isNoContent());

        assertThat(reservas.findById(c.reservaId()).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(transacciones.findByReservaId(c.reservaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(avisosDe(c.e().tutorId())).contains(TipoNotificacion.REPROGRAMACION_RECHAZADA);
        // Quien eligió cancelar no recibe un "te cancelaron".
        assertThat(avisosDe(c.e().alumnoId())).doesNotContain(TipoNotificacion.CLASE_CANCELADA);
    }

    @Test
    void frRes031_sinRespuestaVence_yLaClaseSeCancelaConDevolucion() throws Exception {
        Clase c = claseConfirmada();
        UUID pedidoId = UUID.fromString(objectMapper.readTree(pedir(c.e().tokenTutor(), c.reservaId(),
                a(c.otroDia(), 19, 0), null).andReturn().getResponse().getContentAsString()).get("id").asText());

        pedidos.vencer(pedidoId);
        pedidos.vencer(pedidoId); // idempotente

        assertThat(reservas.findById(c.reservaId()).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(transacciones.findByReservaId(c.reservaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(avisosDe(c.e().alumnoId())).contains(TipoNotificacion.CLASE_CANCELADA);
        assertThat(avisosDe(c.e().tutorId())).contains(TipoNotificacion.REPROGRAMACION_RECHAZADA);
        responder(c.e().tokenAlumno(), c.reservaId(), "aceptar").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes029_reglas_soloElTutor_unoPorVez_franjaYLibre() throws Exception {
        Clase c = claseConfirmada();
        pedir(c.e().tokenAlumno(), c.reservaId(), a(c.otroDia(), 18, 0), null).andExpect(status().isForbidden());
        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 21, 0), null).andExpect(status().isUnprocessableEntity());

        // Otro alumno ya tiene 18:00-19:00 ese día: el Tutor no lo puede proponer.
        String tokenOtro = registrarAdulto(dniUnico(), "Otro", true, false);
        UUID otra = reservar(tokenOtro, c.e().tutorId(), null, a(c.otroDia(), 18, 0), 60);
        confirmarPago(otra);
        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 18, 30), null).andExpect(status().isUnprocessableEntity());

        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 19, 0), null).andExpect(status().isCreated());
        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 19, 0), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes029_conMenosDeUnaHoraNoSePuedePedir() throws Exception {
        Clase c = claseConfirmada();
        Instant pronto = Instant.now().plus(Duration.ofMinutes(50));
        jdbc.update("UPDATE reservas.reservas SET horario = ?, horario_fin = ? WHERE id = ?",
                java.sql.Timestamp.from(pronto), java.sql.Timestamp.from(pronto.plus(Duration.ofHours(1))), c.reservaId());

        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 18, 0), null).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void us13_conUnMenor_respondeElAr_yElMenorNiSeEntera() throws Exception {
        ConMenor e = arConMenor();
        UUID reservaId = reservar(e.tokenAr(), e.tutorId(), e.menorId(), a(e.fecha(), 15, 0), 30);
        confirmarPago(reservaId);
        LocalDate otroDia = enDias(3);
        publicarFranja(e.tokenTutor(), otroDia, "18:00", "19:00");

        pedir(e.tokenTutor(), reservaId, a(otroDia, 18, 0), null).andExpect(status().isCreated());

        assertThat(avisosDe(e.arId())).contains(TipoNotificacion.REPROGRAMACION_PEDIDA);
        assertThat(avisosDe(e.menorId())).doesNotContain(TipoNotificacion.REPROGRAMACION_PEDIDA);
        mockMvc.perform(get("/api/reservas/{id}/pedido-reprogramacion", reservaId)
                        .header("Authorization", "Bearer " + e.tokenMenor()))
                .andExpect(status().isNotFound());
        responder(e.tokenMenor(), reservaId, "aceptar").andExpect(status().isForbidden());
        responder(e.tokenAr(), reservaId, "aceptar").andExpect(status().isNoContent());
        assertThat(reservas.findById(reservaId).orElseThrow().getHorario()).isEqualTo(a(otroDia, 18, 0));
    }

    @Test
    void elTutorRetira_oElAlumnoMueveLaClase_yElPedidoSeCierra() throws Exception {
        Clase c = claseConfirmada();
        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 18, 0), null).andExpect(status().isCreated());
        mockMvc.perform(delete("/api/reservas/{id}/pedido-reprogramacion", c.reservaId())
                        .header("Authorization", "Bearer " + c.e().tokenTutor()))
                .andExpect(status().isNoContent());
        responder(c.e().tokenAlumno(), c.reservaId(), "aceptar").andExpect(status().isUnprocessableEntity());

        pedir(c.e().tokenTutor(), c.reservaId(), a(c.otroDia(), 18, 0), null).andExpect(status().isCreated());
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", c.reservaId())
                        .header("Authorization", "Bearer " + c.e().tokenAlumno())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nuevoHorario", a(c.otroDia(), 19, 0).toString()))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/reservas/{id}/pedido-reprogramacion", c.reservaId())
                        .header("Authorization", "Bearer " + c.e().tokenTutor()))
                .andExpect(status().isNoContent());
    }
}
