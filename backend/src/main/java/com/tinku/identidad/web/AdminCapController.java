package com.tinku.identidad.web;

import com.tinku.admin.AdminModeracionGate;
import com.tinku.identidad.dto.CapColaResponse;
import com.tinku.identidad.dto.CapResponse;
import com.tinku.identidad.dto.RevisarCapRequest;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.port.ArchivoNoDisponibleException;
import com.tinku.identidad.service.CertificadoService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 * Cola y revisión del CAP para el Admin de Moderación y Seguridad (T02, FR-ID-022 a 024).
 * Cada método exige el rol ANTES de hacer nada (TR3: la versión retirada no lo hacía y
 * cualquier usuario podía aprobar su propio CAP). Está bajo {@code /api/admin/**}: el
 * {@code AuditoriaInterceptor} la audita solo.
 */
@RestController
@RequestMapping("/api/admin/moderacion/antecedentes-penales")
public class AdminCapController {

    private final CertificadoService certificadoService;
    private final AdminModeracionGate gate;
    private final Almacenamiento almacenamiento;

    public AdminCapController(CertificadoService certificadoService, AdminModeracionGate gate,
                              Almacenamiento almacenamiento) {
        this.certificadoService = certificadoService;
        this.gate = gate;
        this.almacenamiento = almacenamiento;
    }

    @GetMapping
    public ResponseEntity<List<CapColaResponse>> cola(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return ResponseEntity.ok(certificadoService.colaModeracion().stream().map(CapColaResponse::from).toList());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CapResponse> revisar(@PathVariable UUID id,
                                               @Valid @RequestBody RevisarCapRequest request,
                                               Authentication authentication) {
        UUID adminId = gate.requiereModeracion(authentication);
        CertificadoAntecedentesPenales cap = certificadoService.revisar(
                id, adminId, request.accion(), request.categoria());
        return ResponseEntity.ok(CapResponse.from(cap));
    }

    /** Visor del PDF, mismo patrón y headers que el de credenciales (AUD-007). */
    @GetMapping("/{id}/archivo")
    public ResponseEntity<byte[]> archivo(@PathVariable UUID id, Authentication authentication) {
        gate.requiereModeracion(authentication);
        CertificadoAntecedentesPenales cap = certificadoService.obtener(id);
        byte[] contenido;
        try {
            contenido = almacenamiento.leer(cap.getArchivoUrl());
        } catch (ArchivoNoDisponibleException e) {
            return ResponseEntity.notFound().build();
        }
        var tipo = TipoArchivoCredencial.detectar(contenido);
        ContentDisposition disposicion = tipo.isPresent()
                ? ContentDisposition.inline().build()
                : ContentDisposition.attachment().filename("cap-" + id).build();
        return ResponseEntity.ok()
                .contentType(tipo.map(t -> MediaType.parseMediaType(t.getMediaType()))
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .body(contenido);
    }
}
