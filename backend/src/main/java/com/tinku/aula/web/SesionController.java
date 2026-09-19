package com.tinku.aula.web;

import com.tinku.aula.LiveKitService;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.SesionService;
import com.tinku.aula.jobs.CorteAutomaticoJob;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final LiveKitService liveKitService;
    private final UsuarioActual usuarioActual;

    public SesionController(SesionService sesionService,
                            LiveKitService liveKitService,
                            UsuarioActual usuarioActual) {
        this.sesionService = sesionService;
        this.liveKitService = liveKitService;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Resuelve la Sesión de una Reserva — lo que el frontend necesita para
     * armar el botón "Entrar a la clase" (link a {@code /aula/{sesionId}}) o
     * "Calificar" desde la pantalla de la Reserva, sin conocer de antemano el
     * id de la Sesión. 404 si la Reserva no existe o si todavía no se programó
     * la Sesión (ej. la Reserva no llegó a confirmarse); 403 para quien no sea
     * tutor, beneficiario o pagador de esa Reserva.
     */
    @GetMapping("/por-reserva/{reservaId}")
    public ResponseEntity<SesionResponse> porReserva(@PathVariable UUID reservaId,
                                                     Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        SesionAprendizaje sesion = sesionService.obtenerPorReserva(usuario, reservaId);
        return ResponseEntity.ok(SesionResponse.from(sesion));
    }

    /** Token de acceso a la sala LiveKit — solo participantes de la reserva. */
    @PostMapping("/{id}/token")
    public ResponseEntity<TokenSesionResponse> token(@PathVariable UUID id,
                                                     Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        String[] resultado = sesionService.obtenerToken(usuario, id);
        return ResponseEntity.ok(new TokenSesionResponse(
                resultado[0], liveKitService.getBaseUrl(), resultado[1]));
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