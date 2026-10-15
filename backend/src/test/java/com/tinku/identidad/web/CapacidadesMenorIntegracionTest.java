package com.tinku.identidad.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T3-01 (pruebas en producción, 2026-10-14; M3C-10 H-1): un menor no administra sus capacidades.
 * Su cuenta la administra Tinku y su Adulto Responsable (Artículo II).
 */
class CapacidadesMenorIntegracionTest extends IntegracionBase {

    @Test
    void capacidades_menor_403_yNoCambiaNada() throws Exception {
        Usuario ar = adulto(true);
        Usuario menor = menor(ar);
        String token = token(menor);

        for (String cuerpo : new String[]{
                "{\"capacidadEstudiante\":false,\"capacidadAdultoResponsable\":false}",
                "{\"capacidadEstudiante\":true,\"capacidadAdultoResponsable\":false}"}) {
            mockMvc.perform(patch("/api/usuarios/me/capacidades")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isForbidden());
        }

        Usuario despues = usuarioRepo.findById(menor.getId()).orElseThrow();
        assertThat(despues.isCapacidadEstudiante()).isTrue();
        assertThat(despues.isCapacidadAdultoResponsable()).isFalse();
    }

    @Test
    void capacidades_adulto_sigueFuncionando() throws Exception {
        Usuario adulto = adulto(false);
        mockMvc.perform(patch("/api/usuarios/me/capacidades")
                        .header("Authorization", bearer(token(adulto)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacidadEstudiante\":true,\"capacidadAdultoResponsable\":true}"))
                .andExpect(status().isOk());
        assertThat(usuarioRepo.findById(adulto.getId()).orElseThrow().isCapacidadAdultoResponsable()).isTrue();
    }
}
