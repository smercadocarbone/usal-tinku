package com.tinku.identidad.service;

import com.tinku.identidad.dto.AccionRevisionCap;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Carga, revisión y vencimiento del Certificado de Antecedentes Penales (CAP)
 * de un Tutor (US-6, T-M1-15/16/17).
 *
 *  - Carga (T-M1-15): solo Tutor, mismo backoff escalado que credenciales
 *    (FR-ID-021 reutiliza FR-ID-012 → {@code CredencialBackoffService}).
 *  - Revisión (T-M1-16, usada por el panel Admin de M8): aprobar / rechazar
 *    por BR-CAP-01 (FR-ID-023) / a {@code en_revision_legal} por BR-CAP-02
 *    (FR-ID-024, nunca auto-resuelto).
 *  - Vencimiento (T-M1-17): job de Quartz diario marca {@code vencido} los CAP
 *    con {@code venceAt < hoy} y suspende {@code activoParaMatching} del Tutor
 *    (FR-ID-025).
 */
@Service
public class CertificadoService {

    private static final int MAX_INTENTOS_CICLO = 3;
    private static final int MESES_VIGENCIA = 12; // FR-ID-025, Tabla_Tiempos

    private final CertificadoAntecedentesPenalesRepository capRepo;
    private final UsuarioRepository usuarioRepo;
    private final CredencialBackoffService backoffService;

    public CertificadoService(CertificadoAntecedentesPenalesRepository capRepo,
                              UsuarioRepository usuarioRepo,
                              CredencialBackoffService backoffService) {
        this.capRepo = capRepo;
        this.usuarioRepo = usuarioRepo;
        this.backoffService = backoffService;
    }

    /** Carga un CAP PENDIENTE. Solo Tutor, respetando el backoff compartido con credenciales. */
    @Transactional
    public CertificadoAntecedentesPenales cargarCap(Usuario tutor, String archivoUrl, LocalDate fechaEmision) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new IllegalArgumentException("Solo un perfil de Tutor puede cargar un certificado de antecedentes.");
        }

        backoffService.chequearPuedeIntentar(tutor.getId()); // FR-ID-021 reuse FR-ID-012

        capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE)
                .ifPresent(c -> { throw new YaExisteCredencialPendienteException(); });

        CertificadoAntecedentesPenales cap = new CertificadoAntecedentesPenales();
        cap.setTutor(tutor);
        cap.setArchivoUrl(archivoUrl);
        cap.setFechaEmision(fechaEmision);
        cap.setVenceAt(fechaEmision.plusMonths(MESES_VIGENCIA)); // FR-ID-025
        cap.setEstado(EstadoCap.PENDIENTE);
        cap.setNumeroIntento(numeroDeIntentoParaCiclo(tutor.getId()));
        return capRepo.save(cap);
    }

    private int numeroDeIntentoParaCiclo(UUID tutorId) {
        return capRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutorId)
                .filter(c -> c.getEstado() == EstadoCap.RECHAZADO
                        && c.getNumeroIntento() < MAX_INTENTOS_CICLO)
                .map(c -> c.getNumeroIntento() + 1)
                .orElse(1);
    }

    /**
     * Revisión manual del CAP (invocada por el panel admin de M8). Aprobar
     * habilita matching; rechazar por BR-CAP-01 no admite apelación en producto;
     * {@code en_revision_legal} (BR-CAP-02) nunca se auto-resuelve.
     */
    @Transactional
    public CertificadoAntecedentesPenales revisar(UUID capId, UUID adminRevisorId,
                                                  AccionRevisionCap accion, String categoriaAntecedente) {
        CertificadoAntecedentesPenales cap = capRepo.findById(capId)
                .orElseThrow(CapNoEncontradoException::new);
        cap.setAdminRevisorId(adminRevisorId);
        cap.setRevisadoAt(Instant.now());

        switch (accion) {
            case APROBAR -> {
                cap.setEstado(EstadoCap.APROBADO);
                // Habilita matching mientras el CAP esté vigente (FR-ID-025).
                Usuario tutor = cap.getTutor();
                tutor.setActivoParaMatching(true);
                usuarioRepo.save(tutor);
            }
            case RECHAZAR -> {
                // BR-CAP-01: rechazo automático, sin excepción ni apelación en producto (FR-ID-023).
                cap.setEstado(EstadoCap.RECHAZADO);
                cap.setCategoriaAntecedente(categoriaAntecedente);
                if (cap.getNumeroIntento() >= MAX_INTENTOS_CICLO) {
                    backoffService.registrarCicloAgotado(cap.getTutor().getId()); // FR-ID-021/012
                }
            }
            case EN_REVISION_LEGAL -> {
                // BR-CAP-02: fuera de la lista o proceso en trámite — decisión manual, no auto-resuelto (FR-ID-024).
                cap.setEstado(EstadoCap.EN_REVISION_LEGAL);
                cap.setTieneAntecedentes(true);
                cap.setCategoriaAntecedente(categoriaAntecedente);
            }
        }
        return capRepo.save(cap);
    }

    /** Cola de moderación de M8: pendientes + en revisión legal. */
    @Transactional
    public List<CertificadoAntecedentesPenales> colaModeracion() {
        return capRepo.findByEstadoInOrderByCreatedAtAsc(
                List.of(EstadoCap.PENDIENTE, EstadoCap.EN_REVISION_LEGAL));
    }

    /**
     * Vencimiento (FR-ID-025) — disparado por el job de Quartz {@code CapVencimientoJob}.
     * Marca {@code VENCIDO} los CAP no vencidos cuya fecha venció y suspende
     * {@code activoParaMatching}. Idempotente.
     * @return cantidad de CAPs vencidos.
     */
    @Transactional
    public int marcarVencidos() {
        List<CertificadoAntecedentesPenales> vencidos = capRepo.findByEstadoInAndVenceAtBefore(
                List.of(EstadoCap.PENDIENTE, EstadoCap.APROBADO, EstadoCap.EN_REVISION_LEGAL),
                LocalDate.now());

        for (CertificadoAntecedentesPenales cap : vencidos) {
            cap.setEstado(EstadoCap.VENCIDO);
            Usuario tutor = cap.getTutor();
            if (tutor.isActivoParaMatching()) {
                tutor.setActivoParaMatching(false);
                usuarioRepo.save(tutor);
            }
            capRepo.save(cap);
        }
        return vencidos.size();
    }
}
