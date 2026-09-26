package com.tinku.identidad.web;

import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.MarcarNoConfiableRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.AutorizacionService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Autorizaciones de Tutor (FR-ID-009, T-M1-11), endpoints autenticados: solo
 * la capacidad "Adulto Responsable" puede autorizar o marcar un Tutor como no
 * confiable (Artículo II).
 */
@RestController
@RequestMapping("/api/autorizaciones")
public class AutorizacionController {

    private final AutorizacionService autorizacionService;
    private final UsuarioActual usuarioActual;

    public AutorizacionController(AutorizacionService autorizacionService,
                                  UsuarioActual usuarioActual) {
        this.autorizacionService = autorizacionService;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> autorizarTutor(
            @Valid @RequestBody AutorizarTutorRequest request,
            Authentication authentication
    ) {
        Usuario adulto = usuarioActual.obtener(authentication);
        var autorizacion = autorizacionService.autorizarTutor(
                adulto, request.menorId(), request.tutorId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", autorizacion.getId(), "no_confiable", autorizacion.isNoConfiable()));
    }

    /** R5: Tutores autorizados para un menor (solo su Adulto Responsable). */
    @org.springframework.web.bind.annotation.GetMapping
    public java.util.List<AutorizacionService.AutorizacionVista> listar(
            @org.springframework.web.bind.annotation.RequestParam UUID menorId, Authentication authentication) {
        return autorizacionService.listar(usuarioActual.obtener(authentication), menorId);
    }

    @PatchMapping("/no-confiable")
    public ResponseEntity<Void> marcarNoConfiable(
            @Valid @RequestBody MarcarNoConfiableRequest request,
            Authentication authentication
    ) {
        Usuario adulto = usuarioActual.obtener(authentication);
        autorizacionService.marcarNoConfiable(adulto, request.tutorId(), request.noConfiable());
        return ResponseEntity.noContent().build();
    }
}
