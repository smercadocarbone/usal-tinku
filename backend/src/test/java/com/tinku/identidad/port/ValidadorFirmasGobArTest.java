package com.tinku.identidad.port;

import com.tinku.identidad.port.ValidadorFirmasPdf.EstadoValidacion;
import com.tinku.identidad.port.ValidadorFirmasPdf.Resultado;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-M1-10: lectura de las respuestas del validador oficial (validadordefirmas.gob.ar). */
class ValidadorFirmasGobArTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private Resultado leer(String cuerpo) {
        return ValidadorFirmasGobAr.interpretar(json.readTree(cuerpo));
    }

    @Test
    void pdfSinFirmas_respuestaReal() {
        // Respuesta real del validador (2026-10-15) para un PDF sin firmar. Ojo: "FINISHIED" con dos I.
        Resultado r = leer("""
                {"state":"FINISHIED","timestamp":"00:00:00.000","verificationAsyncResult":{"valid":false,
                "message":"El pdf no contiene firmas que verificar. ","sheets":1,
                "sha256Digest":"794abaa4f6f06fc519895c22944a0ab43ad02b4fb32bdefa1952ce81613cb47b"}}""");

        assertThat(r.estado()).isEqualTo(EstadoValidacion.TERMINADO);
        assertThat(r.valido()).isFalse();
        assertThat(r.mensaje()).contains("no contiene firmas");
        assertThat(r.firmas()).isEmpty();
        assertThat(r.sha256()).hasSize(64);
    }

    @Test
    void pdfFirmado_leeLosFirmantes() {
        Resultado r = leer("""
                {"state":"FINISHIED","verificationAsyncResult":{"valid":true,"message":"","sha256Digest":"ab",
                "signatures":[{"validSign":true,"commonName":"JUAN PEREZ","organization":"Ministerio de Justicia",
                "organizationalUnit":"Registro Nacional de Reincidencia"}]}}""");

        assertThat(r.valido()).isTrue();
        assertThat(r.firmas()).singleElement().satisfies(f -> {
            assertThat(f.valida()).isTrue();
            assertThat(f.nombre()).isEqualTo("JUAN PEREZ");
            assertThat(f.unidad()).isEqualTo("Registro Nacional de Reincidencia");
        });
    }

    @Test
    void enEspera_protegido_yEstadosRaros() {
        assertThat(leer("{\"state\":\"WAITING_PROCESS\",\"posDataQueue\":3}").estado()).isEqualTo(EstadoValidacion.EN_COLA);
        assertThat(leer("{\"state\":\"FINISHIED_PROTECTED\",\"verificationAsyncResult\":{}}").estado())
                .isEqualTo(EstadoValidacion.PROTEGIDO);
        assertThat(leer("{\"state\":\"OTRA_COSA\"}").estado()).isEqualTo(EstadoValidacion.DESCONOCIDO);
        assertThatThrownBy(() -> leer("{\"error\":\"x\"}")).isInstanceOf(ValidadorNoDisponibleException.class);
    }
}
