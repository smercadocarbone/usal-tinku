package com.tinku.matching;

import com.tinku.identidad.dto.MateriasNivel;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carga del perfil de matching del Tutor (US-5, FR-MATCH-006): el Tutor elige
 * sus materias/niveles SOLO del catálogo cerrado. Autenticado por JWT y solo
 * para perfiles TUTOR ({@link PerfilMatchingService}).
 */
@RestController
@RequestMapping("/api/perfil-matching")
public class PerfilMatchingController {

    private final PerfilMatchingService perfilMatchingService;
    private final UsuarioActual usuarioActual;

    public PerfilMatchingController(PerfilMatchingService perfilMatchingService,
                                    UsuarioActual usuarioActual) {
        this.perfilMatchingService = perfilMatchingService;
        this.usuarioActual = usuarioActual;
    }

    @PutMapping
    public ResponseEntity<MateriasNivel> cargar(
            @Valid @RequestBody CargarPerfilMatchingRequest request,
            Authentication authentication
    ) {
        Usuario tutor = usuarioActual.obtener(authentication);
        MateriasNivel perfil = perfilMatchingService.cargarPerfil(
                tutor, request.nivel(), request.materias());
        return ResponseEntity.ok(perfil);
    }
}