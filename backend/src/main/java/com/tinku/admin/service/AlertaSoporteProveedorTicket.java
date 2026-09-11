package com.tinku.admin.service;

import com.tinku.admin.model.TicketSoporte;
import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación real de {@link AlertaSoporteProveedor} (Spec M8): en lugar del
 * stub de log, cada liberación de escrow que agota sus reintentos automáticos
 * genera un {@link TicketSoporte} de Soporte Financiero ({@code origen_modulo}
 * {@code M5.pago_fallido}), enrutado por {@code mapeo_origen_rol} como el resto
 * de los tickets (FR-ADM-006, T-M8-05).
 *
 * El ticket lo abre el Tutor que cobraría la liberación (el reclamante natural:
 * la Reserva por la que se retiene el escrow). Se crea UNA sola vez por
 * transacción — cuando {@code intentos_liberacion} alcanza el máximo de
 * reintentos (FR-PAG-007 / Tabla_Tiempos) — porque el job la invoca en cada
 * fallo: los fallos previos no vuelven a abrir tickets (evita spam, y el fallo
 * del reintento manual tampoco).
 *
 * Nunca lanza: la alerta corre dentro del flujo transactional de la liberación
 * y no puede tumbar el avance de un escrow cuyo estado ya es terminal (misma
 * garantía del contrato del port) — si falla, queda en log.
 */
@Component
public class AlertaSoporteProveedorTicket implements AlertaSoporteProveedor {

    /** Tabla_Tiempos: 3 reintentos automáticos de liberación (5/15/1h). */
    static final int INTENTOS_AGOTADOS = 3;

    private static final Logger log = LoggerFactory.getLogger(AlertaSoporteProveedorTicket.class);

    private final TicketSoporteService ticketService;
    private final ReservaRepository reservaRepo;

    public AlertaSoporteProveedorTicket(TicketSoporteService ticketService,
                                        ReservaRepository reservaRepo) {
        this.ticketService = ticketService;
        this.reservaRepo = reservaRepo;
    }

    @Override
    public void notificarFalloLiberacion(Transaccion transaccion) {
        if (transaccion.getIntentosLiberacion() != INTENTOS_AGOTADOS) {
            return; // reintentos en curso (1-2) o fallo posterior al ticket (4+) — no re-abrir
        }
        try {
            Usuario tutor = reservaRepo.findById(transaccion.getReservaId())
                    .map(r -> r.getTutor())
                    .orElse(null);
            if (tutor == null) {
                log.warn("Liberación agotada sin Reserva para abrir ticket — transaccion={}, "
                        + "reservaId={}", transaccion.getId(), transaccion.getReservaId());
                return;
            }
            ticketService.crear(tutor, "M5.pago_fallido",
                    "No me llega la liberación",
                    "Mi escrow lleva días retenido. Transacción " + transaccion.getId()
                            + " (reserva " + transaccion.getReservaId()
                            + ", " + transaccion.getMontoBruto() + " ARS).");
        } catch (RuntimeException e) {
            log.error("No se pudo abrir el ticket de Soporte Financiero por la "
                    + "liberación agotada — transaccion=" + transaccion.getId(), e);
        }
    }
}