package com.tinku.config;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk REM-B (docs/superpowers/specs/remediacion-pruebas/R-B-api-robustez.md): mensajes en
 * castellano, UTF-8, enums, 404 en vez de 403 para lo ajeno, tarifa, guard de participantes,
 * contacto en la bio, rol de admin en /me y 404 con cuerpo en admin.
 */
class RobustezApiIntegracionTest extends IntegracionBase {

    private String postJson(String url, String token, Object cuerpo, int esperado) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo instanceof String s ? s : objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().is(esperado))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private UUID reserva(Usuario estudiante, Usuario tutor) throws Exception {
        LocalDate dia = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(3);
        String cuerpo = postJson("/api/reservas", token(estudiante), Map.of(
                "tutorId", tutor.getId().toString(),
                "horario", ZonedDateTime.of(dia, LocalTime.of(10, 0), ReservasZonaHoraria.ZONA).toInstant().toString(),
                "duracionMinutos", 60), 201);
        return UUID.fromString(objectMapper.readTree(cuerpo).get("id").asText());
    }

    // ---- B-1 ----

    @Test
    void validacion_mensajesEnCastellano() throws Exception {
        String cuerpo = postJson("/api/reservas", token(adulto(false)), "{}", 400);
        JsonNode campos = objectMapper.readTree(cuerpo).get("campos");
        assertThat(campos.get("tutorId").asText()).isEqualTo("es obligatorio.");
        campos.forEach(v -> assertThat(v.asText()).doesNotContain("must").doesNotContain("Invalid"));
        assertThat(new com.tinku.reputacion.service.CalificacionDefinitivaException().getMessage()).contains("venció");
    }

    // ---- B-2 ----

    @Test
    void admin403_enUtf8() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/admin/yo").header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isForbidden()).andReturn();
        assertThat(res.getResponse().getContentType()).containsIgnoringCase("charset=UTF-8");
        assertThat(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("administración");
    }

    // ---- B-3 ----

    @Test
    void enums_sinDistinguirMayusculas() throws Exception {
        Usuario denunciado = tutor(null, null);
        for (String motivo : new String[]{"ACOSO", "acoso", "Fraude"}) {
            postJson("/api/denuncias", token(adulto(false)),
                    Map.of("denunciadoId", denunciado.getId().toString(), "motivo", motivo), 201);
        }
        Usuario admin = adulto(false);
        hacerAdmin(admin, "SOPORTE_FINANCIERO");
        String t = token(admin);
        mockMvc.perform(get("/api/admin/financiero/reembolsos-adicional").param("estado", "fallido")
                .header("Authorization", bearer(t))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/financiero/reembolsos-adicional").param("estado", "FOO")
                        .header("Authorization", bearer(t)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Valores aceptados")));
    }

    // ---- B-4 ----

    @Test
    void reservaAjena_404_enVerCancelarYReprogramar() throws Exception {
        Usuario tutor = tutor(LocalTime.of(8, 0), LocalTime.of(11, 0));
        UUID id = reserva(adulto(false), tutor);
        String otro = token(adulto(false));
        mockMvc.perform(get("/api/reservas/" + id).header("Authorization", bearer(otro)))
                .andExpect(status().isNotFound());
        postJson("/api/reservas/" + id + "/cancelar", otro, "{}", 404);
        postJson("/api/reservas/" + id + "/reprogramar", otro,
                Map.of("nuevoHorario", ZonedDateTime.of(LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(4),
                        LocalTime.of(9, 0), ReservasZonaHoraria.ZONA).toInstant().toString()), 404);
        postJson("/api/pagos/preferencia", otro, Map.of("reservaId", id.toString()), 404);
    }

    // ---- B-6 / B-7 ----

    @Test
    void tarifa_redondeaYActualizaFecha() throws Exception {
        String t = token(tutor(null, null));
        JsonNode antes = json(mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andReturn());
        Thread.sleep(5);
        mockMvc.perform(put("/api/pagos/tarifa").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioHora\":16140.005}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precioHora").value(16140.01));
        JsonNode despues = json(mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", bearer(t)))
                .andExpect(status().isOk()).andReturn());
        assertThat(despues.get("precioHora").decimalValue()).isEqualByComparingTo("16140.01");
        assertThat(java.time.Instant.parse(despues.get("updatedAt").asText()))
                .isAfter(java.time.Instant.parse(antes.get("updatedAt").asText()));
    }

    @Test
    void tarifa_soloTutor() throws Exception {
        Usuario ar = adulto(true);
        for (Usuario u : new Usuario[]{menor(ar), adulto(false)}) {
            mockMvc.perform(get("/api/pagos/tarifa").header("Authorization", bearer(token(u))))
                    .andExpect(status().isForbidden());
        }
    }

    // ---- B-8 ----

    @Test
    void soloParticipante_mensajeSegunLaAccion() throws Exception {
        Usuario tutor = tutor(LocalTime.of(8, 0), LocalTime.of(11, 0));
        UUID id = reserva(adulto(false), tutor);
        mockMvc.perform(get("/api/sesiones/por-reserva/" + id).header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Solo los participantes de la sesión pueden verla."));
    }

    // ---- B-9 ----

    @Test
    void bio_ocultaDatosDeContacto() throws Exception {
        String t = token(tutor(null, null));
        mockMvc.perform(put("/api/tutores/me/perfil").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Tengo 15 años de experiencia. Mi cel es 11 5555-1234 o escribime a profe@mail.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value(
                        "Tengo 15 años de experiencia. Mi cel es [contacto oculto] o escribime a [contacto oculto]"))
                .andExpect(jsonPath("$.avisos[0]").value(containsString("Quitamos datos de contacto")));
        mockMvc.perform(put("/api/tutores/me/perfil").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"Profe de matemática hace 15 años.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Profe de matemática hace 15 años."))
                .andExpect(jsonPath("$.avisos").isEmpty());
    }

    // ---- B-10 ----

    @Test
    void me_informaElRolDeAdmin() throws Exception {
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAdmin").doesNotExist());
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");
        mockMvc.perform(get("/api/usuarios/me").header("Authorization", bearer(token(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolAdmin").value("moderacion_seguridad"));
    }

    // ---- B-11 ----

    @Test
    void admin404_conCuerpo() throws Exception {
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");
        String t = token(admin);
        UUID x = UUID.randomUUID();
        mockMvc.perform(post("/api/admin/moderacion/credenciales/" + x + "/resolver").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APROBAR\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
        mockMvc.perform(get("/api/admin/moderacion/alertas/" + x + "/clip").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
        mockMvc.perform(delete("/api/admin/moderacion/tutores/" + x + "/bio").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").exists());
    }
}
