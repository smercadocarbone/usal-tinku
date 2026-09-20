package com.tinku.seguridad.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.seguridad.service.DenunciaService;
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
 * Denuncias estándar del lado del usuario (T-M9-02/T-M9-04). El rechazo del
 * menor es a nivel de autorización (403) en el servicio: un menor con su token
 * no puede presentar una Denuncia aunque el frontend se la oculte (FR-SEC-001,
 * Artículo II).
 */
@RestController
@RequestMapping("/api/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;
    private final UsuarioActual usuarioActual;

    public DenunciaController(DenunciaService denunciaService, UsuarioActual usuarioActual) {
        this.denunciaService = denunciaService;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping
    public ResponseEntity<DenunciaResponse> presentar(
            @Valid @RequestBody PresentarDenunciaRequest request, Authentication authentication) {
        Usuario denunciante = usuarioActual.obtener(authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(DenunciaResponse.from(
                denunciaService.presentar(denunciante, request.denunciadoId(), request.sesionId(),
                        request.motivo(), request.evidenciaUrl())));
    }

    /** Auditoría 2026-09-20: recibidas por el propio denunciado autenticado. */
    @GetMapping("/recibidas")
    public ResponseEntity<List<DenunciaResponse>> recibidas(Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(denunciaService.misDenunciasRecibidas(usuario).stream()
                .map(DenunciaResponse::from).toList());
    }

    @PostMapping("/{id}/descargo")
    public ResponseEntity<DenunciaResponse> descargar(
            @PathVariable UUID id, @Valid @RequestBody DescargoRequest request,
            Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(DenunciaResponse.from(
                denunciaService.descargar(id, usuario, request.descargo())));
    }
}