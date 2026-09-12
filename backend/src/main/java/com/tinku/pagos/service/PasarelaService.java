package com.tinku.pagos.service;

import com.tinku.pagos.model.EstadoPasarela;
import com.tinku.pagos.repository.PasarelaEstadoRepository;
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

    private final PasarelaEstadoRepository repo;

    public PasarelaService(PasarelaEstadoRepository repo) {
        this.repo = repo;
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
        EstadoPasarela estado = repo.findById(FILA_UNICA).orElseGet(EstadoPasarela::new);
        estado.setId(FILA_UNICA);
        estado.setHabilitada(habilitada);
        estado.setUpdatedAt(Instant.now());
        estado.setUpdatedBy(adminUsuarioId);
        repo.save(estado);
        return estado.isHabilitada();
    }
}