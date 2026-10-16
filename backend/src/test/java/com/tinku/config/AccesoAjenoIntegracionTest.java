package com.tinku.config;

import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Auditoría de acceso (IDOR): con el id de un recurso ajeno, un usuario autenticado que no es
 * parte recibe 403 o 404 y no cambia nada. Cubre los endpoints que no tenían una prueba "con otro
 * usuario" en su propia clase (reservas: pedido previo, nota, pedido de reprogramación; pagos:
 * confirmar-retorno; aula: kill-switch, clasificador, confirmación; menores y autorizaciones de
 * otro Adulto Responsable; búsquedas guardadas, calificaciones y descargos ajenos).
 */
class AccesoAjenoIntegracionTest extends IntegracionBase {

    @Autowired ReservaRepository reservaRepo;
    @Autowired SesionAprendizajeRepository sesionRepo;

    private Reserva reservaConfirmada(Usuario pagador, Usuario beneficiario, Usuario tutor) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(beneficiario);
        r.setTutor(tutor);
        r.definirHorario(Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.HOURS), 60);
        r.setPrecio(new BigDecimal("15000"));
        r.setEstado(EstadoReserva.CONFIRMADA);
        return reservaRepo.saveAndFlush(r);
    }

    private SesionAprendizaje sesionEnCurso(Reserva reserva) {
        SesionAprendizaje s = new SesionAprendizaje();
        s.setReservaId(reserva.getId());
        s.setLivekitRoomId("sala-" + reserva.getId());
        s.setEstado(SesionAprendizaje.ESTADO_EN_CURSO);
        return sesionRepo.saveAndFlush(s);
    }

    private void rechazado(String token, MockHttpServletRequestBuilder pedido) throws Exception {
        int estado = mockMvc.perform(pedido.header("Authorization", bearer(token)))
                .andReturn().getResponse().getStatus();
        assertThat(estado).as("%s %s", pedido.buildRequest(null).getMethod(),
                pedido.buildRequest(null).getRequestURI()).isIn(403, 404);
    }

    private MockHttpServletRequestBuilder conJson(MockHttpServletRequestBuilder b, Object cuerpo) throws Exception {
        return b.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo));
    }

    @Test
    void reservaYSesionAjenas_noSeVenNiSeTocan() throws Exception {
        Usuario ana = adulto(false);
        Usuario juan = tutor(null, null);
        Reserva reserva = reservaConfirmada(ana, ana, juan);
        SesionAprendizaje sesion = sesionEnCurso(reserva);
        String intruso = token(adulto(true));
        UUID r = reserva.getId();
        UUID s = sesion.getId();
        Instant otroHorario = reserva.getHorario().plus(Duration.ofDays(1));

        for (MockHttpServletRequestBuilder pedido : List.of(
                get("/api/reservas/{id}", r),
                post("/api/reservas/{id}/cancelar", r),
                conJson(post("/api/reservas/{id}/reprogramar", r), Map.of("nuevoHorario", otroHorario.toString())),
                get("/api/reservas/{id}/pedido", r),
                conJson(put("/api/reservas/{id}/pedido", r), Map.of("texto", "hola")),
                get("/api/reservas/{id}/nota", r),
                conJson(put("/api/reservas/{id}/nota", r), Map.of("texto", "hola")),
                get("/api/reservas/{id}/pedido-reprogramacion", r),
                conJson(post("/api/reservas/{id}/pedido-reprogramacion", r),
                        Map.of("nuevoHorario", otroHorario.toString(), "motivo", "x")),
                post("/api/reservas/{id}/pedido-reprogramacion/aceptar", r),
                post("/api/reservas/{id}/pedido-reprogramacion/rechazar", r),
                delete("/api/reservas/{id}/pedido-reprogramacion", r),
                conJson(post("/api/pagos/preferencia"), Map.of("reservaId", r.toString())),
                conJson(post("/api/pagos/confirmar-retorno"), Map.of("reservaId", r.toString(), "paymentId", "123")),
                get("/api/sesiones/por-reserva/{id}", r),
                post("/api/sesiones/{id}/token", s),
                post("/api/sesiones/{id}/finalizar", s),
                conJson(post("/api/sesiones/{id}/killswitch", s), Map.of("detectadoId", juan.getId().toString())),
                get("/api/sesiones/{id}/killswitch", s),
                conJson(post("/api/sesiones/{id}/clasificador", s), Map.of("evento", "CAIDA")),
                conJson(post("/api/sesiones/{id}/killswitch/confirmacion", s), Map.of("vio", true)),
                get("/api/sesiones/{id}/resumen", s))) {
            rechazado(intruso, pedido);
        }

        assertThat(reservaRepo.findById(r).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reservaRepo.findById(r).orElseThrow().getHorario()).isEqualTo(reserva.getHorario());
        assertThat(sesionRepo.findById(s).orElseThrow().getEstado()).isEqualTo(SesionAprendizaje.ESTADO_EN_CURSO);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas.pedidos_previos WHERE reserva_id = ?",
                Long.class, r)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservas.notas_clase WHERE reserva_id = ?",
                Long.class, r)).isZero();
        assertThat(usuarioRepo.findById(juan.getId()).orElseThrow().isActivoParaMatching()).isTrue();
    }

    @Test
    void menorDeOtroAdultoResponsable_noSeOpera() throws Exception {
        Usuario madre = adulto(true);
        Usuario hijo = menor(madre);
        Usuario juan = tutor(null, null);
        String otraAr = token(adulto(true));

        for (MockHttpServletRequestBuilder pedido : List.of(
                get("/api/autorizaciones").param("menorId", hijo.getId().toString()),
                conJson(post("/api/autorizaciones"), Map.of("menorId", hijo.getId().toString(),
                        "tutorId", juan.getId().toString())),
                delete("/api/usuarios/menores/{id}", hijo.getId()).param("confirmar", "true"),
                conJson(post("/api/reservas"), Map.of("tutorId", juan.getId().toString(),
                        "beneficiarioId", hijo.getId().toString(),
                        "horario", Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.HOURS).toString(),
                        "duracionMinutos", 60)))) {
            rechazado(otraAr, pedido);
        }
        assertThat(usuarioRepo.findById(hijo.getId()).orElseThrow().getEstadoCuenta().name()).isEqualTo("ACTIVA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identidad.autorizaciones_tutor WHERE menor_id = ?",
                Long.class, hijo.getId())).isZero();
    }

    @Test
    void busquedaCalificacionYDescargosAjenos_noSeTocan() throws Exception {
        Usuario ana = adulto(false);
        Usuario juan = tutor(null, null);
        Reserva reserva = reservaConfirmada(ana, ana, juan);
        SesionAprendizaje sesion = sesionEnCurso(reserva);
        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        sesionRepo.saveAndFlush(sesion);
        String tokenAna = token(ana);

        // Recursos de Ana: una búsqueda guardada, su calificación y una denuncia que recibió Juan.
        String guardada = json(mockMvc.perform(conJson(post("/api/busquedas/guardadas"),
                Map.of("texto_busqueda", "álgebra lineal")).header("Authorization", bearer(tokenAna))).andReturn())
                .get("id").asText();
        String calificacion = json(mockMvc.perform(conJson(post("/api/sesiones/{id}/calificacion", sesion.getId()),
                Map.of("estrellas", 5, "comentario", "Muy bien")).header("Authorization", bearer(tokenAna)))
                .andReturn()).get("id").asText();
        String denuncia = json(mockMvc.perform(conJson(post("/api/denuncias"),
                Map.of("denunciadoId", juan.getId().toString(), "motivo", "acoso"))
                .header("Authorization", bearer(tokenAna))).andReturn()).get("id").asText();

        String intruso = token(adulto(false));
        for (MockHttpServletRequestBuilder pedido : List.of(
                post("/api/busquedas/guardadas/{id}/ejecutar", guardada),
                delete("/api/busquedas/guardadas/{id}", guardada),
                conJson(patch("/api/calificaciones/{id}", calificacion), Map.of("estrellas", 1)),
                delete("/api/calificaciones/{id}", calificacion),
                conJson(post("/api/denuncias/{id}/descargo", denuncia), Map.of("descargo", "no fui")))) {
            rechazado(intruso, pedido);
        }
        // Ni siquiera la denunciante puede escribir el descargo del denunciado.
        rechazado(tokenAna, conJson(post("/api/denuncias/{id}/descargo", denuncia), Map.of("descargo", "no fui")));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM matching.busquedas_guardadas WHERE id = ?::uuid",
                Long.class, guardada)).isOne();
        assertThat(jdbc.queryForObject("SELECT estrellas FROM reputacion.calificaciones WHERE id = ?::uuid",
                Integer.class, calificacion)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT descargo_texto FROM seguridad.denuncias WHERE id = ?::uuid",
                String.class, denuncia)).isNull();
    }
}
