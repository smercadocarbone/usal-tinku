package com.tinku.reservas.service;

import java.time.ZoneId;

/**
 * Zona horaria de negocio de Tinku: franjas y horarios se interpretan en
 * America/Argentina/Buenos_Aires (producto argentino). Los clientes envían
 * horarios como Instant (ISO-8601 con offset); acá se normalizan a esta zona
 * para comparar día/hora contra las franjas publicadas.
 */
public final class ReservasZonaHoraria {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private ReservasZonaHoraria() {
    }
}