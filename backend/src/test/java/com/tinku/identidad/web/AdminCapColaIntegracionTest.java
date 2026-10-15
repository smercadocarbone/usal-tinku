package com.tinku.identidad.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T4-H1 (pruebas en producción, 2026-10-14; M3C-10 H-2): la cola de CAP daba 403 apenas había un
 * CAP pendiente. Causa: la respuesta leía el Tutor (LAZY) fuera de la transacción.
 */
class AdminCapColaIntegracionTest extends IntegracionBase {

    @Test
    void cola_conCapPendiente_200_conNombreDelTutor() throws Exception {
        Usuario tutor = tutor(null, null);
        jdbc.update("""
                INSERT INTO identidad.certificados_antecedentes_penales
                    (tutor_id, archivo_url, fecha_emision, vence_at, estado)
                VALUES (?, 'cap-prueba.pdf', ?, ?, 'PENDIENTE')""",
                tutor.getId(), LocalDate.now(), LocalDate.now().plusMonths(12));
        Usuario admin = adulto(false);
        hacerAdmin(admin, "MODERACION_SEGURIDAD");

        MvcResult res = mockMvc.perform(get("/api/admin/moderacion/antecedentes-penales")
                        .header("Authorization", bearer(token(admin))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode cola = json(res);
        JsonNode fila = null;
        for (JsonNode f : cola) {
            if (f.get("tutorId").asText().equals(tutor.getId().toString())) {
                fila = f;
            }
        }
        assertThat(fila).as("el CAP pendiente está en la cola").isNotNull();
        assertThat(fila.get("tutorNombre").asText()).isEqualTo(tutor.getNombre());
    }
}
