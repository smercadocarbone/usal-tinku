package com.tinku.identidad.port;

import com.tinku.identidad.port.ValidadorFirmasPdf.EstadoValidacion;
import com.tinku.identidad.port.ValidadorFirmasPdf.Resultado;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-M1-10: prueba contra el validador oficial de verdad. Solo corre con
 * {@code CAP_VALIDADOR_REAL=1}: la suite normal no le manda pedidos a un servicio público.
 * Manda un PDF propio, sin firmar y sin datos de nadie.
 */
class ValidadorFirmasGobArRealTest {

    @Test
    void pdfSinFirmar_elValidadorRealDiceQueNoTieneFirmas() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("CAP_VALIDADOR_REAL")),
                "solo con CAP_VALIDADOR_REAL=1");
        ValidadorFirmasGobAr cliente = new ValidadorFirmasGobAr("https://validadordefirmas.gob.ar",
                JsonMapper.builder().build());
        byte[] pdf = ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);

        assertThat(cliente.pedidosEnCola()).isNotNegative();
        String id = cliente.enviar(pdf);
        Resultado r = cliente.consultar(id);
        for (int i = 0; i < 15 && r.estado() == EstadoValidacion.EN_COLA; i++) {
            Thread.sleep(2000); // solo en esta prueba manual: el sistema espera con la cola de Quartz
            r = cliente.consultar(id);
        }
        assertThat(r.estado()).isEqualTo(EstadoValidacion.TERMINADO);
        assertThat(r.valido()).isFalse();
        assertThat(r.mensaje()).contains("no contiene firmas");
    }
}
