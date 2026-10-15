package com.tinku.admin.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk REM-F (docs/superpowers/specs/remediacion-pruebas/R-F-admin-moderacion.md): conflicto de
 * interés (T4-H2/H3), evidencia y denunciado en la cola (T4-H7), historial de descargos (T4-H8),
 * categoría del CAP al re-rechazar (T4-H11) y rol antes que validación (T4-H12).
 */
class AdminModeracionRemIntegracionTest extends IntegracionBase {

    private ResultActions postJson(String url, String token, Object cuerpo) throws Exception {
        return mockMvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo instanceof String s ? s : objectMapper.writeValueAsString(cuerpo)));
    }

    private UUID denunciar(Usuario denunciante, Usuario denunciado, String evidencia) throws Exception {
        String r = postJson("/api/denuncias", token(denunciante), Map.of(
                "denunciadoId", denunciado.getId().toString(), "motivo", "acoso", "evidenciaUrl", evidencia))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(r).get("id").asText());
    }

    private JsonNode filaDeCola(String tokenAdmin, UUID denunciaId) throws Exception {
        JsonNode cola = json(mockMvc.perform(get("/api/admin/moderacion/denuncias")
                .header("Authorization", bearer(tokenAdmin))).andExpect(status().isOk()).andReturn());
        for (JsonNode f : cola) {
            if (f.get("id").asText().equals(denunciaId.toString())) {
                return f;
            }
        }
        throw new AssertionError("la denuncia no está en la cola");
    }

    @Test
    void conflictoDeInteres_403ConCodigo_yOtroAdminResuelve() throws Exception {
        Usuario tutorAdmin = tutor(null, null);
        hacerAdmin(tutorAdmin, "MODERACION_SEGURIDAD");
        UUID id = denunciar(adulto(false), tutorAdmin, "https://ejemplo.com/captura.png");
        String tokenTutorAdmin = token(tutorAdmin);

        assertThat(filaDeCola(tokenTutorAdmin, id).get("conflicto").asBoolean()).isTrue();
        postJson("/api/admin/moderacion/denuncias/" + id + "/resolver", tokenTutorAdmin, Map.of("resolucion", "infundada"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO_DE_INTERES"))
                .andExpect(jsonPath("$.error").value(
                        "No podés resolver un caso en el que sos parte. Lo tiene que resolver otro admin de Moderación."));

        Usuario otroAdmin = adulto(false);
        hacerAdmin(otroAdmin, "MODERACION_SEGURIDAD");
        String tokenOtro = token(otroAdmin);
        assertThat(filaDeCola(tokenOtro, id).get("conflicto").asBoolean()).isFalse();
        postJson("/api/admin/moderacion/denuncias/" + id + "/resolver", tokenOtro, Map.of("resolucion", "infundada"))
                .andExpect(status().isOk());
    }

    @Test
    void colaAdmin_traeEvidenciaYDenunciado_sinDenunciante() throws Exception {
        Usuario denunciado = tutor(null, null);
        Usuario denunciante = adulto(false);
        UUID id = denunciar(denunciante, denunciado, "https://ejemplo.com/evidencia.png");
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");

        JsonNode fila = filaDeCola(token(admin), id);
        assertThat(fila.get("evidenciaUrl").asText()).isEqualTo("https://ejemplo.com/evidencia.png");
        assertThat(fila.get("denunciadoNombre").asText()).isEqualTo(denunciado.getNombre());
        assertThat(fila.get("denunciadoTipo").asText()).isEqualTo("TUTOR");
        assertThat(fila.toString()).doesNotContain("denunciante").doesNotContain(denunciante.getId().toString());

        String recibidas = mockMvc.perform(get("/api/denuncias/recibidas").header("Authorization", bearer(token(denunciado))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(recibidas).doesNotContain("evidenciaUrl");
    }

    @Test
    void descargos_seConservanEnElHistorial() throws Exception {
        Usuario denunciado = tutor(null, null);
        UUID id = denunciar(adulto(false), denunciado, "https://ejemplo.com/e.png");
        String t = token(denunciado);
        postJson("/api/denuncias/" + id + "/descargo", t, Map.of("descargo", "Primer descargo")).andExpect(status().isOk());
        postJson("/api/denuncias/" + id + "/descargo", t, Map.of("descargo", "Segundo descargo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descargoTexto").value("Segundo descargo"));
        postJson("/api/denuncias/" + id + "/descargo", token(adulto(false)), Map.of("descargo", "Ajeno"))
                .andExpect(status().isForbidden());

        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");
        String historial = filaDeCola(token(admin), id).get("descargoHistorial").asText();
        assertThat(historial).contains("Primer descargo").contains("Segundo descargo").contains("\n---\n");
        assertThat(historial).containsPattern("\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\\] Primer descargo");
    }

    @Test
    void cap_reRechazarSinCategoria_conservaLaCategoria() throws Exception {
        Usuario tutor = tutor(null, null);
        UUID cap = jdbc.queryForObject("""
                INSERT INTO identidad.certificados_antecedentes_penales
                    (tutor_id, archivo_url, fecha_emision, vence_at, estado, tiene_antecedentes, categoria_antecedente)
                VALUES (?, 'cap.pdf', ?, ?, 'EN_REVISION_LEGAL', true, 'OTRO') RETURNING id""", UUID.class,
                tutor.getId(), LocalDate.now(), LocalDate.now().plusMonths(12));
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");
        mockMvc.perform(patch("/api/admin/moderacion/antecedentes-penales/" + cap)
                        .header("Authorization", bearer(token(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accion\":\"RECHAZAR\"}"))
                .andExpect(status().isOk());
        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT estado, tiene_antecedentes, categoria_antecedente FROM identidad.certificados_antecedentes_penales WHERE id = ?", cap);
        assertThat(fila.get("estado")).isEqualTo("RECHAZADO");
        assertThat(fila.get("categoria_antecedente")).isEqualTo("OTRO");
        assertThat(fila.get("tiene_antecedentes")).isEqualTo(true);
    }

    @Test
    void rolEquivocado_403AntesQueValidacion() throws Exception {
        Usuario moderacion = adulto(false);
        hacerAdmin(moderacion, "MODERACION_SEGURIDAD");
        postJson("/api/admin/financiero/transacciones/" + UUID.randomUUID() + "/reembolso-parcial", token(moderacion), "{}")
                .andExpect(status().isForbidden());
        Usuario financiero = adulto(false);
        hacerAdmin(financiero, "SOPORTE_FINANCIERO");
        mockMvc.perform(get("/api/admin/moderacion/denuncias").header("Authorization", bearer(token(financiero))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/financiero/reembolsos-adicional").header("Authorization", bearer(token(financiero))))
                .andExpect(status().isOk());
    }
}
