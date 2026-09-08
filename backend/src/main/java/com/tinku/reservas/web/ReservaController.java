package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.service.ReservaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reserva directa sin Solicitud intermedia (FR-RES-001, T-M4-05; US-3/US-4).
 * La crea quien paga — Estudiante adulto para sí mismo o Adulto Responsable
 * por su menor — con el mismo patrón de autenticación que SolicitudController.
 */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaService reservaService;
    private final UsuarioRepository usuarioRepository;

    public ReservaController(ReservaService reservaService, UsuarioRepository usuarioRepository) {
        this.reservaService = reservaService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody NuevaReservaDirectaRequest request,
            Authentication authentication) {
        Reserva reserva = reservaService.crearDirecta(usuarioActual(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservaResponse.from(reserva));
    }

    private Usuario usuarioActual(Authentication authentication) {
        return usuarioRepository.findByDni(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }
}