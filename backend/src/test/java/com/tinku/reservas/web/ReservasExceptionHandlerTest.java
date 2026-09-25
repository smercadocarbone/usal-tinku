package com.tinku.reservas.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** AUD-023: solo la superposición de reservas es "horario ocupado" (409). */
class ReservasExceptionHandlerTest {

    private final ReservasExceptionHandler handler = new ReservasExceptionHandler();

    private static DataIntegrityViolationException violacion(String mensajePostgres) {
        return new DataIntegrityViolationException("x", new SQLException(mensajePostgres));
    }

    @Test
    void superposicionDeReservas_409() {
        var r = handler.handleSuperposicion(violacion(
                "ERROR: conflicting key value violates exclusion constraint \"ex_reservas_rango_tutor\""));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void otraViolacion_FK_500SinDisfrazarseDeHorarioOcupado() {
        var r = handler.handleSuperposicion(violacion(
                "ERROR: insert or update on table \"reservas\" violates foreign key constraint \"reservas_beneficiario_id_fkey\""));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().get("error")).doesNotContain("reservado");
    }
}
