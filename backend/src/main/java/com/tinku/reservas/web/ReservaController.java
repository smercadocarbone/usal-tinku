package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.service.AdicionalResumen;
import com.tinku.reservas.service.ReservaService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
    private final AdicionalResumen adicionalResumen;

    public ReservaController(ReservaService reservaService, UsuarioActual usuarioActual,
                             AdicionalResumen adicionalResumen) {
        this.adicionalResumen = adicionalResumen;
        this.reservaService = reservaService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping
    public ResponseEntity<List<ReservaResponse>> listar(Authentication authentication) {
        return ResponseEntity.ok(reservaService.vistas(usuarioActual.obtener(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> obtener(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(reservaService.vista(usuarioActual.obtener(authentication), id));
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody NuevaReservaDirectaRequest request,
            Authentication authentication) {
        Usuario yo = usuarioActual.obtener(authentication);
        Reserva reserva = reservaService.crearDirecta(yo, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(reservaService.vista(yo, reserva.getId()));
    }

    @PostMapping("/{id}/reprogramar")
    public ResponseEntity<ReservaResponse> reprogramar(
            @PathVariable UUID id,
            @Valid @RequestBody ReprogramarReservaRequest request,
            Authentication authentication) {
        Usuario yo = usuarioActual.obtener(authentication);
        reservaService.reprogramar(yo, id, request.nuevoHorario());
        return ResponseEntity.ok(reservaService.vista(yo, id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelar(
            @PathVariable UUID id,
            Authentication authentication) {
        Usuario yo = usuarioActual.obtener(authentication);
        reservaService.cancelar(yo, id);
        return ResponseEntity.ok(reservaService.vista(yo, id));
    }

    public record AdicionalResumenResponse(boolean disponible, java.math.BigDecimal precio) {
    }

    /** T09: si este Tutor ofrece el resumen automático y a qué precio (para el checkbox al reservar). */
    @GetMapping("/adicional-resumen")
    public ResponseEntity<AdicionalResumenResponse> adicionalResumen(@RequestParam UUID tutorId) {
        return ResponseEntity.ok(new AdicionalResumenResponse(
                adicionalResumen.disponibleCon(tutorId), adicionalResumen.precio()));
    }
}
