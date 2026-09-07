package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.service.FranjaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Publicación de disponibilidad del Tutor (US-1, FR-RES-012; T-M4-02).
 * Autenticado por JWT (SecurityConfig); la validación de perfil TUTOR vive en
 * FranjaService, no en el controller.
 */
@RestController
@RequestMapping("/api/tutores")
public class FranjaController {

    private final FranjaService franjaService;
    private final UsuarioRepository usuarioRepository;

    public FranjaController(FranjaService franjaService, UsuarioRepository usuarioRepository) {
        this.franjaService = franjaService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/franjas")
    public ResponseEntity<FranjaResponse> publicar(
            @Valid @RequestBody PublicarFranjaRequest request,
            Authentication authentication) {
        Usuario tutor = usuarioRepository.findByDni(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FranjaResponse.from(franjaService.publicar(tutor, request)));
    }
}