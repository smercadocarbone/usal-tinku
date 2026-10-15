package com.tinku.matching.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Búsqueda guardada por un Estudiante (o el perfil de menor) para re-ejecutar
 * después (FR-MATCH-008) — tabla {@code matching.busquedas_guardadas} (V7).
 *
 * Se guarda SOLO el texto de búsqueda. La re-ejecución vuelve a correr el flujo
 * completo contra el índice vigente (nunca devuelve una lista congelada), por
 * eso no existe una columna de resultados persistidos (Artículo V).
 */
@Entity
@Table(name = "busquedas_guardadas", schema = "matching")
@Getter
@Setter
@NoArgsConstructor
public class BusquedaGuardada {

    @Id
    @GeneratedValue
    private UUID id;

    /** Quién la guardó: Estudiante o el perfil de menor (FR-MATCH-008). */
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "texto_busqueda", nullable = false, length = 500)
    private String textoBusqueda;

    /** R-E E-2 (T1-6): filtros con los nombres del API (texto_busqueda, nombre, filtro_materia,
     *  filtro_nivel, precio_max_hora); solo los que vinieron. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "filtros", nullable = false)
    private java.util.Map<String, Object> filtros = new java.util.LinkedHashMap<>();

    /** Identifica "la misma búsqueda" de un usuario: evita duplicados. */
    @Column(name = "huella", nullable = false, length = 64)
    private String huella;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}