package com.tinku.aula.model;

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
 * Sesión de Aprendizaje creada por la Reserva confirmada (M3, US-1). La crea
 * {@code SesionService} (T-M3-03) al recibir la confirmación del pago; los
 * jobs de Quartz de M3-B avanzan su ciclo de vida: T-5 crea la sala
 * ({@code livekitRoomId}), el webhook marca la entrada de participantes
 * (V10, {@code joinedAt}) y T+10 decide el no-show (T-M3-04). El cierre
 * (finalizada / finalizada_anticipada) escribe {@code inicioReal},
 * {@code finReal} y {@code duracionEfectivaSegundos} — columnas de V8 que M6
 * usa para el umbral de 10 min.
 */
@Entity
@Table(name = "sesiones_aprendizaje", schema = "aula")
@Getter
@Setter
@NoArgsConstructor
public class SesionAprendizaje {

    public static final String ESTADO_NO_INICIADA = "no_iniciada";
    public static final String ESTADO_EN_CURSO = "en_curso";
    public static final String ESTADO_FINALIZADA = "finalizada";
    public static final String ESTADO_FINALIZADA_ANTICIPADA = "finalizada_anticipada";
    public static final String ESTADO_INTERRUMPIDA = "interrumpida";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reserva_id", nullable = false, unique = true)
    private UUID reservaId;

    @Column(name = "livekit_room_id", length = 100)
    private String livekitRoomId;

    @Column(nullable = false, length = 30)
    private String estado = ESTADO_NO_INICIADA;

    /** Primer join del estudiante (beneficiario de la Reserva), via webhook (V10). */
    @Column(name = "estudiante_joined_at")
    private Instant estudianteJoinedAt;

    /** Primer join del tutor, via webhook (V10). */
    @Column(name = "tutor_joined_at")
    private Instant tutorJoinedAt;

    /** Momento real de inicio (primer join de cualquiera de los dos). */
    @Column(name = "inicio_real")
    private Instant inicioReal;

    /** Momento real de fin (finalizar manual, corte automático o no-show). */
    @Column(name = "fin_real")
    private Instant finReal;

    /** Segundos efectivos (fin_real - inicio_real); el no-show deja 0. */
    @Column(name = "duracion_efectiva_segundos")
    private Integer duracionEfectivaSegundos;

    /**
     * Duración agendada de la franja que cubre la Reserva (en segundos).
     * Se fija al programar la Sesión (T-M3-03) y se usa en el corte automático
     * para decidir si aplica la regla del 50% (US-5, FR-AULA-005).
     */
    @Column(name = "duracion_agendada_segundos")
    private Integer duracionAgendadaSegundos;
}