package com.tinku.pagos.service;

import com.tinku.pagos.model.EstadoPasarela;
import com.tinku.pagos.repository.PasarelaEstadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Flag global de pasarela (modo Bypass, V22). M5 lo consulta en cada punto de
 * contacto con MercadoPago (generarPreferencia, liberación, reembolsos) SIEMPRE
 * desde base — nunca cacheado en memoria (un cambio de M8 debe ser efectivo de
 * inmediato). Sin fila (DB migrada a medias) el default es {@code habilitada=true}
 * (fail-closed hacia cobro real).
 */
@Service
public class PasarelaService {

    private static final short FILA_UNICA = 1;
    private static final Logger LOG = LoggerFactory.getLogger(PasarelaService.class);

    private final PasarelaEstadoRepository repo;
    private final Environment environment;

    public PasarelaService(PasarelaEstadoRepository repo, Environment environment) {
        this.repo = repo;
        this.environment = environment;
    }

    /**
     * FASE2-07 / AUD-018 (P2, opción a): el Modo Bypass solo existe fuera de
     * {@code prod}. En producción la pasarela se puede reactivar, nunca apagar.
     */
    public boolean bypassPermitido() {
        return !environment.acceptsProfiles(Profiles.of("prod"));
    }

    public boolean estaHabilitada() {
        return repo.findById(FILA_UNICA)
                .map(EstadoPasarela::isHabilitada)
                .orElse(true);
    }

    /** Upsert de la fila única. {@code adminUsuarioId} = usuario del Admin que
     * toggló (auditoría ya la registra el interceptor de M8; acá se conserva
     * quién lo hizo como dato de la propia fila). */
    @Transactional
    public boolean establecerHabilitada(boolean habilitada, UUID adminUsuarioId) {
        if (!habilitada) {
            if (!bypassPermitido()) {
                throw new BypassNoPermitidoException();
            }
            LOG.warn("Modo Bypass ACTIVADO por el admin {}: las reservas se confirman sin cobro real.", adminUsuarioId);
        }
        EstadoPasarela estado = repo.findById(FILA_UNICA).orElseGet(EstadoPasarela::new);
        estado.setId(FILA_UNICA);
        estado.setHabilitada(habilitada);
        estado.setUpdatedAt(Instant.now());
        estado.setUpdatedBy(adminUsuarioId);
        repo.save(estado);
        return estado.isHabilitada();
    }
}