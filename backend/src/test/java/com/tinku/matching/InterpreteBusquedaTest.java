package com.tinku.matching;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** FR-MATCH-011: nivel y raíces de lo escrito, sin depender del modelo de embeddings. */
class InterpreteBusquedaTest {

    @Test
    void reconoceElNivelEscrito() {
        assertThat(InterpreteBusqueda.nivel("divisiones en primario")).contains("primario");
        assertThat(InterpreteBusqueda.nivel("Fracciones de la primaria")).contains("primario");
        assertThat(InterpreteBusqueda.nivel("química del secundario")).contains("secundario");
        assertThat(InterpreteBusqueda.nivel("análisis para la facu")).contains("universitario");
        assertThat(InterpreteBusqueda.nivel("parcial de la Universidad")).contains("universitario");
    }

    @Test
    void segundoGradoNoEsPrimario() {
        // "ecuaciones de segundo grado" es un tema de secundario: "grado" no dice el nivel.
        assertThat(InterpreteBusqueda.nivel("ecuaciones de segundo grado")).isEmpty();
    }

    @Test
    void raicesSinPalabrasVaciasNiElNivel() {
        assertThat(InterpreteBusqueda.raices("Necesito ayuda con divisiones en primario"))
                .containsExactly("divisi");
        assertThat(InterpreteBusqueda.raices("multiplicar fracciones")).containsExactly("multip", "fracci");
        assertThat(InterpreteBusqueda.raices("la luz")).isEmpty();
    }
}
