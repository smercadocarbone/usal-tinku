package com.tinku.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR-M2-04: el texto que se guarda como tema sugerido va normalizado y sin números largos. */
class TemasSugeridosNormalizacionTest {

    @Test
    void minusculasSinTildesYEspaciosColapsados() {
        assertThat(TemasSugeridosService.normalizar("  Ecuaciones   CUADRÁTICAS  ")).isEqualTo("ecuaciones cuadraticas");
    }

    @Test
    void losNumerosLargosNuncaSeGuardan_dniOTelefono() {
        assertThat(TemasSugeridosService.normalizar("clases para 40123456 al 1155667788"))
                .isEqualTo("clases para # al #");
        assertThat(TemasSugeridosService.normalizar("tabla del 7 y del 12")).isEqualTo("tabla del 7 y del 12");
    }

    @Test
    void sinSignosRarosYCortadoAlLargoMaximo() {
        assertThat(TemasSugeridosService.normalizar("¿qué es C++? <script>")).isEqualTo("que es c++ script");
        assertThat(TemasSugeridosService.normalizar("a".repeat(200))).hasSize(TemasSugeridosService.LARGO_MAXIMO);
    }
}
