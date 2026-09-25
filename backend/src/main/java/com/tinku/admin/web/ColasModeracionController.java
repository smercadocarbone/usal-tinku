package com.tinku.admin.web;

import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.identidad.dto.CredencialResponse;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.port.ArchivoNoDisponibleException;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.service.CredencialService;
import com.tinku.identidad.service.PerfilPublicoTutorService;
import com.tinku.identidad.service.TutorNoEncontradoException;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.seguridad.web.AlertaSeguridadResponse;
import com.tinku.seguridad.web.DenunciaResponse;
import com.tinku.admin.AdminModeracionGate;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Colas del Admin de Moderación y Seguridad (US-1/2/3, FR-ADM-001/002/003).
 * Cada cola valida el rol contra {@code admin.admins} (T-M8-03) y se ordena por
 * plazo restante (T-M8-04), nunca por fecha de creación:
 *  - Credenciales: {@code ciclo_espera_hasta} ASC (nulls al final).
 *  - Alertas kill-switch: la ventana de 12hs arranca en {@code created_at} →
 *    la más vieja es la más urgente (mismo orden que {@code findByEstadoOrderByCreatedAtAsc}).
 *  - Denuncias: {@code prioridad_alta} primero, luego el SLA más cercano.
 *
 * Las RESOLUCIONES de Denuncias/Alertas viven en M9
 * ({@code /api/admin/moderacion/denuncias/{id}/resolver},
 * {@code /api/admin/moderacion/alertas-seguridad/{id}/resolver}); acá se listan.
 * La resolución de Credenciales (aprobar/rechazar) está en este mismo controller:
 * M1 expone las transiciones vía {@link CredencialService} (que dispara el
 * backoff escalado de FR-ID-012) y este panel las invoca como port (T-M8-01).
 */
@RestController
@RequestMapping("/api/admin/moderacion")
public class ColasModeracionController {

    private final AdminModeracionGate gate;
    private final CredencialAcademicaRepository credencialRepo;
    private final AlertaSeguridadRepository alertaRepo;
    private final DenunciaRepository denunciaRepo;
    private final CredencialService credencialService;
    private final Almacenamiento almacenamiento;
    private final PerfilPublicoTutorService perfilPublicoService;

    public ColasModeracionController(AdminModeracionGate gate,
                                     CredencialAcademicaRepository credencialRepo,
                                     AlertaSeguridadRepository alertaRepo,
                                     DenunciaRepository denunciaRepo,
                                     CredencialService credencialService,
                                     Almacenamiento almacenamiento,
                                     PerfilPublicoTutorService perfilPublicoService) {
        this.gate = gate;
        this.credencialRepo = credencialRepo;
        this.alertaRepo = alertaRepo;
        this.denunciaRepo = denunciaRepo;
        this.credencialService = credencialService;
        this.almacenamiento = almacenamiento;
        this.perfilPublicoService = perfilPublicoService;
    }

    /**
     * U1: moderación del perfil público de un Tutor — quita su bio y/o su foto
     * (p. ej. datos de contacto o contenido inapropiado). Auditado por el
     * {@code AuditoriaInterceptor} de {@code /api/admin/**}.
     */
    @DeleteMapping("/tutores/{tutorId}/bio")
    public ResponseEntity<Void> quitarBio(@PathVariable UUID tutorId, Authentication authentication) {
        gate.requiereModeracion(authentication);
        return moderarPerfil(tutorId, true, false);
    }

    @DeleteMapping("/tutores/{tutorId}/foto")
    public ResponseEntity<Void> quitarFoto(@PathVariable UUID tutorId, Authentication authentication) {
        gate.requiereModeracion(authentication);
        return moderarPerfil(tutorId, false, true);
    }

    private ResponseEntity<Void> moderarPerfil(UUID tutorId, boolean bio, boolean foto) {
        try {
            perfilPublicoService.moderar(tutorId, bio, foto);
        } catch (TutorNoEncontradoException e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/credenciales")
    public ResponseEntity<List<CredencialColaResponse>> credenciales(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return ResponseEntity.ok(credencialRepo.colaPendientes(EstadoCredencial.PENDIENTE)
                .stream().map(CredencialColaResponse::from).toList());
    }

    /**
     * El archivo de la Credencial, para que el Admin la revise antes de decidir
     * (AUD-007: hasta acá se aprobaba a ciegas, y la Credencial es la única
     * verificación que habilita el matching tras ADR-M1-02). Sirve BYTES, nunca la
     * referencia interna del almacenamiento (ADR-M1-03). Queda auditado por el
     * {@code AuditoriaInterceptor} de {@code /api/admin/**}.
     *
     * <p>El Content-Type sale de los magic bytes, no de lo que declaró el Tutor. Un
     * archivo fuera de la allowlist (subido antes de AUD-007) se entrega como
     * descarga opaca, nunca inline. {@code CSP: sandbox} + {@code nosniff} evitan que
     * un archivo hostil ejecute algo en el origen del panel.</p>
     */
    @GetMapping("/credenciales/{credencialId}/archivo")
    public ResponseEntity<byte[]> archivoCredencial(@PathVariable UUID credencialId,
                                                    Authentication authentication) {
        gate.requiereModeracion(authentication);
        CredencialAcademica credencial = credencialRepo.findById(credencialId).orElse(null);
        if (credencial == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] contenido;
        try {
            contenido = almacenamiento.leer(credencial.getArchivoUrl());
        } catch (ArchivoNoDisponibleException e) {
            return ResponseEntity.notFound().build();
        }
        var tipo = TipoArchivoCredencial.detectar(contenido);
        ContentDisposition disposicion = tipo.isPresent()
                ? ContentDisposition.inline().build()
                : ContentDisposition.attachment().filename("credencial-" + credencialId).build();
        return ResponseEntity.ok()
                .contentType(tipo.map(t -> MediaType.parseMediaType(t.getMediaType()))
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .body(contenido);
    }

    /** AUD-021: el clip de evidencia del kill-switch lo sirve Tinku (se subió como
     *  archivo), nunca un enlace externo. Mismos headers que el visor de credenciales. */
    @GetMapping("/alertas/{alertaId}/clip")
    public ResponseEntity<byte[]> clipAlerta(@PathVariable UUID alertaId, Authentication authentication) {
        gate.requiereModeracion(authentication);
        AlertaSeguridad alerta = alertaRepo.findById(alertaId).orElse(null);
        if (alerta == null || alerta.getClipUrl() == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] contenido;
        try {
            contenido = almacenamiento.leer(alerta.getClipUrl());
        } catch (ArchivoNoDisponibleException e) {
            return ResponseEntity.notFound().build();
        }
        boolean webm = contenido.length >= 4 && (contenido[0] & 0xFF) == 0x1A;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(webm ? "video/webm" : "video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noStore())
                .body(contenido);
    }

    @PostMapping("/credenciales/{credencialId}/resolver")
    public ResponseEntity<?> resolverCredencial(@PathVariable UUID credencialId,
                                                 @Valid @RequestBody RevisarCredencialRequest request,
                                                 Authentication authentication) {
        UUID adminId = gate.requiereModeracion(authentication);
        CredencialAcademica credencial = credencialRepo.findById(credencialId).orElse(null);
        if (credencial == null) {
            return ResponseEntity.notFound().build();
        }
        AdminModeracionGate.exigirNoEsParteDelCaso(adminId, credencial.getTutor().getId());
        // Solo una credencial PENDIENTE se resuelve (una ya resuelta no se re-toca).
        if (credencial.getEstado() != EstadoCredencial.PENDIENTE) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "La credencial no está pendiente de revisión."));
        }
        CredencialAcademica resuelta = request.decision() == DecisionCredencial.APROBAR
                ? credencialService.marcarAprobada(credencialId, adminId)
                : credencialService.marcarRechazada(credencialId, adminId);
        return ResponseEntity.ok(CredencialResponse.from(resuelta));
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<AlertaSeguridadResponse>> alertas(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return ResponseEntity.ok(alertaRepo.findByEstadoOrderByCreatedAtAsc(
                        AlertaSeguridad.ESTADO_PENDIENTE_REVISION)
                .stream().map(AlertaSeguridadResponse::from).toList());
    }

    @GetMapping("/denuncias")
    public ResponseEntity<List<DenunciaResponse>> denuncias(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return ResponseEntity.ok(denunciaRepo.findByEstadoOrderByPrioridadAltaDescSlaResolucionVenceAtAsc(
                        EstadoDenuncia.EN_REVISION)
                .stream().map(DenunciaResponse::from).toList());
    }
}