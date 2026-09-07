package com.tinku.reservas.model;

import com.tinku.identidad.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Franja de disponibilidad publicada por un Tutor (FR-RES-012, T-M4-02).
 *
 * ADR-M4-01 (resuelto en el sprint): la franja es semanal recurrente
 * ({@code diaSemana}, 0=domingo..6=sábado) o puntual ({@code fechaEspecifica});
 * la constraint chk_franja_modo (V9) exige exactamente uno de los dos.
 */
@Entity
@Table(name = "franjas_disponibilidad", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class FranjaDisponibilidad {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @Column(name = "dia_semana")
    private Short diaSemana;

    @Column(name = "fecha_especifica")
    private LocalDate fechaEspecifica;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    @Column(nullable = false)
    private boolean activa = true;
}