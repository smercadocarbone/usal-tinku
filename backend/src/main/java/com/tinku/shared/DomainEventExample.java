package com.tinku.shared;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ejemplo de referencia del mecanismo de eventos de dominio en memoria
 * (Constitucion, Articulo IX): se usa para efectos secundarios de un solo
 * disparo con multiples reacciones, SIN un message broker externo
 * (Kafka/RabbitMQ estan explicitamente prohibidos salvo cambio de escala).
 *
 * Casos reales que van a usar este mismo patron (no reinventarlo por modulo):
 *  - M9 -> sancion aplicada -> listeners en M1 (suspender cuenta), M2 (excluir
 *    de matching), M4 (cancelar reservas futuras), M5 (reglas de fondos).
 *    Ver Plan_M9_Denuncias_Seguridad.md, seccion 2.5.
 *  - M3 -> kill-switch disparado -> listeners en M5 (reembolso), M9 (crear
 *    Alerta de Seguridad).
 *
 * Borrar esta clase de ejemplo una vez que el primer evento real (ej.
 * SancionAplicadaEvent) este implementado siguiendo el mismo patron.
 */
public class DomainEventExample {

    public static class EjemploEvent extends ApplicationEvent {
        private final String mensaje;

        public EjemploEvent(Object source, String mensaje) {
            super(source);
            this.mensaje = mensaje;
        }

        public String getMensaje() {
            return mensaje;
        }
    }

    @Component
    public static class EjemploListener {
        @EventListener
        public void onEjemplo(EjemploEvent event) {
            System.out.println("Listener de ejemplo recibio: " + event.getMensaje());
        }
    }
}
