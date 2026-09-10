package com.tinku.seguridad.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.seguridad.service.AlertaSeguridadService;
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
 * Descargo del Tutor detectado sobre su Alerta de kill-switch (vía de
 * apelación, US-2) — sólo él (403). La RESOLUCIÓN es del Admin (ver
 * {@link ModeracionController}); este endpoint jamás bloquea el track de 12hs.
 */
@RestController
@RequestMapping("/api/alertas-seguridad")
public class AlertasSeguridadController {

    private final AlertaSeguridadService alertaSeguridadService;
    private final UsuarioActual usuarioActual;

    public AlertasSeguridadController(AlertaSeguridadService alertaSeguridadService,
                                      UsuarioActual usuarioActual) {
        this.alertaSeguridadService = alertaSeguridadService;
        this.usuarioActual = usuarioActual;
    }

    @PostMapping("/{id}/descargo")
    public ResponseEntity<AlertaSeguridadResponse> descargar(
            @PathVariable UUID id, @Valid @RequestBody DescargoRequest request,
            Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(AlertaSeguridadResponse.from(
                alertaSeguridadService.descargar(id, usuario, request.descargo())));
    }
}