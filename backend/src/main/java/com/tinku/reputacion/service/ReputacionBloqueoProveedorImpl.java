package com.tinku.reputacion.service;

import com.tinku.reputacion.repository.CalificacionRepository;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Implementacion real de {@link ReputacionBloqueoProveedor} (reemplaza el stub
 * de M4). FR-REP-006: un Tutor con al menos una Sesion finalizada sin su
 * calificacion publica queda bloqueado para nuevas reservas hasta que el
 * Estudiante la complete.
 */
@Service
public class ReputacionBloqueoProveedorImpl implements ReputacionBloqueoProveedor {

    private final CalificacionRepository calificacionRepo;

    public ReputacionBloqueoProveedorImpl(CalificacionRepository calificacionRepo) {
        this.calificacionRepo = calificacionRepo;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> tutoresConCalificacionPendiente() {
        return new HashSet<>(calificacionRepo.findAllTutoresConCalificacionPendiente());
    }
}