package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ConsentimientoService;
import com.tinku.reservas.model.Reserva;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * T09 (DT3) + T08 (ADR-M3-04): reglas del adicional pago de resumen. Único árbitro de las cuatro
 * condiciones del Artículo V para grabar: adicional contratado, beneficiario NO Menor, cláusula
 * aceptada por pagador y Tutor, y el adicional habilitado (apagado hasta tener el texto legal).
 */
@Component
public class AdicionalResumen {

    private static final BigDecimal DECENA = BigDecimal.TEN;

    private final boolean habilitado;
    private final ConsentimientoService consentimiento;
    private final BigDecimal costoTranscripcionPorMin;
    private final BigDecimal costoEntradaPorToken;
    private final BigDecimal tokensPorMin;
    private final BigDecimal costoSalidaPorToken;
    private final BigDecimal tokensSalida;
    private final BigDecimal margen;
    private final BigDecimal tipoCambio;

    public AdicionalResumen(@Value("${tinku.resumen.adicional.habilitado:false}") boolean habilitado,
                            ConsentimientoService consentimiento,
                            @Value("${tinku.resumen.adicional.costo-transcripcion-usd-min:0.0030}") BigDecimal costoTranscripcionPorMin,
                            @Value("${tinku.resumen.adicional.costo-entrada-usd-token:0.0000025}") BigDecimal costoEntradaPorToken,
                            @Value("${tinku.resumen.adicional.tokens-por-minuto:217}") BigDecimal tokensPorMin,
                            @Value("${tinku.resumen.adicional.costo-salida-usd-token:0.00001}") BigDecimal costoSalidaPorToken,
                            @Value("${tinku.resumen.adicional.tokens-salida:840}") BigDecimal tokensSalida,
                            @Value("${tinku.resumen.adicional.margen:1.50}") BigDecimal margen,
                            @Value("${tinku.resumen.adicional.tipo-cambio-ars:1535}") BigDecimal tipoCambio) {
        this.habilitado = habilitado;
        this.consentimiento = consentimiento;
        this.costoTranscripcionPorMin = costoTranscripcionPorMin;
        this.costoEntradaPorToken = costoEntradaPorToken;
        this.tokensPorMin = tokensPorMin;
        this.costoSalidaPorToken = costoSalidaPorToken;
        this.tokensSalida = tokensSalida;
        this.margen = margen;
        this.tipoCambio = tipoCambio;
    }

    /**
     * ADR-M5-06 / tesis Cap. 5: precio = margen × costo de IA de la clase, en ARS y redondeado hacia
     * arriba a la decena. Costo = transcripción por minuto + tokens de entrada por minuto + tokens de
     * salida fijos. Todo en {@link BigDecimal}: es dinero.
     */
    public BigDecimal precio(int duracionMinutos) {
        if (duracionMinutos <= 0) {
            throw new IllegalArgumentException("La duración debe ser positiva: " + duracionMinutos);
        }
        BigDecimal d = BigDecimal.valueOf(duracionMinutos);
        BigDecimal costoUsd = costoTranscripcionPorMin.multiply(d)
                .add(costoEntradaPorToken.multiply(tokensPorMin).multiply(d))
                .add(costoSalidaPorToken.multiply(tokensSalida));
        BigDecimal ars = costoUsd.multiply(margen).multiply(tipoCambio);
        return ars.divide(DECENA, 0, RoundingMode.CEILING).multiply(DECENA).setScale(0);
    }

    /** Si el Tutor lo ofrece: adicional habilitado y cláusula aceptada por el Tutor. */
    public boolean disponibleCon(UUID tutorId) {
        return habilitado && consentimiento.haAceptado(tutorId, ConsentimientoService.GRABACION_AUDIO_RESUMEN);
    }

    /** Al reservar con el adicional: 422 si alguna condición no se cumple (no confía en el frontend). */
    public void validarContratacion(Usuario pagador, Usuario beneficiario, Usuario tutor) {
        if (!habilitado) {
            throw new AdicionalResumenNoDisponibleException("El resumen automático todavía no está disponible.");
        }
        if (beneficiario.getTipo() == TipoUsuario.MENOR) {
            throw new AdicionalResumenNoDisponibleException(
                    "El resumen automático no se ofrece en clases con menores.");
        }
        if (!consentimiento.haAceptado(tutor.getId(), ConsentimientoService.GRABACION_AUDIO_RESUMEN)) {
            throw new AdicionalResumenNoDisponibleException("Este tutor todavía no habilitó el resumen.");
        }
        if (!consentimiento.haAceptado(pagador.getId(), ConsentimientoService.GRABACION_AUDIO_RESUMEN)) {
            throw new AdicionalResumenNoDisponibleException(
                    "Para contratar el resumen tenés que aceptar la grabación de solo audio de la clase.");
        }
    }

    /** Re-chequeo al grabar y al recibir el audio (Art. II: nunca con un Menor, aunque figure contratado). */
    public boolean permiteGrabar(Reserva reserva) {
        return habilitado
                && reserva.isResumenContratado()
                && reserva.getBeneficiario().getTipo() != TipoUsuario.MENOR
                && reserva.getPagador() != null
                && consentimiento.haAceptado(reserva.getTutor().getId(), ConsentimientoService.GRABACION_AUDIO_RESUMEN)
                && consentimiento.haAceptado(reserva.getPagador().getId(), ConsentimientoService.GRABACION_AUDIO_RESUMEN);
    }
}
