package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk REM-D (docs/superpowers/specs/remediacion-pruebas/R-D-agenda-franjas.md): baja de franjas
 * (T2-H1), franjas contiguas y duraciones largas (T1-M2, T1-M3), validaciones de publicación (T2-H2,
 * T2-H3), disponibilidad real del perfil (T2-H10) y el mensaje de horario pasado (T1-12).
 */
class AgendaFranjasIntegracionTest extends IntegracionBase {

    @Autowired ReservaService reservaService;
    @Autowired ReservaRepository reservaRepo;

    private LocalDate enDias(int dias) {
        return LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(dias);
    }

    private Instant a(LocalDate dia, int hora, int minuto) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, minuto), ReservasZonaHoraria.ZONA).toInstant();
    }

    private UUID franjaPuntual(Usuario tutor, LocalDate fecha, int desde, int hasta) {
        return jdbc.queryForObject("""
                INSERT INTO reservas.franjas_disponibilidad (tutor_id, fecha_especifica, hora_inicio, hora_fin)
                VALUES (?, ?, ?, ?) RETURNING id""", UUID.class,
                tutor.getId(), fecha, LocalTime.of(desde, 0), LocalTime.of(hasta, 0));
    }

    private ResultActions reservar(String token, Usuario tutor, Instant horario, int minutos) throws Exception {
        return mockMvc.perform(post("/api/reservas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tutorId", tutor.getId().toString(),
                        "horario", horario.toString(),
                        "duracionMinutos", minutos))));
    }

    private List<String> inicios(Usuario tutor, String token, LocalDate fecha, int minutos) throws Exception {
        MvcResult res = mockMvc.perform(get("/api/tutores/" + tutor.getId() + "/horarios")
                        .param("fecha", fecha.toString())
                        .param("duracionMinutos", String.valueOf(minutos))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        List<String> inicios = new ArrayList<>();
        for (JsonNode s : json(res)) {
            inicios.add(Instant.parse(s.get("startTime").asText()).atZone(ReservasZonaHoraria.ZONA)
                    .toLocalTime().toString());
        }
        return inicios;
    }

    private ResultActions publicar(String token, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/tutores/franjas")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    // ---- D-1 baja de franjas ----

    @Test
    void quitar_franjaPropia_200_yDejaDeReservarse() throws Exception {
        Usuario tutor = tutor(null, null);
        LocalDate dia = enDias(2);
        UUID franja = franjaPuntual(tutor, dia, 10, 12);
        String tokenTutor = token(tutor);

        mockMvc.perform(delete("/api/tutores/franjas/" + franja).header("Authorization", bearer(tokenTutor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(franja.toString()))
                .andExpect(jsonPath("$.reservasFuturasEnLaFranja").value(0));

        assertThat(inicios(tutor, tokenTutor, dia, 60)).isEmpty();
        reservar(token(adulto(false)), tutor, a(dia, 10, 0), 60).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void quitar_franjaAjena_404() throws Exception {
        Usuario duenio = tutor(null, null);
        UUID franja = franjaPuntual(duenio, enDias(2), 10, 12);
        mockMvc.perform(delete("/api/tutores/franjas/" + franja)
                        .header("Authorization", bearer(token(tutor(null, null)))))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT activa FROM reservas.franjas_disponibilidad WHERE id = ?",
                Boolean.class, franja)).isTrue();
    }

    @Test
    void quitar_noDejaSinEfectoReservasExistentes() throws Exception {
        Usuario tutor = tutor(null, null);
        LocalDate dia = enDias(3);
        UUID franja = franjaPuntual(tutor, dia, 10, 12);
        MvcResult res = reservar(token(adulto(false)), tutor, a(dia, 10, 0), 60)
                .andExpect(status().isCreated()).andReturn();
        UUID reservaId = UUID.fromString(json(res).get("id").asText());
        reservaService.confirmarPagoSimulado(reservaId);

        mockMvc.perform(delete("/api/tutores/franjas/" + franja).header("Authorization", bearer(token(tutor))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservasFuturasEnLaFranja").value(1));

        assertThat(reservaRepo.findById(reservaId).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void quitar_doble_404() throws Exception {
        Usuario tutor = tutor(null, null);
        UUID franja = franjaPuntual(tutor, enDias(2), 10, 12);
        String t = token(tutor);
        mockMvc.perform(delete("/api/tutores/franjas/" + franja).header("Authorization", bearer(t)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/tutores/franjas/" + franja).header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
    }

    @Test
    void quitar_comoEstudiante_403() throws Exception {
        UUID franja = franjaPuntual(tutor(null, null), enDias(2), 10, 12);
        mockMvc.perform(delete("/api/tutores/franjas/" + franja)
                        .header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isForbidden());
    }

    // ---- D-2 franjas contiguas ----

    @Test
    void horarios_franjasContiguasNoDanLaVueltaAMedianoche() throws Exception {
        Usuario tutor = tutor(null, null);
        LocalDate dia = enDias(2);
        franjaPuntual(tutor, dia, 21, 22);
        franjaPuntual(tutor, dia, 22, 23);
        String t = token(tutor);

        assertThat(inicios(tutor, t, dia, 180)).isEmpty();
        assertThat(inicios(tutor, t, dia, 120)).containsExactly("21:00");
        assertThat(inicios(tutor, t, dia, 60)).containsExactly("21:00", "21:30", "22:00");
    }

    @Test
    void horarios_15FranjasDeUnaHora_ofrecen180Minutos() throws Exception {
        Usuario tutor = tutor(LocalTime.of(8, 0), LocalTime.of(9, 0));
        for (int h = 9; h < 23; h++) {
            for (short d = 0; d <= 6; d++) {
                jdbc.update("""
                        INSERT INTO reservas.franjas_disponibilidad (tutor_id, dia_semana, hora_inicio, hora_fin)
                        VALUES (?, ?, ?, ?)""", tutor.getId(), d, LocalTime.of(h, 0), LocalTime.of(h + 1, 0));
            }
        }
        LocalDate dia = enDias(2);
        List<String> esperados = new ArrayList<>();
        for (LocalTime c = LocalTime.of(8, 0); !c.isAfter(LocalTime.of(20, 0)); c = c.plusMinutes(30)) {
            esperados.add(c.toString());
        }
        assertThat(inicios(tutor, token(tutor), dia, 180)).containsExactlyElementsOf(esperados);

        String tokenEst = token(adulto(false));
        reservar(tokenEst, tutor, a(dia, 10, 0), 180).andExpect(status().isCreated());
        reservar(tokenEst, tutor, a(dia, 21, 30), 180).andExpect(status().isUnprocessableEntity());
        reservar(tokenEst, tutor, a(dia, 20, 0), 180).andExpect(status().isCreated());
    }

    @Test
    void horarios_franjasNoContiguas_noSeUnen() throws Exception {
        Usuario tutor = tutor(null, null);
        LocalDate dia = enDias(2);
        franjaPuntual(tutor, dia, 8, 9);
        franjaPuntual(tutor, dia, 10, 11);
        assertThat(inicios(tutor, token(tutor), dia, 120)).isEmpty();
        reservar(token(adulto(false)), tutor, a(dia, 8, 0), 120).andExpect(status().isUnprocessableEntity());
    }

    // ---- D-3 validaciones al publicar ----

    @Test
    void publicar_diaSemanaFueraDeRango_400() throws Exception {
        String t = token(tutor(null, null));
        for (int dia : new int[]{9, -1}) {
            publicar(t, Map.of("diaSemana", dia, "horaInicio", "10:00", "horaFin", "11:00"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void publicar_fechaPasada_422_yHoyOMañana_201() throws Exception {
        String t = token(tutor(null, null));
        publicar(t, Map.of("fechaEspecifica", "2020-01-01", "horaInicio", "10:00", "horaFin", "11:00"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("La fecha de la franja ya pasó."));
        publicar(t, Map.of("fechaEspecifica", enDias(0).toString(), "horaInicio", "00:00", "horaFin", "00:30"))
                .andExpect(status().isCreated());
        publicar(t, Map.of("fechaEspecifica", enDias(1).toString(), "horaInicio", "10:00", "horaFin", "11:00"))
                .andExpect(status().isCreated());
    }

    // ---- D-7 mensaje de horario pasado ----

    @Test
    void reservar_horarioPasado_422_yaPaso() throws Exception {
        Usuario tutor = tutor(LocalTime.of(0, 0), LocalTime.of(3, 0));
        String t = token(adulto(false));
        reservar(t, tutor, Instant.now().minusSeconds(3600), 60)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Ese horario ya pasó."));
        reservar(t, tutor, Instant.now().plusSeconds(20 * 60), 60)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("menos de 21 minutos")));
    }

    // ---- D-4 disponibilidad del perfil público ----

    @Test
    void disponibilidad_rangosLibresUnidos_yReservasLosParten() throws Exception {
        Usuario tutor = tutor(null, null);
        LocalDate dia = enDias(2);
        for (int h = 8; h < 23; h++) {
            franjaPuntual(tutor, dia, h, h + 1);
        }
        MvcResult res = reservar(token(adulto(false)), tutor, a(dia, 14, 0), 60)
                .andExpect(status().isCreated()).andReturn();
        reservaService.confirmarPagoSimulado(UUID.fromString(json(res).get("id").asText()));

        JsonNode dias = json(mockMvc.perform(get("/api/tutores/" + tutor.getId() + "/disponibilidad")
                        .header("Authorization", bearer(token(tutor))))
                .andExpect(status().isOk()).andReturn());

        assertThat(dias).hasSize(1);
        assertThat(dias.get(0).get("fecha").asText()).isEqualTo(dia.toString());
        JsonNode rangos = dias.get(0).get("rangos");
        assertThat(rangos).hasSize(2);
        assertThat(rangos.get(0).get("desde").asText()).isEqualTo("08:00");
        assertThat(rangos.get(0).get("hasta").asText()).isEqualTo("14:00");
        assertThat(rangos.get(1).get("desde").asText()).isEqualTo("15:00");
        assertThat(rangos.get(1).get("hasta").asText()).isEqualTo("23:00");
    }

    @Test
    void disponibilidad_deHoy_empiezaDespuesDeLaVentanaMinima() throws Exception {
        Usuario tutor = tutor(LocalTime.of(0, 0), LocalTime.of(23, 30));
        LocalTime limite = ZonedDateTime.now(ReservasZonaHoraria.ZONA).plusMinutes(30).toLocalTime();
        JsonNode dias = json(mockMvc.perform(get("/api/tutores/" + tutor.getId() + "/disponibilidad")
                        .param("dias", "1")
                        .header("Authorization", bearer(token(tutor))))
                .andExpect(status().isOk()).andReturn());
        for (JsonNode d : dias) {
            assertThat(d.get("fecha").asText()).isEqualTo(enDias(0).toString());
            for (JsonNode r : d.get("rangos")) {
                assertThat(LocalTime.parse(r.get("desde").asText())).isAfterOrEqualTo(limite.withSecond(0).withNano(0));
            }
        }
    }
}
