package com.tinku.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistencia del contador de backoff de OCR (FR-ID-011), ligada a la
 * tabla {@code intentos_ocr} — migración V4. Ver {@code OcrBackoffService}.
 *
 * Un ciclo = hasta {@code tinku.ocr.max-intentos} (6) fotos ilegibles; al consumir la última se
 * fija {@code proximoIntentoPermitido} = ahora + 24hs y se resetea el
 * contador a 0 para el ciclo siguiente. El cooldown es PASIVO (se compara
 * el timestamp contra ahora al leer), no hay job que lo "limpie".
 */
@Entity
@Table(name = "intentos_ocr", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class IntentoOcr {

    @Id
    @Column(length = 20)
    private String dni;

    @Column(name = "intentos_consumidos", nullable = false)
    private int intentosConsumidos = 0;

    @Column(name = "proximo_intento_permitido")
    private Instant proximoIntentoPermitido;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
