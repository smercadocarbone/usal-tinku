package com.tinku.pagos.evento;

import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Base de los eventos ENTRE MODULOS que M5 consume (Spec_M5 §2): {@code
 * sesion.*} llega de M3 (aula), {@code denuncia.registrada} de M9. Definidos por
 * el CONSUMIDOR (M5) como stub con el payload mínimo; M3-E/M9-D publicarán estas
 * mismas clases — precedente del repo: {@code DenunciaResueltaEvent} lo definió
 * M4 y lo publicará M9-D.
 *
 * El {@code nombre} sigue la convención de AGENTS §4: es el contrato público de
 * cada evento (ej. {@code sesion.finalizada}) y NO se renombra. Cada subclase
 * tiene exactamente un listener en {@code EscrowService}.
 */
public abstract class SesionEvento extends ApplicationEvent {

    private final String nombre;
    private final UUID reservaId;

    protected SesionEvento(Object source, String nombre, UUID reservaId) {
        super(source);
        this.nombre = nombre;
        this.reservaId = reservaId;
    }

    public String getNombre() {
        return nombre;
    }

    public UUID getReservaId() {
        return reservaId;
    }
}