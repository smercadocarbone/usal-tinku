package com.tinku.identidad.web;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.ConsentimientoService;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** PT5 (T08): aceptación expresa y versionada de una cláusula de los Términos. */
@RestController
@RequestMapping("/api/usuarios/me/clausulas")
public class ClausulaController {

    private final ConsentimientoService consentimientoService;
    private final UsuarioActual usuarioActual;

    public ClausulaController(ConsentimientoService consentimientoService, UsuarioActual usuarioActual) {
        this.consentimientoService = consentimientoService;
        this.usuarioActual = usuarioActual;
    }

    public record ClausulaResponse(String clausula, String versionVigente, boolean aceptada) {
    }

    @GetMapping("/{clausula}")
    public ResponseEntity<ClausulaResponse> estado(@PathVariable String clausula, Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(new ClausulaResponse(clausula, consentimientoService.versionVigente(clausula),
                consentimientoService.haAceptado(usuario.getId(), clausula)));
    }

    @PostMapping("/{clausula}")
    public ResponseEntity<ClausulaResponse> aceptar(@PathVariable String clausula, Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        // ADR-M3-05: aceptar los Términos (p. ej. una versión nueva) incluye la grabación de solo
        // audio; un Menor nunca la acepta (Art. II: nunca hay grabación con menores).
        if (ConsentimientoService.TERMINOS.equals(clausula) && usuario.getTipo() != TipoUsuario.MENOR) {
            consentimientoService.aceptarTerminos(usuario.getId());
        } else {
            consentimientoService.aceptar(usuario.getId(), clausula);
        }
        return ResponseEntity.ok(new ClausulaResponse(clausula, consentimientoService.versionVigente(clausula), true));
    }
}
