package com.tinku.reputacion.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reputacion.service.CalificacionService;
import com.tinku.shared.AdminModeracionGate;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Calificaciones del modulo M7: crear (T-M7-02), editar/eliminar dentro de la
 * ventana de 48hs (T-M7-06) y el listado de calificaciones ocultas para el
 * panel de Moderacion (T-M7-04, gate {@link AdminModeracionGate}).
 */
@RestController
@RequestMapping("/api")
public class CalificacionController {

    private final CalificacionService calificacionService;
    private final UsuarioActual usuarioActual;
    private final AdminModeracionGate adminModeracionGate;

    public CalificacionController(CalificacionService calificacionService,
                                  UsuarioActual usuarioActual,
                                  AdminModeracionGate adminModeracionGate) {
        this.calificacionService = calificacionService;
        this.usuarioActual = usuarioActual;
        this.adminModeracionGate = adminModeracionGate;
    }

    /** T-M7-02: el Tutor califica al estudiante (oculta) o el estudiante/AR al
     *  Tutor (publica). La direccion se deriva del rol, nunca del payload. */
    @PostMapping("/sesiones/{sesionId}/calificacion")
    public ResponseEntity<CalificacionResponse> calificar(
            @PathVariable UUID sesionId,
            @RequestBody CalificarRequest request,
            Authentication authentication) {
        Usuario autor = usuarioActual.obtener(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(calificacionService.calificar(autor, sesionId, request));
    }

    /** T-M7-06 / FR-REP-005: editar la propia calificacion publica (<= 48hs). */
    @PatchMapping("/calificaciones/{id}")
    public ResponseEntity<CalificacionResponse> editar(
            @PathVariable UUID id,
            @RequestBody CalificarRequest request,
            Authentication authentication) {
        Usuario autor = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(calificacionService.editar(autor, id, request));
    }

    /** T-M7-06 / FR-REP-005: eliminar la propia calificacion publica (<= 48hs). */
    @DeleteMapping("/calificaciones/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, Authentication authentication) {
        Usuario autor = usuarioActual.obtener(authentication);
        calificacionService.eliminar(autor, id);
        return ResponseEntity.noContent().build();
    }

    /** T-M7-04: ocultas (tutor_a_estudiante) de un estudiante, solo admin de
     *  Moderacion (AdminModeracionGate → 403 si no autorizado). */
    @GetMapping("/admin/moderacion/calificaciones-ocultas/{estudianteId}")
    public ResponseEntity<List<CalificacionOcultaResponse>> ocultas(
            @PathVariable UUID estudianteId,
            Authentication authentication) {
        adminModeracionGate.requiereModeracion(authentication);
        return ResponseEntity.ok(calificacionService.calificacionesOcultas(estudianteId));
    }
}