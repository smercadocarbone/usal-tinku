package com.tinku.identidad.service;

import com.tinku.identidad.model.IntentoOcr;
import com.tinku.identidad.repository.IntentoOcrRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Backoff persistido de OCR (FR-ID-011, Tabla_Tiempos: 24hs) — T-M1-07.
 *
 * Contrato de negocio:
 *  - Hasta 3 intentos de foto ILEGIBLE por ciclo (el rechazo por edad o DNI
 *    duplicado NO consume intentos: no tiene sentido "reintentar" ser mayor,
 *    ver Plan_M1 sección 2.1 paso 7).
 *  - Al consumir el 3ro, la persona queda en espera 24hs antes de un nuevo
 *    ciclo (contador se resetea a 0 para el ciclo siguiente).
 *  - El cooldown es PASIVO (se compara {@code proximoIntentoPermitido} contra
 *    ahora al momento de leer) — por eso no se necesita un job de Quartz para
 *    "resetear": es un timestamp persistido, no un callback en memoria
 *    (Constitución, Artículo IV/X).
 *
 * Clave del contador: el DNI DECLARADO de quien intenta darse de alta (todavía
 * no existe como usuario en la BD). Se aplica tanto al alta de adulto, de
 * menor y de tutor.
 */
@Service
public class OcrBackoffService {

    private static final int MAX_INTENTOS_CICLO = CicloIntentos.MAX;

    private final IntentoOcrRepository intentoRepo;
    private final Duration cooldown;

    @Autowired
    public OcrBackoffService(IntentoOcrRepository intentoRepo,
                             @Value("${tinku.ocr.backoff-horas:24}") long backoffHoras) {
        this(intentoRepo, Duration.ofHours(backoffHoras));
    }

    /** Constructor de test (inyección directa de Duration, sin Spring). */
    OcrBackoffService(IntentoOcrRepository intentoRepo, Duration cooldown) {
        this.intentoRepo = intentoRepo;
        this.cooldown = cooldown;
    }

    /**
     * Aborta el intento si el DNI declarado está en periodo de espera.
     * @throws DocumentoEnBackoffException si falta tiempo de backoff.
     */
    @Transactional
    public void chequearPuedeIntentar(String dniDeclarado) {
        intentoRepo.findByDni(dniDeclarado).ifPresent(intento -> {
            Instant hasta = intento.getProximoIntentoPermitido();
            if (hasta != null && hasta.isAfter(Instant.now())) {
                throw new DocumentoEnBackoffException(
                        Duration.between(Instant.now(), hasta));
            }
        });
    }

    /**
     * Registra una foto ilegible del DNI declarado. Devuelve cuántos intentos
     * quedan en el ciclo actual (antes de la espera de 24hs).
     */
    @Transactional
    public int registrarIntentoFallido(String dniDeclarado) {
        IntentoOcr intento = intentoRepo.findByDni(dniDeclarado)
                .orElseGet(IntentoOcr::new);
        intento.setDni(dniDeclarado);

        int consumidos = intento.getIntentosConsumidos() + 1;
        if (consumidos >= MAX_INTENTOS_CICLO) {
            // Se agotó el ciclo → espera de 24hs y reset para el siguiente.
            intento.setProximoIntentoPermitido(Instant.now().plus(cooldown));
            intento.setIntentosConsumidos(0);
        } else {
            intento.setProximoIntentoPermitido(null);
            intento.setIntentosConsumidos(consumidos);
        }
        intento.setUpdatedAt(Instant.now());
        intentoRepo.save(intento);

        return Math.max(0, MAX_INTENTOS_CICLO - consumidos);
    }
}
