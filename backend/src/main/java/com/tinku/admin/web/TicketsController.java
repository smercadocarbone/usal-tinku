package com.tinku.admin.web;

import com.tinku.admin.model.Admin;
import com.tinku.admin.service.TicketSoporteService;
import com.tinku.admin.model.TicketSoporte;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.shared.AdminModeracionGate;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Canal de soporte (US-7, FR-ADM-006, T-M8-05):
 *
 *  - {@code POST /api/soporte/tickets}: lo usa el USUARIO (Tutor u otro) que
 *    llega a un límite ya definido en otro módulo — cualquier cuenta autenticada,
 *    sin rol de Admin. El ticket se enruta automáticamente por
 *    {@code origen_modulo → rol_asignado} (mapeo de BD).
 *  - {@code GET /api/admin/tickets}: cola del Admin, filtrada por SU rol
 *    (FR-ADM-008) — un Soporte Financiero no ve tickets de Moderación.
 */
@RestController
public class TicketsController {

    private final AdminModeracionGate gate;
    private final TicketSoporteService ticketService;
    private final UsuarioRepository usuarioRepository;

    public TicketsController(AdminModeracionGate gate,
                             TicketSoporteService ticketService,
                             UsuarioRepository usuarioRepository) {
        this.gate = gate;
        this.ticketService = ticketService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/api/soporte/tickets")
    public ResponseEntity<TicketResponse> crearTicket(
            @Valid @RequestBody CrearTicketRequest request, Authentication authentication) {
        String dni = authentication.getName();
        TicketSoporte ticket = ticketService.crear(
                usuarioRepository.findByDni(dni).orElseThrow(),
                request.origenModulo(), request.asunto(), request.detalle());
        return ResponseEntity.created(URI.create("/api/soporte/tickets/" + ticket.getId()))
                .body(TicketResponse.from(ticket));
    }

    @GetMapping("/api/admin/tickets")
    public ResponseEntity<List<TicketResponse>> colaPorRol(Authentication authentication) {
        Admin admin = gate.adminAutenticado(authentication);
        return ResponseEntity.ok(ticketService.listarPorRol(admin.getRol())
                .stream().map(TicketResponse::from).toList());
    }

    /** Auditoría 2026-09-18 (gap del frontend): transición de estado del
     *  ticket ({@code abierto → en_proceso → resuelto → cerrado}) — antes no
     *  existía ningún endpoint de escritura más allá del alta. Solo el Admin
     *  del mismo rol asignado al ticket puede tocarlo (FR-ADM-008). */
    @PatchMapping("/api/admin/tickets/{ticketId}")
    public ResponseEntity<TicketResponse> actualizarEstado(
            @PathVariable UUID ticketId,
            @Valid @RequestBody ActualizarEstadoTicketRequest request,
            Authentication authentication) {
        Admin admin = gate.adminAutenticado(authentication);
        TicketSoporte ticket = ticketService.actualizarEstado(admin, ticketId, request.estado());
        return ResponseEntity.ok(TicketResponse.from(ticket));
    }
}