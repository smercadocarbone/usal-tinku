package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ConsentimientoService;
import com.tinku.reservas.model.Reserva;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * T09 (DT3) + T08 (ADR-M3-04): reglas del adicional pago de resumen. Único árbitro de las cuatro
 * condiciones del Artículo V para grabar: adicional contratado, beneficiario NO Menor, cláusula
 * aceptada por pagador y Tutor, y el adicional habilitado (apagado hasta tener el texto legal).
 */
@Component
public class AdicionalResumen {

    private final boolean habilitado;
    private final BigDecimal precio;
    private final ConsentimientoService consentimiento;

    public AdicionalResumen(@Value("${tinku.resumen.adicional.habilitado:false}") boolean habilitado,
                            @Value("${tinku.resumen.adicional.precio-ars:770}") BigDecimal precio,
                            ConsentimientoService consentimiento) {
        this.habilitado = habilitado;
        this.precio = precio;
        this.consentimiento = consentimiento;
    }

    public BigDecimal precio() {
        return precio;
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
