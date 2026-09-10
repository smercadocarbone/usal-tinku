package com.tinku.identidad.service;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Carga y ciclo de vida de una Credencial Académica (US-4, T-M1-10).
 *
 *  - El Tutor sube una credencial → {@code PENDIENTE} ({"@link #cargarCredencial}).
 *  - El panel de Admin (M8) la aprueba o rechaza ({@link #marcarAprobada} /
 *    {@link #marcarRechazada}). Estas transiciones de estado viven acá para
 *    que M8 las invoque como port, y son las que disparan el backoff escalado
 *    de {@code CredencialBackoffService} (FR-ID-012).
 *  - FR-ID-008: hasta 3 intentos por ciclo. Al rechazar el 3ro, el Tutor entra
 *    en espera escalada (24hs * 2^n) y el ciclo siguiente arranca en intento 1.
 *
 *  Nota (decisión de producto): la Credencial aprobada es la ÚNICA verificación
 *  que habilita el matching del Tutor; la función de Certificado de Antecedentes
 *  Penales fue retirada del onboarding y ya no existe en el código (las tablas
 *  de la migración V6 quedan en la BD, sin uso).
 */
@Service
public class CredencialService {

    private static final int MAX_INTENTOS_CICLO = CicloIntentos.MAX;

    private final CredencialAcademicaRepository credencialRepo;
    private final CredencialBackoffService backoffService;
    private final UsuarioRepository usuarioRepo;

    public CredencialService(CredencialAcademicaRepository credencialRepo,
                             CredencialBackoffService backoffService,
                             UsuarioRepository usuarioRepo) {
        this.credencialRepo = credencialRepo;
        this.backoffService = backoffService;
        this.usuarioRepo = usuarioRepo;
    }

    /**
     * Carga una credencial PENDIENTE para el Tutor. Antes valida: es Tutor,
     * no está en backoff (FR-ID-012), y no tiene ya una PENDIENTE en revisión.
     */
    @Transactional
    public CredencialAcademica cargarCredencial(Usuario tutor, TipoCredencial tipo, String archivoUrl) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new IllegalArgumentException("Solo un perfil de Tutor puede cargar credenciales.");
        }

        backoffService.chequearPuedeIntentar(tutor.getId()); // FR-ID-012

        credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE)
                .ifPresent(c -> { throw new YaExisteCredencialPendienteException(); });

        CredencialAcademica credencial = new CredencialAcademica();
        credencial.setTutor(tutor);
        credencial.setTipoDocumento(tipo);
        credencial.setArchivoUrl(archivoUrl);
        credencial.setEstado(EstadoCredencial.PENDIENTE);
        credencial.setNumeroIntento(numeroDeIntentoParaCiclo(tutor.getId()));
        return credencialRepo.save(credencial);
    }

    /** El intento dentro del ciclo actual: 1 si el ciclo arranca, o el siguiente tras un rechazo. */
    private int numeroDeIntentoParaCiclo(UUID tutorId) {
        return CicloIntentos.siguiente(
                credencialRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutorId),
                c -> c.getEstado() == EstadoCredencial.RECHAZADO,
                CredencialAcademica::getNumeroIntento);
    }

    /** Transición PENDIENTE → APROBADO (invocada por el panel Admin, M8).
     * Con el CAP retirado del onboarding, la Credencial aprobada es la que
     * habilita el matching del Tutor (antes lo hacía el CAP aprobado). */
    @Transactional
    public CredencialAcademica marcarAprobada(UUID credencialId, UUID adminRevisorId) {
        CredencialAcademica c = credencialO(credencialId);
        c.setEstado(EstadoCredencial.APROBADO);
        c.setAdminRevisorId(adminRevisorId);
        c.setRevisadoAt(Instant.now());
        CredencialAcademica guardada = credencialRepo.save(c);

        Usuario tutor = c.getTutor();
        if (!tutor.isActivoParaMatching()) {
            tutor.setActivoParaMatching(true);
            usuarioRepo.save(tutor);
        }
        return guardada;
    }

    /**
     * Transición PENDIENTE → RECHAZADO (invocada por el panel Admin, M8).
     * Si era el 3er intento del ciclo, dispara el backoff escalado (FR-ID-012)
     * y el próximo ciclo arranca de nuevo en intento 1.
     */
    @Transactional
    public CredencialAcademica marcarRechazada(UUID credencialId, UUID adminRevisorId) {
        CredencialAcademica c = credencialO(credencialId);
        c.setEstado(EstadoCredencial.RECHAZADO);
        c.setAdminRevisorId(adminRevisorId);
        c.setRevisadoAt(Instant.now());
        CredencialAcademica guardada = credencialRepo.save(c);

        if (guardada.getNumeroIntento() >= MAX_INTENTOS_CICLO) {
            backoffService.registrarCicloAgotado(guardada.getTutor().getId()); // FR-ID-012
        }
        return guardada;
    }

    private CredencialAcademica credencialO(UUID credencialId) {
        return credencialRepo.findById(credencialId)
                .orElseThrow(CredencialNoEncontradaException::new);
    }
}
