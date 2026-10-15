package com.tinku.identidad.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** R-B B-9 (T2-H11): qué se tapa y qué no en la presentación del Tutor. */
class OcultadorDeContactoTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Tengo 15 años de experiencia.|Tengo 15 años de experiencia.",
            "Clases de 90 minutos, $12000 la hora.|Clases de 90 minutos, $12000 la hora.",
            "Egresada en 2015, UBA.|Egresada en 2015, UBA.",
            "mi cel es 11 5555-1234|mi cel es [contacto oculto]",
            "llamame al +54 9 11 5555 1234 ya|llamame al [contacto oculto] ya",
            "escribime a profe.mate@gmail.com|escribime a [contacto oculto]",
            "seguime en @profe_mate|seguime en [contacto oculto]",
            "WhatsApp (011) 4555-1234|WhatsApp [contacto oculto]",
    })
    void oculta(String entrada, String esperado) {
        assertThat(OcultadorDeContacto.ocultar(entrada)).isEqualTo(esperado);
    }
}
