package com.tinku.reputacion;

import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.reputacion.model.Calificacion;
import com.tinku.reputacion.repository.CalificacionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** R-E E-3 (T1-11, FR-REP-001, D-4): los comentarios públicos se ven en el perfil del Tutor. */
class ComentariosTutorIntegracionTest extends IntegracionBase {

    @Autowired ReservaRepository reservaRepo;
    @Autowired SesionAprendizajeRepository sesionRepo;
    @Autowired CalificacionRepository calificacionRepo;

    private int dias = 30;

    private void calificar(Usuario tutor, Usuario alumno, String direccion, int estrellas, String comentario) {
        Reserva r = new Reserva();
        r.setPagador(alumno);
        r.setBeneficiario(alumno);
        r.setTutor(tutor);
        r.setHorario(Instant.now().minus(dias--, ChronoUnit.DAYS));
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(EstadoReserva.CONFIRMADA);
        r = reservaRepo.save(r);
        SesionAprendizaje s = new SesionAprendizaje();
        s.setReservaId(r.getId());
        s.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        s.setDuracionAgendadaSegundos(3600);
        s = sesionRepo.save(s);
        Calificacion c = new Calificacion();
        c.setSesionId(s.getId());
        c.setAutorId(direccion.equals("estudiante_a_tutor") ? alumno.getId() : tutor.getId());
        c.setDireccion(direccion);
        c.setEstrellas((short) estrellas);
        c.setComentario(comentario);
        c.setEditableHasta(Instant.now());
        calificacionRepo.save(c);
    }

    private JsonNode comentarios(Usuario tutor) throws Exception {
        return json(mockMvc.perform(get("/api/tutores/" + tutor.getId() + "/calificaciones")
                        .header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void conPromedioPublico_devuelveSoloComentariosPublicos_sinIdentidad() throws Exception {
        Usuario tutor = tutor(null, null);
        Usuario alumno = adulto(false);
        calificar(tutor, alumno, "estudiante_a_tutor", 5, "Explica muy claro");
        calificar(tutor, alumno, "estudiante_a_tutor", 4, "<script>alert(1)</script>Muy paciente");
        calificar(tutor, alumno, "estudiante_a_tutor", 5, null);
        calificar(tutor, alumno, "estudiante_a_tutor", 5, "  ");
        calificar(tutor, alumno, "estudiante_a_tutor", 4, null);
        calificar(tutor, alumno, "estudiante_a_tutor", 5, null);
        calificar(tutor, alumno, "tutor_a_estudiante", 2, "Comentario oculto del tutor");

        JsonNode lista = comentarios(tutor);
        assertThat(lista).hasSize(2);
        assertThat(lista.toString()).doesNotContain("oculto").doesNotContain(alumno.getId().toString())
                .doesNotContain("autorId").doesNotContain("pagadorId").doesNotContain("nombre").doesNotContain("apellido");
        assertThat(lista.get(0).get("estrellas").asInt()).isBetween(1, 5);
        assertThat(lista.get(0).has("fecha")).isTrue();
    }

    @Test
    void sinPromedioPublico_vacio() throws Exception {
        Usuario tutor = tutor(null, null);
        Usuario alumno = adulto(false);
        for (int i = 0; i < 4; i++) {
            calificar(tutor, alumno, "estudiante_a_tutor", 5, "Muy buena clase " + i);
        }
        assertThat(comentarios(tutor)).isEmpty();
    }
}
