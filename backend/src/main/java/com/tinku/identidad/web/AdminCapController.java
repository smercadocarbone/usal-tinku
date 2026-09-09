package com.tinku.identidad.web;

import com.tinku.identidad.dto.CapResponse;
import com.tinku.identidad.dto.RevisarCapRequest;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.service.CertificadoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Revisión del Certificado de Antecedentes Penales para el panel ABMIN de M8
 * (T-M1-16). Aprobar / rechazar (BR-CAP-01, FR-ID-023) / marcar
 * {@code en_revision_legal} (BR-CAP-02, FR-ID-024).
 *
 * IMPORTANTE (scope M1-F): estos endpoints son solo {@code authenticated()}
 * por ahora. M8 debe cerrarlos con rol ADMIN (tabla `admins`, separada de
 * `usuarios`, sin compartir JWT — ver SecurityConfig). Hasta entonces, la
 * identidad del revisor se deduce del principal (UUID si aplica) — ver
 * NOTAS_VERIFICACION.md de M1-F.
 */
@RestController
@RequestMapping("/api/admin/moderacion/antecedentes-penales")
public class AdminCapController {

    private final CertificadoService certificadoService;

    public AdminCapController(CertificadoService certificadoService) {
        this.certificadoService = certificadoService;
    }

    @GetMapping
    public ResponseEntity<List<CapResponse>> cola() {
        return ResponseEntity.ok(certificadoService.colaModeracion().stream().map(CapResponse::from).toList());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CapResponse> revisar(
            @PathVariable UUID id,
            @Valid @RequestBody RevisarCapRequest request,
            Authentication authentication
    ) {
        CertificadoAntecedentesPenales cap = certificadoService.revisar(
                id, adminId(authentication), request.accion(), request.categoriaAntecedente());
        return ResponseEntity.ok(CapResponse.from(cap));
    }

    private UUID adminId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            // M8 proveerá la identidad real del Admin (UUID); hoy puede venir un dni.
            return null;
        }
    }
}
