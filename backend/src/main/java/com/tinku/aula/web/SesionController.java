package com.tinku.aula.web;

import com.tinku.aula.SesionService;
import com.tinku.aula.jobs.CorteAutomaticoJob;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sesiones de Aprendizaje (M3). US-8: «Finalizar» la cierra a mano — lo
 * autoriza la participante (tutor, beneficiario o pagador de la Reserva; 403
 * para cualquier tercero, Artículo II incluido). El cierre automático a
 * T-fin+5min lo hace {@link CorteAutomaticoJob}. Cuando llegue el frontend de
 * M3 se agregarán {@code POST /api/sesiones/{id}/token} y el resto del Plan §4.
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
}