package com.tinku.numeros;

import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los montos se guardan en NUMERIC(10, 2) (hasta $99.999.999,99). Una tarifa por hora sin tope
 * terminaba en un 500 al guardarla, o, si entraba, en un 500 al reservar: la clase de 180 minutos
 * (× 3) o el paquete de 4 clases (× 12) ya no entran en la columna.
 */
class MontosTopeIntegracionTest extends IntegracionBase {

    private int ponerTarifa(String token, String precioHora) throws Exception {
        return mockMvc.perform(put("/api/pagos/tarifa").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioHora\":" + precioHora + "}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void tarifaQueNoEntraEnLaColumna_es422ConMensaje_no500() throws Exception {
        String t = token(tutor(null, null));
        var res = mockMvc.perform(put("/api/pagos/tarifa").header("Authorization", bearer(t))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"precioHora\":100000000}"))
                .andExpect(status().isUnprocessableEntity()).andReturn();
        assertThat(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("no puede superar");
    }

    @Test
    void tarifaConLaQueElPaqueteNoEntra_seRechaza() throws Exception {
        String t = token(tutor(null, null));
        // 9.000.000/h: entra en la columna, pero 4 clases de 3 h son $108.000.000.
        assertThat(ponerTarifa(t, "9000000")).isEqualTo(422);
        // El tope mismo vale: 4 × 3 h × 8.333.333,33 = 99.999.999,96.
        assertThat(ponerTarifa(t, "8333333.33")).isEqualTo(200);
    }

    @Test
    void precioRegionalQueNoEntraEnLaColumna_es400_no500() throws Exception {
        var soporte = adulto(false);
        hacerAdmin(soporte, "SOPORTE_FINANCIERO");
        mockMvc.perform(post("/api/admin/financiero/precios-regionales").header("Authorization", bearer(token(soporte)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provincia\":\"Salta\",\"valorSugerido\":100000000}"))
                .andExpect(status().isBadRequest());
    }

    /** PageRequest calcula el offset en long, pero JPA lo pide en int: página × 30 > 2^31 era un 500. */
    @Test
    void paginaDeNotificacionesEnorme_daListaVacia_no500() throws Exception {
        mockMvc.perform(get("/api/notificaciones").param("pagina", "100000000")
                        .header("Authorization", bearer(token(adulto(false)))))
                .andExpect(status().isOk());
    }
}
