package com.tinku.identidad.port;

import java.util.List;

/**
 * Puerto hacia el validador oficial de firma digital (ADR-M1-10). La implementación real
 * ({@link ValidadorFirmasGobAr}) habla con validadordefirmas.gob.ar; los tests la simulan.
 *
 * <p>Ninguna llamada reintenta sola: el ritmo y los reintentos los maneja la cola persistida
 * ({@code VerificacionFirmaCapService}), para no saturar un servicio público.</p>
 */
public interface ValidadorFirmasPdf {

    /** Pedidos que el validador tiene en espera. Su propia página no deja entrar con 300 o más. */
    int pedidosEnCola();

    /** Envía el PDF y devuelve el identificador del pedido. */
    String enviar(byte[] pdf);

    /** Estado del pedido; {@link EstadoValidacion#EN_COLA} si todavía no terminó. */
    Resultado consultar(String idPedido);

    enum EstadoValidacion {
        /** Todavía no terminó. */
        EN_COLA,
        /** Terminó: el resultado está en {@code valido}, {@code mensaje} y {@code firmas}. */
        TERMINADO,
        /** El PDF está protegido con contraseña: no se puede verificar. */
        PROTEGIDO,
        /** El validador informó un estado que no se reconoce. */
        DESCONOCIDO
    }

    record Firma(boolean valida, String nombre, String organizacion, String unidad) {
    }

    record Resultado(EstadoValidacion estado, boolean valido, String mensaje, String sha256, List<Firma> firmas) {
    }
}
