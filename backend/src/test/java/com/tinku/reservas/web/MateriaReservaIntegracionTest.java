package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-M3-07 §4bis: la materia de la clase se elige al reservar entre las que dicta el Tutor y queda
 * en la Reserva; las de contenido anatómico activan el modo salud del kill-switch.
 */
class MateriaReservaIntegracionTest extends IntegracionBase {

    @Autowired ReservaRepository reservaRepo;

    private UUID trayecto(String carrera, String materia) {
        return jdbc.queryForObject("SELECT id FROM matching.trayectos WHERE anio_o_carrera = ? AND materia = ?",
                UUID.class, carrera, materia);
    }

    /** Tutor con un tema de Anatomía (Medicina) y franja libre pasado mañana de 10 a 12. */
    private Usuario tutorDeAnatomia() {
        Usuario tutor = tutor(null, null);
        UUID tema = jdbc.queryForObject("SELECT id FROM matching.temas WHERE trayecto_id = ? LIMIT 1",
                UUID.class, trayecto("Medicina", "Anatomía"));
        jdbc.update("""
                INSERT INTO matching.perfiles_tutor_matching (tutor_id, tema_ids) VALUES (?, ARRAY[?]::uuid[])
                ON CONFLICT (tutor_id) DO UPDATE SET tema_ids = EXCLUDED.tema_ids""", tutor.getId(), tema);
        jdbc.update("""
                INSERT INTO reservas.franjas_disponibilidad (tutor_id, fecha_especifica, hora_inicio, hora_fin)
                VALUES (?, ?, ?, ?)""", tutor.getId(), dia(), LocalTime.of(10, 0), LocalTime.of(12, 0));
        return tutor;
    }

    private LocalDate dia() {
        return LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
    }

    private ResultActions reservar(Usuario tutor, UUID trayectoId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of(
                "tutorId", tutor.getId().toString(),
                "horario", ZonedDateTime.of(dia(), LocalTime.of(10, 0), ReservasZonaHoraria.ZONA).toInstant().toString(),
                "duracionMinutos", 60));
        if (trayectoId != null) body.put("trayectoId", trayectoId.toString());
        return mockMvc.perform(post("/api/reservas")
                .header("Authorization", bearer(token(adulto(false))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void materiasDelTutor_marcaLaDeContenidoAnatomico() throws Exception {
        Usuario tutor = tutorDeAnatomia();
        mockMvc.perform(get("/api/tutores/" + tutor.getId() + "/materias")
                        .header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].materia").value("Anatomía"))
                .andExpect(jsonPath("$[0].carrera").value("Medicina"))
                .andExpect(jsonPath("$[0].contenidoAnatomico").value(true));
    }

    @Test
    void reservarConMateriaDelTutor_laGuarda() throws Exception {
        Usuario tutor = tutorDeAnatomia();
        UUID anatomia = trayecto("Medicina", "Anatomía");
        MvcResult res = reservar(tutor, anatomia).andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(json(res).get("id").asText());
        assertThat(reservaRepo.findById(id).orElseThrow().getTrayectoId()).isEqualTo(anatomia);
    }

    @Test
    void reservarConMateriaQueNoDicta_422() throws Exception {
        Usuario tutor = tutorDeAnatomia();
        reservar(tutor, trayecto("Odontología", "Bioquímica General"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservarSinMateria_sigueFuncionando() throws Exception {
        reservar(tutorDeAnatomia(), null).andExpect(status().isCreated());
    }
}
