package com.tinku.reservas.model;

import com.tinku.identidad.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva (US-3/US-4, FR-RES-001/003/...). La crea y paga exclusivamente quien
 * tiene esa capacidad — Estudiante adulto o Adulto Responsable por un menor —
 * nunca el menor directamente (Artículo II). {@code precio} se congela al crear
 * la fila (FR-PAG-013 de M5). Dura {@code duracionMinutos} (D6) y la EXCLUDE por
 * rango {@code [horario, horario_fin)} (FR-RES-007, AUD-009) impide que se superponga
 * con otra no cancelada del mismo tutor o beneficiario.
 */
@Entity
@Table(name = "reservas", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagador_id", nullable = false)
    private Usuario pagador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiario_id", nullable = false)
    private Usuario beneficiario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_origen_id")
    private SolicitudSesion solicitudOrigen;

    @Column(nullable = false)
    private Instant horario;

    /** D6 / AUD-020. Default 30 (la duración mínima de la Tabla de Tiempos): así el código y los
     *  tests que crean reservas con setHorario(...) siguen funcionando. El flujo real la fija siempre. */
    @Column(name = "duracion_minutos", nullable = false)
    private Integer duracionMinutos = 30;

    /** Fin agendado. Se deriva SIEMPRE de horario + duración (AUD-009: la EXCLUDE depende de él). */
    @Column(name = "horario_fin", nullable = false)
    private Instant horarioFin;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Convert(converter = EstadoReservaConverter.class)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE_PAGO;

    @Convert(converter = MotivoCancelacionConverter.class)
    @Column(name = "motivo_cancelacion", length = 30)
    private MotivoCancelacion motivoCancelacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PrePersist
    @PreUpdate
    void sincronizarHorarioFin() {
        if (horario != null && duracionMinutos != null) {
            this.horarioFin = horario.plus(Duration.ofMinutes(duracionMinutos));
        }
    }

    /** Forma preferida en código de producción: fija horario y duración juntos. */
    public void definirHorario(Instant horario, int duracionMinutos) {
        this.horario = horario;
        this.duracionMinutos = duracionMinutos;
        sincronizarHorarioFin();
    }
}