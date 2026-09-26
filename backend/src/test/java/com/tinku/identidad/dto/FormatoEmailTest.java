package com.tinku.identidad.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 2026-09-26: "ana@gmail" (sin extensión) pasaba el @Email de Jakarta y se guardaba. */
class FormatoEmailTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valido(String email) {
        return validator.validate(new ActualizarEmailRequest(email)).isEmpty();
    }

    @Test
    void aceptaEmailsReales() {
        assertThat(valido("ana@gmail.com")).isTrue();
        assertThat(valido("ana.perez+tinku@alumnos.usal.edu.ar")).isTrue();
        assertThat(valido("12345678@tinku.test")).isTrue();
    }

    @Test
    void rechazaEmailsIncompletos() {
        assertThat(valido("ana@gmail")).isFalse();
        assertThat(valido("ana@gmail.")).isFalse();
        assertThat(valido("ana gmail.com")).isFalse();
        assertThat(valido("ana@.com")).isFalse();
        assertThat(valido("@gmail.com")).isFalse();
    }
}
