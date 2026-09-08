package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.evento.ReservaConfirmadaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.port.TarifaProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Creación de Reserva por aprobación de una Solicitud del menor (US-3/US-4,
 * T-M4-04). La Reserva la crea SOLO quien paga/n el menor: Estudiante adulto o
 * Adulto Responsable por un menor a su cargo (Artículo II). El Tutor no acepta
 * manualmente — la validación de franja activa ES la aceptación implícita.
 */
@Service
public class ReservaService {

    /** FR-RES-013 — no se reserva a menos de 15 min del inicio (Tabla_Tiempos_Tinku.md). */
    private static final java.time.Duration VENTANA_MINIMA = java.time.Duration.ofMinutes(15);

    private final SolicitudSesionRepository solicitudRepo;
    private final ReservaRepository reservaRepo;
    private final FranjaService franjaService;
    private final TarifaProveedor tarifaProveedor;
    private final ApplicationEventPublisher events;

    public ReservaService(SolicitudSesionRepository solicitudRepo,
                          ReservaRepository reservaRepo,
                          FranjaService franjaService,
                          TarifaProveedor tarifaProveedor,
                          ApplicationEventPublisher events) {
        this.solicitudRepo = solicitudRepo;
        this.reservaRepo = reservaRepo;
        this.franjaService = franjaService;
        this.tarifaProveedor = tarifaProveedor;
        this.events = events;
    }

    /**
     * Convierte la Solicitud pendiente en Reserva con estado pendiente_pago.
     * El pago en sí lo dispara M5 (dependencia pendiente); esta transacción
     * garantiza la fila en `reservas` y el avance de la Solicitud a convertida.
     */
    @Transactional
    public Reserva aprobarSolicitud(Usuario adultoResponsable, UUID solicitudId) {
        exigirCapacidadAdultoResponsable(adultoResponsable);

        SolicitudSesion solicitud = solicitudRepo.findByIdAndEstado(
                solicitudId, EstadoSolicitud.PENDIENTE)
                .orElseThrow(SolicitudNoPendienteException::new);

        // FR-ID-020: solo puede operar menores a su cargo.
        Usuario menor = solicitud.getMenor();
        if (menor.getAdultoResponsable() == null
                || !menor.getAdultoResponsable().getId().equals(adultoResponsable.getId())) {
            throw new SolicitudMenorNoPerteneceException();
        }

        Instant horario = solicitud.getHorarioPropuesto();
        if (Instant.now().plus(VENTANA_MINIMA).isAfter(horario)) {
            throw new VentanaMinimaException(
                    "Faltan menos de 15 minutos para el horario — no se puede reservar (FR-RES-013).");
        }
        if (!franjaService.estaDentroDeFranjaActiva(solicitud.getTutor().getId(), horario)) {
            throw new HorarioFueraDeFranjaException(
                    "La franja ya no está activa o ya no cubre el horario (FR-RES-012).");
        }

        // FR-PAG-013: el precio se congela al crear la Reserva (fuente: M5, tarifa del Tutor).
        Reserva reserva = new Reserva();
        reserva.setPagador(adultoResponsable);
        reserva.setBeneficiario(menor);
        reserva.setTutor(solicitud.getTutor());
        reserva.setSolicitudOrigen(solicitud);
        reserva.setHorario(horario);
        reserva.setPrecio(tarifaProveedor.tarifaPorSesion(solicitud.getTutor().getId()));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        Reserva guardada = reservaRepo.save(reserva);

        solicitud.setEstado(EstadoSolicitud.CONVERTIDA);
        solicitudRepo.save(solicitud);
        return guardada;
    }

    /**
     * STUB TEMPORAL — reemplazar cuando M5 implemente el webhook real de
     * MercadoPago (Chunk M5-B). Simula la confirmación del pago: transición
     * pendiente_pago → confirmada. Idempotente (los webhooks de MP se
     * reintentan, así que confirmar algo ya confirmado no es un error). Al
     * confirmar, emite {@link ReservaConfirmadaEvent} para que M3 cree y agende
     * la Sesión de Aprendizaje (T-M3-03/04/05).
     */
    @Transactional
    public Reserva confirmarPagoSimulado(UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId)
                .orElseThrow(ReservaNoEncontradaException::new);
        if (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
            reserva.setEstado(EstadoReserva.CONFIRMADA);
            reservaRepo.save(reserva);
            events.publishEvent(new ReservaConfirmadaEvent(this, reserva.getId()));
        }
        return reserva;
    }

    private void exigirCapacidadAdultoResponsable(Usuario usuario) {
        if (usuario.getTipo() == TipoUsuario.MENOR || !usuario.isCapacidadAdultoResponsable()) {
            throw new SoloAdultoResponsableException(
                    "Solo la capacidad 'Adulto Responsable' puede aprobar y crear Reservas.");
        }
    }
}