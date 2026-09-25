package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ConsentimientoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** T09 / Art. II: el adicional de resumen nunca se contrata para un Menor, aunque todo lo demás esté. */
class AdicionalResumenTest {

    private static Usuario usuario(TipoUsuario tipo) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setTipo(tipo);
        return u;
    }

    @Test
    void reservaConAdicional_beneficiarioMenor_422() {
        ConsentimientoService consentimiento = mock(ConsentimientoService.class);
        when(consentimiento.haAceptado(any(), anyString())).thenReturn(true);
        AdicionalResumen adicional = new AdicionalResumen(true, new BigDecimal("770"), consentimiento);

        assertThatThrownBy(() -> adicional.validarContratacion(
                usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.MENOR), usuario(TipoUsuario.TUTOR)))
                .isInstanceOf(AdicionalResumenNoDisponibleException.class)
                .hasMessageContaining("menores");
    }

    @Test
    void adicionalApagado_noSeContrata() {
        AdicionalResumen adicional = new AdicionalResumen(false, new BigDecimal("770"), mock(ConsentimientoService.class));

        assertThatThrownBy(() -> adicional.validarContratacion(
                usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.TUTOR)))
                .isInstanceOf(AdicionalResumenNoDisponibleException.class);
    }
}
