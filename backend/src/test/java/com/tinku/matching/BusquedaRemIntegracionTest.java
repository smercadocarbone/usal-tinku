package com.tinku.matching;

import com.tinku.identidad.model.Usuario;
import com.tinku.matching.port.MatchingServiceClient.ResultadoMatch;
import com.tinku.matching.repository.PerfilTutorTemasRepository;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk REM-E (docs/superpowers/specs/remediacion-pruebas/R-E-busqueda-perfil.md): relevancia
 * (T1-M1), búsquedas guardadas con filtros (T1-6) y filtro de materia sin tildes (T1-12).
 */
class BusquedaRemIntegracionTest extends IntegracionBase {

    @Autowired PerfilTutorTemasRepository perfilTemas;

    private ResultActions buscar(String token, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post("/api/busquedas").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)));
    }

    private List<String> directos(JsonNode res) {
        List<String> ids = new ArrayList<>();
        res.forEach(r -> {
            if (!r.get("porArea").asBoolean()) {
                ids.add(r.get("tutorId").asText());
            }
        });
        return ids;
    }

    // ---- E-1 ----

    @Test
    void textoSinLetrasSuficientes_vacioSinLlamarAlModelo() throws Exception {
        String t = token(adulto(false));
        for (String texto : new String[]{"a", "123456", "!!!!"}) {
            JsonNode r = json(buscar(t, Map.of("texto_busqueda", texto)).andExpect(status().isOk()).andReturn());
            assertThat(r).as(texto).isEmpty();
            verify(matchingClient, never()).match(anyList(), eq(texto));
        }
    }

    @Test
    void corteRelativo_yAbsolutoCalibrado() throws Exception {
        Usuario a = tutor(null, null);
        Usuario b = tutor(null, null);
        when(matchingClient.match(anyList(), eq("derivadas e integrales")))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.9), new ResultadoMatch(b.getId(), 0.5)));
        when(matchingClient.match(anyList(), eq("clases de tango bailarín")))
                .thenReturn(List.of(new ResultadoMatch(a.getId(), 0.44)));
        String t = token(adulto(false));

        assertThat(directos(json(buscar(t, Map.of("texto_busqueda", "derivadas e integrales")).andReturn())))
                .containsExactly(a.getId().toString());
        assertThat(directos(json(buscar(t, Map.of("texto_busqueda", "clases de tango bailarín")).andReturn())))
                .isEmpty();
    }

    // ---- Auditoría de acceso: listados sin límite ----

    @Test
    void busqueda_devuelveComoMaximo50Resultados() throws Exception {
        List<ResultadoMatch> todos = new ArrayList<>();
        for (int i = 0; i < 55; i++) {
            todos.add(new ResultadoMatch(tutor(null, null).getId(), 0.9));
        }
        when(matchingClient.match(anyList(), eq("geometría analítica")))
                .thenReturn(todos);
        String t = token(adulto(false));
        JsonNode r = json(buscar(t, Map.of("texto_busqueda", "geometría analítica")).andExpect(status().isOk()).andReturn());
        // El frontend pide el perfil de cada resultado: sin tope, una búsqueda amplia dispara cientos.
        assertThat(r).hasSize(50);
        assertThat(directos(r)).containsExactlyElementsOf(
                todos.subList(0, 50).stream().map(m -> m.tutorId().toString()).toList());
    }

    // ---- E-4 ----

    @Test
    void filtroMateria_sinDistinguirMayusculasNiTildes() throws Exception {
        Usuario tutor = tutor(null, null);
        UUID tema = jdbc.queryForObject("""
                SELECT t.id FROM matching.temas t JOIN matching.trayectos tr ON tr.id = t.trayecto_id
                 WHERE tr.materia = 'Matemática' LIMIT 1""", UUID.class);
        perfilTemas.upsertTemaIds(tutor.getId(), List.of(tema));
        when(matchingClient.match(anyList(), org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> ((List<UUID>) inv.getArgument(0)).stream().map(id -> new ResultadoMatch(id, 0.9)).toList());
        String t = token(adulto(false));
        for (String materia : new String[]{"Matemática", "matematica", "MATEMÁTICA"}) {
            JsonNode r = json(buscar(t, Map.of("filtro_materia", materia)).andExpect(status().isOk()).andReturn());
            assertThat(directos(r)).as(materia).contains(tutor.getId().toString());
        }
    }

    // ---- E-2 ----

    @Test
    void guardada_conservaFiltros_noDuplica_yseBorra() throws Exception {
        Usuario barato = tutor(null, null);
        Usuario caro = tutor(null, null);
        jdbc.update("UPDATE pagos.tarifas_tutor SET precio_hora = ? WHERE tutor_id = ?", new BigDecimal("30000"), caro.getId());
        when(matchingClient.match(anyList(), eq("física y química")))
                .thenReturn(List.of(new ResultadoMatch(barato.getId(), 0.9), new ResultadoMatch(caro.getId(), 0.9)));
        Usuario yo = adulto(false);
        String t = token(yo);
        Map<String, Object> cuerpo = Map.of("texto_busqueda", "física y química", "precio_max_hora", 20000);

        JsonNode g1 = json(mockMvc.perform(post("/api/busquedas/guardadas").header("Authorization", bearer(t))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated()).andReturn());
        JsonNode g2 = json(mockMvc.perform(post("/api/busquedas/guardadas").header("Authorization", bearer(t))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk()).andReturn());
        assertThat(g2.get("id").asText()).isEqualTo(g1.get("id").asText());
        assertThat(g1.get("filtros").get("precio_max_hora").asInt()).isEqualTo(20000);
        assertThat(json(mockMvc.perform(get("/api/busquedas/guardadas").header("Authorization", bearer(t))).andReturn()))
                .hasSize(1);

        String id = g1.get("id").asText();
        JsonNode resultado = json(mockMvc.perform(post("/api/busquedas/guardadas/" + id + "/ejecutar")
                .header("Authorization", bearer(t))).andExpect(status().isOk()).andReturn());
        assertThat(directos(resultado)).contains(barato.getId().toString()).doesNotContain(caro.getId().toString());

        mockMvc.perform(delete("/api/busquedas/guardadas/" + id).header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/busquedas/guardadas/" + id).header("Authorization", bearer(t)))
                .andExpect(status().isNoContent());
        assertThat(json(mockMvc.perform(get("/api/busquedas/guardadas").header("Authorization", bearer(t))).andReturn()))
                .isEmpty();
    }

    @Test
    void guardadaSinFiltros_sigueFuncionando() throws Exception {
        String t = token(adulto(false));
        JsonNode g = json(mockMvc.perform(post("/api/busquedas/guardadas").header("Authorization", bearer(t))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto_busqueda\":\"química orgánica\"}"))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/busquedas/guardadas/" + g.get("id").asText() + "/ejecutar")
                .header("Authorization", bearer(t))).andExpect(status().isOk());
    }
}
