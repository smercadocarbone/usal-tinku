package com.tinku.resumen.model;

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
 * Resumen automatico de una Sesion (M6, FR-SUM-001/005/006/007/008). Una fila
 * por sesion de aprendizaje con {@code duracionEfectivaSegundos >= 10min} (la
 * unicidad la garantiza el constraint UNIQUE de V15). {@code estado} es la unica
 * maquina del resumen:
 *
 * <pre>
 * pendiente → generado            (LLM respondio)
 *          → suspendido_seguridad (denuncia o alerta de seguridad activa, FR-SUM-008/T-M6-03)
 *          → fallido              (sin transcript util, caso borde #2 — nunca se inventa contenido)
 *          → reintento_agotado    (3 reintentos fallidos, FR-SUM-007 — la sesion NO se marca fallida)
 * </pre>
 *
 * {@code transcriptAnonimizado}/{@code promptAnonimizado} persisten la data YA
 * anonimizada (FR-SUM-005): se escriben en el primer intento de generacion y se
 * dejan ahi aunque el LLM falle — la anonimizacion aplica siempre, con o sin
 * proveedor (T-M6-04). El transcript crudo jamas se persiste (Articulo V).
 */
@Entity
@Table(name = "resumenes_sesion", schema = "resumen")
@Getter
@Setter
@NoArgsConstructor
public class ResumenSesion {

    public static final String ESTADO_PENDIENTE = "pendiente";
    public static final String ESTADO_GENERADO = "generado";
    public static final String ESTADO_SUSPENDIDO_SEGURIDAD = "suspendido_seguridad";
    public static final String ESTADO_REINTENTO_AGOTADO = "reintento_agotado";
    public static final String ESTADO_FALLIDO = "fallido";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sesion_id", nullable = false, unique = true)
    private UUID sesionId;

    @Column(nullable = false, length = 30)
    private String estado = ESTADO_PENDIENTE;

    @Column(name = "prompt_anonimizado", columnDefinition = "text")
    private String promptAnonimizado;

    @Column(name = "transcript_anonimizado", columnDefinition = "text")
    private String transcriptAnonimizado;

    @Column(name = "resumen_final", columnDefinition = "text")
    private String resumenFinal;

    @Column(nullable = false)
    private int intentos = 0;

    @Column(name = "proximo_reintento_at")
    private Instant proximoReintentoAt;

    @Column(name = "recordatorio_pendiente", nullable = false)
    private boolean recordatorioPendiente = false;

    @Column(name = "suspendido_seguridad", nullable = false)
    private boolean suspendidoSeguridad = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}