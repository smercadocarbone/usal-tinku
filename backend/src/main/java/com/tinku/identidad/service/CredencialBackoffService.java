package com.tinku.identidad.service;

import com.tinku.identidad.model.IntentoCredencial;
import com.tinku.identidad.repository.IntentoCredencialRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Backoff ESCALADO de Credencial Académica (FR-ID-012, Tabla_Tiempos:
 * 24hs duplicándose → 24→48→96…) — T-M1-10.
 *
 * Contrato de negocio:
 *  - Tras agotar el ciclo de reintentos de una credencial (3 intentos, FR-ID-008),
 *    el Tutor queda en espera: primera vez 24hs, y cada re-agotamiento duplica
 *    el cooldown (24→48→96…).
 *  - El cooldown es PASIVO (se compara {@code proximoIntentoPermitido} contra
 *    ahora al momento de leer) — no hay job de Quartz que lo "limpie"
 *    (Constitución, Artículo IV/X).
 *
 * Clave: el {@code tutorId} (el tutor ya existe como usuario al cargar la
 * credencial). La base 24hs es configurable vía {@code tinku.credencial.backoff-horas}.
 */
@Service
public class CredencialBackoffService {

    private final IntentoCredencialRepository intentoRepo;
    private final Duration cooldownBase;

    @Autowired
    public CredencialBackoffService(IntentoCredencialRepository intentoRepo,
                                    @Value("${tinku.credencial.backoff-horas:24}") long backoffHoras) {
        this(intentoRepo, Duration.ofHours(backoffHoras));
    }

    /** Constructor de test (inyección directa de Duration, sin Spring). */
    CredencialBackoffService(IntentoCredencialRepository intentoRepo, Duration cooldownBase) {
        this.intentoRepo = intentoRepo;
        this.cooldownBase = cooldownBase;
    }

    /**
     * Aborta la carga si el Tutor está en periodo de espera.
     * @throws CredencialEnBackoffException si falta tiempo de espera (FR-ID-012).
     */
    @Transactional
    public void chequearPuedeIntentar(UUID tutorId) {
        intentoRepo.findById(tutorId).ifPresent(intento -> {
            Instant hasta = intento.getProximoIntentoPermitido();
            if (hasta != null && hasta.isAfter(Instant.now())) {
                throw new CredencialEnBackoffException(
                        Duration.between(Instant.now(), hasta));
            }
        });
    }

    /**
     * Registra un ciclo de reintentos agotado. Devuelve la fecha hasta la que
     * el Tutor debe esperar (ahora + 24hs * 2^n).
     */
    @Transactional
    public Instant registrarCicloAgotado(UUID tutorId) {
        IntentoCredencial intento = intentoRepo.findById(tutorId)
                .orElseGet(IntentoCredencial::new);
        intento.setTutorId(tutorId);

        Instant hasta = Instant.now().plus(cooldownMultiplicado(intento.getVecesCicloAgotado()));
        intento.setProximoIntentoPermitido(hasta);
        intento.setVecesCicloAgotado(intento.getVecesCicloAgotado() + 1);
        intento.setUpdatedAt(Instant.now());
        intentoRepo.save(intento);
        return hasta;
    }

    /** 24hs * 2^n → 24, 48, 96… */
    private Duration cooldownMultiplicado(int vecesAgotado) {
        long multiplicador = 1L << vecesAgotado; // 2^n
        return cooldownBase.multipliedBy(multiplicador);
    }
}
