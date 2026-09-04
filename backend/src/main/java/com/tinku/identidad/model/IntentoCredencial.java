package com.tinku.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistencia del backoff ESCALADO de credencial de Tutor (FR-ID-012) —
 * tabla {@code intentos_credencial} (migración V5). Ver
 * {@code CredencialBackoffService}.
 *
 * {@code vecesCicloAgotado} permite escalar el cooldown: 24hs * 2^n
 * (24→48→96…, Tabla_Tiempos). El cooldown es PASIVO (se compara
 * {@code proximoIntentoPermitido} contra ahora al leer), sin job que lo
 * "limpie" (Constitución, Artículo IV/X).
 */
@Entity
@Table(name = "intentos_credencial", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class IntentoCredencial {

    @Id
    private UUID tutorId;

    @Column(name = "veces_ciclo_agotado", nullable = false)
    private int vecesCicloAgotado = 0;

    @Column(name = "proximo_intento_permitido")
    private Instant proximoIntentoPermitido;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
