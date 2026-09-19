package com.tinku.admin.service;

import com.tinku.admin.model.Admin;
import com.tinku.admin.model.EstadoTicket;
import com.tinku.admin.model.MapeoOrigenRol;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;
import com.tinku.admin.repository.MapeoOrigenRolRepository;
import com.tinku.admin.repository.TicketSoporteRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.AccesoModeracionDenegadoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Canal "contactar a soporte" (US-7, FR-ADM-006, T-M8-05). La creación enruta el
 * ticket al rol según {@code origen_modulo} mirando {@code mapeo_origen_rol} (un
 * SELECT a una tabla de config, no lógica de negocio); sin mapeo → 422 y no se
 * persiste nada.
 */
@Service
public class TicketSoporteService {

    private final TicketSoporteRepository ticketRepo;
    private final MapeoOrigenRolRepository mapeoRepo;

    public TicketSoporteService(TicketSoporteRepository ticketRepo,
                                MapeoOrigenRolRepository mapeoRepo) {
        this.ticketRepo = ticketRepo;
        this.mapeoRepo = mapeoRepo;
    }

    @Transactional
    public TicketSoporte crear(Usuario usuario, String origenModulo, String asunto, String detalle) {
        MapeoOrigenRol mapeo = mapeoRepo.findById(origenModulo)
                .orElseThrow(() -> new OrigenMapNoDefinidoException(origenModulo));

        TicketSoporte ticket = new TicketSoporte();
        ticket.setUsuario(usuario);
        ticket.setOrigenModulo(origenModulo);
        ticket.setAsunto(asunto);
        ticket.setDetalle(detalle);
        ticket.setRolAsignado(mapeo.getRolAsignado());
        return ticketRepo.save(ticket);
    }

    /** Cola de un rol (FR-ADM-008): un Admin solo ve los tickets de su rol. */
    public List<TicketSoporte> listarPorRol(RolAdmin rol) {
        return ticketRepo.findByRolAsignadoOrderByCreadoEnDesc(rol);
    }

    /**
     * Auditoría 2026-09-18 (gap del frontend): transición de estado
     * ({@code abierto → en_proceso → resuelto → cerrado}, V16) — el modelo y el
     * enum ya la anticipaban, pero no existía ningún método para ejecutarla.
     * Solo el Admin del MISMO rol asignado al ticket puede tocarlo (mismo
     * criterio de aislamiento que {@link #listarPorRol}, FR-ADM-008) — un
     * Admin de Soporte Financiero no resuelve un ticket de Moderación aunque
     * conozca el id. Sin restricción de secuencia entre estados (Artículo VII:
     * una máquina de estados estricta no está pedida por ningún Spec y
     * agregaría fricción sin un caso de uso real que la exija); {@code
     * resueltoEn} se fija la primera vez que se alcanza {@code RESUELTO} o
     * {@code CERRADO}, nunca se pisa en transiciones posteriores.
     */
    @Transactional
    public TicketSoporte actualizarEstado(Admin admin, UUID ticketId, EstadoTicket nuevoEstado) {
        TicketSoporte ticket = ticketRepo.findById(ticketId)
                .orElseThrow(TicketNoEncontradoException::new);
        if (ticket.getRolAsignado() != admin.getRol()) {
            throw new AccesoModeracionDenegadoException();
        }
        ticket.setEstado(nuevoEstado);
        if ((nuevoEstado == EstadoTicket.RESUELTO || nuevoEstado == EstadoTicket.CERRADO)
                && ticket.getResueltoEn() == null) {
            ticket.setResueltoEn(Instant.now());
        }
        return ticketRepo.save(ticket);
    }
}