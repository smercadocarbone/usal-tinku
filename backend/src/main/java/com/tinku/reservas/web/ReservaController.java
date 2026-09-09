package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.service.ReservaService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Reserva directa sin Solicitud intermedia (FR-RES-001, T-M4-05; US-3/US-4).
 * La crea quien paga — Estudiante adulto para sí mismo o Adulto Responsable
 * por su menor — con el mismo patrón de autenticación que SolicitudController.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;
    private final UsuarioActual usuarioActual;

    public ReservaController(ReservaService reservaService, UsuarioActual usuarioActual) {
        this.reservaService = reservaService;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody NuevaReservaDirectaRequest request,
            Authentication authentication) {
        Reserva reserva = reservaService.crearDirecta(usuarioActual.obtener(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservaResponse.from(reserva));
    }

    @PostMapping("/{id}/reprogramar")
    public ResponseEntity<ReservaResponse> reprogramar(
            @PathVariable UUID id,
            @Valid @RequestBody ReprogramarReservaRequest request,
            Authentication authentication) {
        Reserva reserva = reservaService.reprogramar(usuarioActual.obtener(authentication), id, request.nuevoHorario());
        return ResponseEntity.ok(ReservaResponse.from(reserva));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelar(
            @PathVariable UUID id,
            Authentication authentication) {
        Reserva reserva = reservaService.cancelar(usuarioActual.obtener(authentication), id);
        return ResponseEntity.ok(ReservaResponse.from(reserva));
    }
}