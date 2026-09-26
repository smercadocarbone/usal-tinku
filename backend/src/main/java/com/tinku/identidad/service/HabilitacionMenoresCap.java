package com.tinku.identidad.service;

import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import com.tinku.reservas.port.VerificadorHabilitacionMenores;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * FR-ID-026 para M4 (puerto {@link VerificadorHabilitacionMenores}): CAP aprobado y con
 * {@code vence_at} de hoy en adelante. Aparte de {@code CertificadoService} a propósito:
 * ese servicio cancela reservas (PT10) y M4 consulta esto al reservar — juntos serían
 * un ciclo de dependencias.
 */
@Component
public class HabilitacionMenoresCap implements VerificadorHabilitacionMenores {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CertificadoAntecedentesPenalesRepository capRepo;
    private final UsuarioRepository usuarioRepo;

    public HabilitacionMenoresCap(CertificadoAntecedentesPenalesRepository capRepo, UsuarioRepository usuarioRepo) {
        this.capRepo = capRepo;
        this.usuarioRepo = usuarioRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean habilitadoParaMenores(UUID tutorId) {
        return habilitado(capRepo, usuarioRepo, tutorId);
    }

    /** CAP aprobado y vigente Y el Tutor acepta dar clases a menores (V41). */
    static boolean habilitado(CertificadoAntecedentesPenalesRepository capRepo, UsuarioRepository usuarioRepo,
                              UUID tutorId) {
        return usuarioRepo.existsByIdAndAceptaMenoresTrue(tutorId)
                && capRepo.existsByTutorIdAndEstadoAndVenceAtGreaterThanEqual(
                        tutorId, EstadoCap.APROBADO, LocalDate.now(ZONA));
    }
}
