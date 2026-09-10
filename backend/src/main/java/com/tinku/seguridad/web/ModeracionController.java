package com.tinku.seguridad.web;

import com.tinku.seguridad.service.AlertaSeguridadService;
import com.tinku.seguridad.service.DenunciaService;
import com.tinku.shared.AdminModeracionGate;
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
 * Resolución de Denuncias y Alertas de Seguridad por el Admin de Moderación y
 * Seguridad (T-M9-04/T-M9-05). Autorización con {@link AdminModeracionGate}
 * (403 si el principal no está en el allowlist — hasta que M8 cree la tabla
 * {@code admins}); la identidad del Admin queda persistida en
 * {@code admin_resolutor_id}/{@code admin_id}. Tracks separados: el de Alerta
 * nunca espera descargo (FR-SEC-004).
 */
@RestController
@RequestMapping("/api/admin/moderacion")
public class ModeracionController {

    private final DenunciaService denunciaService;
    private final AlertaSeguridadService alertaSeguridadService;
    private final AdminModeracionGate moderacionGate;

    public ModeracionController(DenunciaService denunciaService,
                                AlertaSeguridadService alertaSeguridadService,
                                AdminModeracionGate moderacionGate) {
        this.denunciaService = denunciaService;
        this.alertaSeguridadService = alertaSeguridadService;
        this.moderacionGate = moderacionGate;
    }

    @PostMapping("/denuncias/{id}/resolver")
    public ResponseEntity<DenunciaResponse> resolverDenuncia(
            @PathVariable UUID id, @Valid @RequestBody ResolverDenunciaRequest request,
            Authentication authentication) {
        UUID adminId = moderacionGate.requiereModeracion(authentication);
        return ResponseEntity.ok(DenunciaResponse.from(denunciaService.resolver(
                id, adminId, request.resolucion(), request.tipoSancion(), request.diasSuspension())));
    }

    @PostMapping("/alertas-seguridad/{id}/resolver")
    public ResponseEntity<AlertaSeguridadResponse> resolverAlerta(
            @PathVariable UUID id, @Valid @RequestBody ResolverAlertaRequest request,
            Authentication authentication) {
        UUID adminId = moderacionGate.requiereModeracion(authentication);
        return ResponseEntity.ok(AlertaSeguridadResponse.from(alertaSeguridadService.resolver(
                id, adminId, request.decision(), request.tipoSancion(), request.diasSuspension())));
    }
}