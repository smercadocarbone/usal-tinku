package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ConsentimientoService;
import org.junit.jupiter.api.Test;

import com.tinku.reservas.model.Reserva;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    /** Parámetros de la tesis (Cap. 5, tabla de parámetros de IA). */
    private static AdicionalResumen adicional(boolean habilitado, ConsentimientoService c) {
        return new AdicionalResumen(habilitado, c, new BigDecimal("0.0030"), new BigDecimal("0.0000025"),
                new BigDecimal("217"), new BigDecimal("0.00001"), new BigDecimal("840"),
                new BigDecimal("1.50"), new BigDecimal("1535"));
    }

    /** ADR-M5-06: la tabla de la tesis, en pesos y redondeada hacia arriba a la decena. */
    @Test
    void precio_creceConLaDuracion_yCoincideConLaTablaDeLaTesis() {
        AdicionalResumen a = adicional(true, mock(ConsentimientoService.class));
        assertThat(a.precio(30)).isEqualByComparingTo("270");
        assertThat(a.precio(60)).isEqualByComparingTo("510");
        assertThat(a.precio(90)).isEqualByComparingTo("760");
        assertThat(a.precio(120)).isEqualByComparingTo("1000");
        assertThat(a.precio(180)).isEqualByComparingTo("1490");
    }

    @Test
    void precio_redondeaHaciaArribaALaDecena_yNoSubeDeMasSiYaEsExacto() {
        ConsentimientoService c = mock(ConsentimientoService.class);
        // costo 1 USD/min sin tokens, margen 1, tipo de cambio 10 -> 10 ARS por minuto, exacto.
        AdicionalResumen exacto = new AdicionalResumen(true, c, BigDecimal.ONE, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN);
        assertThat(exacto.precio(3)).isEqualByComparingTo("30");
        // 1,01 ARS por minuto: 3 min = 3,03 -> sube a la decena siguiente.
        AdicionalResumen conResto = new AdicionalResumen(true, c, new BigDecimal("0.101"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.TEN);
        assertThat(conResto.precio(3)).isEqualByComparingTo("10");
    }

    @Test
    void precio_duracionNoPositiva_noSeCalcula() {
        AdicionalResumen a = adicional(true, mock(ConsentimientoService.class));
        assertThatThrownBy(() -> a.precio(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> a.precio(-30)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permiteGrabar_beneficiarioMenor_nuncaAunqueFigureContratado() {
        ConsentimientoService c = mock(ConsentimientoService.class);
        when(c.haAceptado(any(), anyString())).thenReturn(true);
        AdicionalResumen a = adicional(true, c);
        Reserva r = new Reserva();
        r.setResumenContratado(true);
        r.setBeneficiario(usuario(TipoUsuario.MENOR));
        r.setPagador(usuario(TipoUsuario.ADULTO));
        r.setTutor(usuario(TipoUsuario.TUTOR));
        assertThat(a.permiteGrabar(r)).isFalse();
        r.setBeneficiario(usuario(TipoUsuario.ADULTO));
        assertThat(a.permiteGrabar(r)).isTrue();
    }

    @Test
    void reservaConAdicional_beneficiarioMenor_422() {
        ConsentimientoService consentimiento = mock(ConsentimientoService.class);
        when(consentimiento.haAceptado(any(), anyString())).thenReturn(true);
        AdicionalResumen adicional = adicional(true, consentimiento);

        assertThatThrownBy(() -> adicional.validarContratacion(
                usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.MENOR), usuario(TipoUsuario.TUTOR)))
                .isInstanceOf(AdicionalResumenNoDisponibleException.class)
                .hasMessageContaining("menores");
    }

    @Test
    void adicionalApagado_noSeContrata() {
        AdicionalResumen adicional = adicional(false, mock(ConsentimientoService.class));

        assertThatThrownBy(() -> adicional.validarContratacion(
                usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.ADULTO), usuario(TipoUsuario.TUTOR)))
                .isInstanceOf(AdicionalResumenNoDisponibleException.class);
    }
}
