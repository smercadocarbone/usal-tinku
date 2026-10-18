package com.tinku.evaluacion;

import com.tinku.identidad.model.Usuario;
import com.tinku.matching.port.MatchingServiceClient.ResultadoMatch;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-TES-11b §4: con {@code tinku.evaluacion.habilitada} en su default ({@code false}) el sistema se
 * comporta como antes: no registra búsquedas, no ofrece el NPS y los endpoints de usuario dan 404.
 * Usa la configuración por defecto (comparte el contexto de Spring con el resto de la suite).
 */
class EvaluacionApagadaIntegracionTest extends IntegracionBase {

    @Test
    void busqueda_sinFlag_noRegistra_niHeader() throws Exception {
        Usuario a = tutor(null, null);
        String texto = "integrales dobles y triples";
        when(matchingClient.match(anyList(), eq(texto))).thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9)));
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM matching.evaluacion_busquedas", Integer.class);

        MvcResult res = mockMvc.perform(post("/api/busquedas").header("Authorization", bearer(token(adulto(false))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("texto_busqueda", texto))))
                .andExpect(status().isOk()).andReturn();

        assertThat(res.getResponse().getHeader("X-Busqueda-Id")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM matching.evaluacion_busquedas", Integer.class))
                .isEqualTo(antes);
    }

    @Test
    void nps_yAceptaciones_sinFlag_404() throws Exception {
        String t = token(adulto(false));
        mockMvc.perform(get("/api/evaluacion/nps/pendiente").header("Authorization", bearer(t)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/evaluacion/nps").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"puntaje\":9}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/busquedas/" + UUID.randomUUID() + "/aceptaciones").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tutorId\":\"" + UUID.randomUUID() + "\",\"tipo\":\"PERFIL\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void panel_sinFlag_respondeIgualYLoDice() throws Exception {
        Usuario admin = adulto(false);
        hacerAdmin(admin, "SOPORTE_FINANCIERO");
        var r = json(mockMvc.perform(get("/api/admin/evaluacion/indicadores").header("Authorization", bearer(token(admin))))
                .andExpect(status().isOk()).andReturn());
        assertThat(r.get("habilitada").asBoolean()).isFalse();
    }
}
