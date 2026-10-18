package com.tinku.admin.evaluacion;

import com.tinku.shared.UsuarioActual;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** T-TES-11b §4.2: NPS in-app (autenticado; 404 con {@code tinku.evaluacion.habilitada=false}). */
@RestController
@RequestMapping("/api/evaluacion/nps")
public class NpsController {

    private final NpsService nps;
    private final UsuarioActual usuarioActual;

    public NpsController(NpsService nps, UsuarioActual usuarioActual) {
        this.nps = nps;
        this.usuarioActual = usuarioActual;
    }

    public record RespuestaNpsRequest(Integer puntaje) {
    }

    @GetMapping("/pendiente")
    public ResponseEntity<Map<String, Boolean>> pendiente(Authentication authentication) {
        return ResponseEntity.ok(Map.of("ofrecer", nps.ofrecer(usuarioActual.obtener(authentication))));
    }

    @PostMapping
    public ResponseEntity<Void> responder(@RequestBody RespuestaNpsRequest request, Authentication authentication) {
        nps.responder(usuarioActual.obtener(authentication), request.puntaje());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
