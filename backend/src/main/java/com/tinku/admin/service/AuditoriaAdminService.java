package com.tinku.admin.service;

import com.tinku.admin.model.Admin;
import com.tinku.admin.model.LogAuditoriaAdmin;
import com.tinku.admin.repository.LogAuditoriaAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Único punto de escritura del {@code log_auditoria_admin} (US-6/FR-ADM-005).
 * El interceptor de auditoría (T-M8-02) lo invoca para TODA request 2xx sobre
 * {@code /api/admin/**} — ningún endpoint del módulo se escribe sin auditoría
 * desde el día 1.
 *
 * {@code REQUIRES_NEW}: la fila de auditoría se commitea independientemente de
 * la transacción de negocio (un rollback del negocio no borra el registro; una
 * falla del registro nunca tumba la acción ya completada). El método no lanza:
 * un problema de auditoría se loguea, no rompe el request ya ejecutado.
 */
@Service
public class AuditoriaAdminService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaAdminService.class);

    private final LogAuditoriaAdminRepository repo;

    public AuditoriaAdminService(LogAuditoriaAdminRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Admin admin, String accion, String entidadTipo,
                          String entidadId, Map<String, String> detalle) {
        try {
            repo.save(new LogAuditoriaAdmin(admin.getId(), accion, entidadTipo, entidadId, detalle));
        } catch (RuntimeException e) {
            log.error("No se pudo auditar la accion del admin {} ({}) — accion={}, entidad={}/{}",
                    admin.getId(), admin.getRol(), accion, entidadTipo, entidadId, e);
        }
    }
}