package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.SolicitudService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Solicitud de Sesión del menor (US-2) y su aprobación por el Adulto Responsable
 * (US-3, crea la Reserva — T-M4-03/T-M4-04). Restricciones de Artículo II en los
 * servicios: el menor solo crea, el AR solo revisa/aprueba.
 */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final ReservaService reservaService;
    private final UsuarioActual usuarioActual;

    public SolicitudController(SolicitudService solicitudService,
                               ReservaService reservaService,
                               UsuarioActual usuarioActual) {
        this.solicitudService = solicitudService;
        this.reservaService = reservaService;
        this.usuarioActual = usuarioActual;
    }

    /** US-2: la genera la cuenta del menor. No bloquea horario ni genera cobro (FR-RES-021). */
    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(
            @Valid @RequestBody NuevaSolicitudRequest request,
            Authentication authentication) {
        SolicitudSesion solicitud = solicitudService.crear(
                usuarioActual.obtener(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(SolicitudResponse.from(solicitud));
    }

    /** US-3: las solicitudes pendientes de los menores a cargo del AR. */
    @GetMapping("/pendientes")
    public ResponseEntity<List<SolicitudResponse>> pendientes(Authentication authentication) {
        List<SolicitudSesion> pendientes = solicitudService.pendientesDelAdultoResponsable(
                usuarioActual.obtener(authentication));
        return ResponseEntity.ok(pendientes.stream().map(SolicitudResponse::from).toList());
    }

    /** US-3/US-4: aprueba el AR — crea la Reserva real (pendiente_pago) y dispara el cobro. */
    @PostMapping("/{id}/aprobar")
    public ResponseEntity<ReservaResponse> aprobar(
            @PathVariable UUID id,
            Authentication authentication) {
        var reserva = reservaService.aprobarSolicitud(usuarioActual.obtener(authentication), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservaResponse.from(reserva));
    }
}