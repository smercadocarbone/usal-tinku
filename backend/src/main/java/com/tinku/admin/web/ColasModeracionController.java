package com.tinku.admin.web;

import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.seguridad.web.AlertaSeguridadResponse;
import com.tinku.seguridad.web.DenunciaResponse;
import com.tinku.shared.AdminModeracionGate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Colas del Admin de Moderación y Seguridad (US-1/2/3, FR-ADM-001/002/003).
 * Cada cola valida el rol contra {@code admin.admins} (T-M8-03) y se ordena por
 * plazo restante (T-M8-04), nunca por fecha de creación:
 *  - Credenciales: {@code ciclo_espera_hasta} ASC (nulls al final).
 *  - Alertas kill-switch: la ventana de 12hs arranca en {@code created_at} →
 *    la más vieja es la más urgente (mismo orden que {@code findByEstadoOrderByCreatedAtAsc}).
 *  - Denuncias: {@code prioridad_alta} primero, luego el SLA más cercano.
 *
 * Las RESOLUCIONES ya viven en M9 ({@code /api/admin/moderacion/denuncias/{id}/resolver},
 * {@code /api/admin/moderacion/alertas-seguridad/{id}/resolver}) — acá solo se listan.
 */
@RestController
@RequestMapping("/api/admin/moderacion")
public class ColasModeracionController {

    private final AdminModeracionGate gate;
    private final CredencialAcademicaRepository credencialRepo;
    private final AlertaSeguridadRepository alertaRepo;
    private final DenunciaRepository denunciaRepo;

    public ColasModeracionController(AdminModeracionGate gate,
                                     CredencialAcademicaRepository credencialRepo,
                                     AlertaSeguridadRepository alertaRepo,
                                     DenunciaRepository denunciaRepo) {
        this.gate = gate;
        this.credencialRepo = credencialRepo;
        this.alertaRepo = alertaRepo;
        this.denunciaRepo = denunciaRepo;
    }

    @GetMapping("/credenciales")
    public ResponseEntity<List<CredencialColaResponse>> credenciales(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return ResponseEntity.ok(credencialRepo.colaPendientes(EstadoCredencial.PENDIENTE)
                .stream().map(CredencialColaResponse::from).toList());
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