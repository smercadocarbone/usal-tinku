package com.tinku.seguridad.evento;

import com.tinku.aula.evento.SesionEvento;

import java.util.UUID;

/**
 * {@code denuncia.registrada} (M9 → M5, Spec_M5 §2): se registró una Denuncia
 * sobre la sesión de esta Reserva con escrow activo. M5 pausa la liberación de
 * fondos hasta la resolución ({@code estado → pausado_denuncia}) y cancela la
 * ventana de liberación si existía. Definido por el consumidor con el payload
 * mínimo (reservaId); M9-D publicará esta clase y puede enriquecer la
 * documentación en Spec_M5 §2 (AGENTS §4) si hace falta.
 */
public class DenunciaRegistradaEvent extends SesionEvento {

    public DenunciaRegistradaEvent(Object source, UUID reservaId) {
        super(source, "denuncia.registrada", reservaId);
    }
}
