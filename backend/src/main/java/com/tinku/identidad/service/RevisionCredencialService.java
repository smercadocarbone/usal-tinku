package com.tinku.identidad.service;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.MetodoVerificacion;
import com.tinku.identidad.model.MotivoRechazoCredencial;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Decisión del Admin sobre una credencial con ADR-M1-06 encendido: aprobar indicando cómo verificó
 * y qué especialidades cubre, o rechazar con motivo. Las transiciones las sigue haciendo
 * {@link CredencialService} (backoff, matching, aviso).
 */
@Service
public class RevisionCredencialService {

    private static final int MAX_DETALLE = 500;

    private final CredencialAcademicaRepository credencialRepo;
    private final UsuarioRepository usuarioRepo;
    private final EspecialidadService especialidades;
    private final CredencialService credencialService;

    public RevisionCredencialService(CredencialAcademicaRepository credencialRepo, UsuarioRepository usuarioRepo,
                                     EspecialidadService especialidades, CredencialService credencialService) {
        this.credencialRepo = credencialRepo;
        this.usuarioRepo = usuarioRepo;
        this.especialidades = especialidades;
        this.credencialService = credencialService;
    }

    @Transactional
    public CredencialAcademica aprobar(UUID credencialId, UUID adminId, MetodoVerificacion metodo,
                                       Collection<UUID> especialidadesCubiertas) {
        if (metodo == null) {
            throw new RevisionCredencialInvalidaException("Indicá cómo verificaste la credencial: registro "
                    + "oficial o solo documento.");
        }
        CredencialAcademica c = credencialRepo.findById(credencialId).orElseThrow(CredencialNoEncontradaException::new);
        usuarioRepo.bloquearPorId(c.getTutor().getId()); // el Tutor no cambia sus especialidades en el medio
        Set<UUID> cubiertas = especialidades.validarCobertura(c, especialidadesCubiertas);
        return credencialService.marcarAprobada(credencialId, adminId, metodo, cubiertas);
    }

    @Transactional
    public CredencialAcademica rechazar(UUID credencialId, UUID adminId, MotivoRechazoCredencial motivo,
                                        String detalle) {
        if (motivo == null) {
            throw new RevisionCredencialInvalidaException("Elegí el motivo del rechazo: el Tutor lo va a ver.");
        }
        String d = detalle == null || detalle.isBlank() ? null : detalle.strip();
        if (d == null && motivo == MotivoRechazoCredencial.OTRO) {
            throw new RevisionCredencialInvalidaException("Contá el motivo del rechazo: el Tutor lo va a ver.");
        }
        if (d != null && d.length() > MAX_DETALLE) {
            throw new RevisionCredencialInvalidaException("El detalle puede tener hasta " + MAX_DETALLE + " caracteres.");
        }
        return credencialService.marcarRechazada(credencialId, adminId, motivo, d);
    }
}
