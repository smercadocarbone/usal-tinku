package com.tinku.reservas.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.evento.PaqueteCanceladoEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Paquete;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.TarifaProveedor;
import com.tinku.reservas.repository.PaqueteRepository;
import com.tinku.reservas.repository.ReservaRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paquete mensual (FR-RES-032..037, ADR-M5-03): 4 clases semanales (mismo día, hora y duración)
 * que se pagan juntas. Cada clase se valida con las mismas reglas que una reserva suelta y es una
 * {@link Reserva} normal con {@code paquete}; la primera es la ancla del pago.
 */
@Service
public class PaqueteService {

    /** ADR-M5-03: 4 clases, una por semana. */
    public static final int CLASES = 4;
    /** Tabla_Tiempos: vigencia del paquete, 4 semanas desde la primera clase. */
    public static final Duration VIGENCIA = Duration.ofDays(28);

    private final ReservaService reservaService;
    private final ReservaRepository reservaRepo;
    private final PaqueteRepository paqueteRepo;
    private final UsuarioRepository usuarioRepo;
    private final TarifaProveedor tarifas;
    private final ApplicationEventPublisher events;
    private final com.tinku.shared.notificacion.Notificador notificador;

    public PaqueteService(ReservaService reservaService, ReservaRepository reservaRepo, PaqueteRepository paqueteRepo,
                          UsuarioRepository usuarioRepo, TarifaProveedor tarifas, ApplicationEventPublisher events,
                          com.tinku.shared.notificacion.Notificador notificador) {
        this.notificador = notificador;
        this.reservaService = reservaService;
        this.reservaRepo = reservaRepo;
        this.paqueteRepo = paqueteRepo;
        this.usuarioRepo = usuarioRepo;
        this.tarifas = tarifas;
        this.events = events;
    }

    public record Oferta(boolean disponible, int descuentoPorcentaje, int clases, int semanas) {
    }

    /** Lo que ve quien reserva: si el Tutor ofrece el paquete y con qué descuento. */
    @Transactional(readOnly = true)
    public Oferta oferta(UUID tutorId) {
        return tarifas.descuentoPaquete(tutorId)
                .map(d -> new Oferta(true, d, CLASES, CLASES))
                .orElseGet(() -> new Oferta(false, 0, CLASES, CLASES));
    }

    /** Las 4 fechas (cada 7 días, en hora argentina). */
    public static List<Instant> fechas(Instant primera) {
        List<Instant> fechas = new ArrayList<>();
        var local = primera.atZone(ReservasZonaHoraria.ZONA);
        for (int i = 0; i < CLASES; i++) {
            fechas.add(local.plusWeeks(i).toInstant());
        }
        return fechas;
    }

    /**
     * FR-RES-032/033: arma el paquete en {@code pendiente_pago}. Si alguna fecha no entra (franja,
     * ventana mínima u ocupada), 422 con todas las que chocan. Un solo timeout de pago: el de la
     * clase ancla, que al vencer vence las 4.
     */
    @Transactional
    public Paquete crear(Usuario pagador, UUID tutorId, UUID beneficiarioId, Instant horario, Integer duracionMinutos) {
        Usuario tutor = usuarioRepo.findById(tutorId).orElseThrow(TutorNoEncontradoException::new);
        Usuario beneficiario = reservaService.beneficiarioDe(pagador, tutorId, beneficiarioId);
        int descuento = tarifas.descuentoPaquete(tutorId)
                .orElseThrow(() -> new PaqueteNoDisponibleException("Este tutor no ofrece el paquete del mes."));
        if (duracionMinutos == null) {
            throw new DuracionMinutosInvalidaException("Elegí la duración de las clases.");
        }

        List<Instant> fechas = fechas(horario);
        List<Instant> chocan = new ArrayList<>();
        for (Instant fecha : fechas) {
            try {
                reservaService.validarNuevaClase(pagador, beneficiario, tutor, fecha, duracionMinutos);
            } catch (VentanaMinimaException | HorarioFueraDeFranjaException e) {
                chocan.add(fecha);
                continue;
            }
            if (ocupado(tutor.getId(), beneficiario.getId(), fecha, duracionMinutos)) {
                chocan.add(fecha);
            }
        }
        if (!chocan.isEmpty()) {
            throw new PaqueteNoDisponibleException(
                    "Algunas fechas del paquete no están disponibles. Probá con otro día u horario.", chocan);
        }

        BigDecimal precioClase = reservaService.precioDe(tutorId, duracionMinutos, descuento);
        Paquete paquete = new Paquete();
        paquete.setPagador(pagador);
        paquete.setBeneficiario(beneficiario);
        paquete.setTutor(tutor);
        paquete.setCantidadClases(CLASES);
        paquete.setDuracionMinutos(duracionMinutos);
        paquete.setDescuentoPorcentaje(descuento);
        paquete.setPrecioTotal(precioClase.multiply(BigDecimal.valueOf(CLASES)));
        paquete.setVigenteHasta(horario.plus(VIGENCIA));
        paquete = paqueteRepo.save(paquete);

        List<Reserva> clases = new ArrayList<>();
        for (Instant fecha : fechas) {
            clases.add(ReservaService.nuevaClase(pagador, beneficiario, tutor, fecha, duracionMinutos, precioClase, paquete));
        }
        // saveAndFlush: si otra reserva ganó un horario en carrera, la EXCLUDE da 409 acá.
        clases = reservaRepo.saveAllAndFlush(clases);
        paquete.setReservaAnclaId(clases.get(0).getId());
        paqueteRepo.save(paquete);
        reservaService.programarTimeoutPago(clases.get(0));
        return paquete;
    }

    /**
     * FR-RES-034 / FR-PAG-023: quien pagó cancela el paquete entero hasta 24 hs antes de la primera
     * clase, con todas confirmadas y sin haber movido ni cancelado ninguna. M5 devuelve el total.
     */
    @Transactional
    public void cancelar(Usuario usuario, UUID paqueteId) {
        Paquete paquete = paqueteRepo.findById(paqueteId).orElseThrow(ReservaNoEncontradaException::new);
        if (!paquete.getPagador().getId().equals(usuario.getId())) {
            throw new SoloPagadorReservaException();
        }
        List<Reserva> clases = reservaRepo.findByPaquete_IdOrderByHorario(paqueteId);
        if (!puedeCancelarse(paquete, clases, Instant.now())) {
            throw new PaqueteNoDisponibleException("El paquete entero se puede cancelar hasta 24 horas antes de "
                    + "la primera clase y con todas las clases todavía en pie.");
        }
        paquete.setEstado(Paquete.CANCELADO);
        paqueteRepo.save(paquete);
        for (Reserva clase : clases) {
            reservaService.cancelarClaseDePaquete(clase, usuario.getId());
        }
        events.publishEvent(new PaqueteCanceladoEvent(this, paqueteId, paquete.getReservaAnclaId()));
        // Un solo aviso al Tutor (no uno por clase), con la fecha de la primera.
        notificador.notificar(paquete.getTutor().getId(), com.tinku.shared.notificacion.TipoNotificacion.CLASE_CANCELADA,
                java.util.Map.of("reservaId", paquete.getReservaAnclaId().toString(),
                        "horario", clases.get(0).getHorario().toString(), "canceladaPor", "alumno"));
    }

    public static boolean puedeCancelarse(Paquete paquete, List<Reserva> clases, Instant ahora) {
        return Paquete.CONFIRMADO.equals(paquete.getEstado())
                && clases.size() == paquete.getCantidadClases()
                && clases.stream().allMatch(r -> r.getEstado() == EstadoReserva.CONFIRMADA)
                && !ahora.plus(com.tinku.reservas.model.PoliticaCancelacion.VENTANA_SIN_PENALIDAD)
                        .isAfter(clases.get(0).getHorario());
    }

    private boolean ocupado(UUID tutorId, UUID beneficiarioId, Instant inicio, int duracionMinutos) {
        Instant fin = inicio.plus(Duration.ofMinutes(duracionMinutos));
        List<EstadoReserva> vivas = List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, EstadoReserva.EN_CURSO);
        Instant desde = inicio.minus(Duration.ofHours(4)); // una clase dura como máximo 3 hs (Tabla_Tiempos)
        return reservaRepo.findByEstadoInAndHorarioAfterAndTutor_Id(vivas, desde, tutorId).stream()
                .anyMatch(r -> r.getHorario().isBefore(fin) && inicio.isBefore(r.getHorarioFin()))
                || reservaRepo.findByEstadoInAndHorarioAfterAndBeneficiario_Id(vivas, desde, beneficiarioId).stream()
                .anyMatch(r -> r.getHorario().isBefore(fin) && inicio.isBefore(r.getHorarioFin()));
    }
}
