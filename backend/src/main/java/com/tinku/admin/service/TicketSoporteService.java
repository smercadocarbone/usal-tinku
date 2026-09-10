package com.tinku.admin.service;

import com.tinku.admin.model.MapeoOrigenRol;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.model.TicketSoporte;
import com.tinku.admin.repository.MapeoOrigenRolRepository;
import com.tinku.admin.repository.TicketSoporteRepository;
import com.tinku.identidad.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
}