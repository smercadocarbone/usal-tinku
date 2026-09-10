package com.tinku.aula.web;

import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.SesionService;
import com.tinku.aula.jobs.CorteAutomaticoJob;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sesiones de Aprendizaje (M3). US-8: «Finalizar» la cierra a mano — lo
 * autoriza la participante (tutor, beneficiario o pagador de la Reserva; 403
 * para cualquier tercero, Artículo II incluido). El cierre automático a
 * T-fin+5min lo hace {@link CorteAutomaticoJob}. El kill-switch (T-M3-07), su
 * confirmación en la rama adultos (T-M3-09) y la subida de evidencia (T-M3-08)
 * también viven acá — la rama la decide el backend, el request nunca la trae.
 */
@RestController
@RequestMapping("/api/sesiones")
public class SesionController {

    private final SesionService sesionService;
    private final UsuarioActual usuarioActual;

    public SesionController(SesionService sesionService, UsuarioActual usuarioActual) {
        this.sesionService = sesionService;
        this.usuarioActual = usuarioActual;
    }

    /** US-8 — botón «Finalizar» de cualquiera de las partes. */
    @PostMapping("/{id}/finalizar")
    public ResponseEntity<SesionResponse> finalizar(@PathVariable UUID id,
                                                    Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        SesionAprendizaje sesion = sesionService.finalizar(usuario, id);
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** US-6/US-7 — disparo del kill-switch (T-M3-07). La rama (menor/adultos)
     *  la decide el backend con datos de M1; el body solo trae {@code
     *  detectadoId} (quién generó la detección). Intentar forzar la rama desde
     *  el cliente (ej. {@code rama: "adultos"}) se ignora. */
    @PostMapping("/{id}/killswitch")
    public ResponseEntity<SesionResponse> killswitch(@PathVariable UUID id,
                                                     @Valid @RequestBody KillswitchRequest request,
                                                     Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        SesionAprendizaje sesion = sesionService.ejecutarKillswitch(usuario, id, request.detectadoId());
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** US-7 — confirmación de la rama adultos (T-M3-09): sí/no del OTRO participante. */
    @PostMapping("/{id}/killswitch/confirmacion")
    public ResponseEntity<SesionResponse> confirmarKillswitch(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmacionKillswitchRequest request,
            Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        SesionAprendizaje sesion =
                sesionService.confirmarRamaAdultos(usuario, id, request.vio());
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** US-6/US-7 — subida de la evidencia del kill-switch (T-M3-08): solo la
     *  referencia al clip de 30s (Artículo V). 404 si no hay kill-switch
     *  registrado para la sesión. */
    @PostMapping("/{id}/evidencia")
    public ResponseEntity<EvidenciaResponse> evidencia(@PathVariable UUID id,
                                                       @Valid @RequestBody EvidenciaRequest request,
                                                       Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        AlertaSeguridad alerta = sesionService.subirEvidencia(
                usuario, id, request.clipUrl(), request.duracionSegundos());
        return ResponseEntity.ok(EvidenciaResponse.from(alerta));
    }
}