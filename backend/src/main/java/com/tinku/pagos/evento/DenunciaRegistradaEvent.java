package com.tinku.pagos.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * {@code denuncia.registrada} (M9 → M5, Spec_M5 §2): se registró una Denuncia
 * sobre la sesión de esta Reserva con escrow activo. M5 pausa la liberación de
 * fondos hasta la resolución ({@code estado → pausado_denuncia}) y cancela la
 * ventana de liberación si existía. Definido por el consumidor con el payload
 * mínimo (reservaId); M9-D publicará esta clase y puede enriquecer la
 * documentación en Spec_M5 §2 (AGENTS §4) si hace falta.
 */
public class DenunciaRegistradaEvent extends ApplicationEvent {

    private final String nombre = "denuncia.registrada";
    private final UUID reservaId;

    public DenunciaRegistradaEvent(Object source, UUID reservaId) {
        super(source);
        this.reservaId = reservaId;
    }

    public String getNombre() {
        return nombre;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}