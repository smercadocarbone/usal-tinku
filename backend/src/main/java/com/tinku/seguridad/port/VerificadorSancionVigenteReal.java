package com.tinku.seguridad.port;

import com.tinku.identidad.port.VerificadorSancionVigente;
import com.tinku.seguridad.repository.SancionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** Implementación real del puerto {@link VerificadorSancionVigente} (M9), que usa M1 (AUD-013). */
@Component
public class VerificadorSancionVigenteReal implements VerificadorSancionVigente {

    private final SancionRepository sancionRepo;

    public VerificadorSancionVigenteReal(SancionRepository sancionRepo) {
        this.sancionRepo = sancionRepo;
    }

    @Override
    public boolean tieneSancionVigente(UUID usuarioId) {
        return sancionRepo.existeSancionVigente(usuarioId, Instant.now());
    }
}
