package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.reservas.evento.ReservaReprogramadaEvent;
import com.tinku.reservas.jobs.BorradoAdjuntoPedidoJob;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.PedidoPrevio;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.PedidoPrevioRepository;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.resumen.anonimizacion.AnonimizadorTranscript;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Pedido previo a la clase (FR-RES-027/028, ADR-M4-01). Lo escribe quien pagó (con un Menor,
 * siempre su Adulto Responsable: el Menor nunca es el pagador, Art. II) y lo ven él y el Tutor.
 * Texto filtrado con el mismo anonimizador que M6. El archivo (JPG/PNG/PDF validado por firma)
 * se borra 24 hs después del fin agendado con un job de Quartz persistido, o en el acto si la
 * reserva se cancela.
 */
@Service
public class PedidoPrevioService {

    /** ADR-M4-01 / Tabla_Tiempos: retención del adjunto desde el fin agendado de la clase. */
    public static final Duration RETENCION_ADJUNTO = Duration.ofHours(24);

    private final ReservaRepository reservaRepo;
    private final PedidoPrevioRepository pedidoRepo;
    private final Almacenamiento almacenamiento;
    private final AnonimizadorTranscript anonimizador;
    private final Scheduler scheduler;
    private final DataSize maxArchivo;

    public PedidoPrevioService(ReservaRepository reservaRepo, PedidoPrevioRepository pedidoRepo,
                               Almacenamiento almacenamiento, AnonimizadorTranscript anonimizador,
                               Scheduler scheduler,
                               @Value("${tinku.archivos.max-documento:5MB}") DataSize maxArchivo) {
        this.reservaRepo = reservaRepo;
        this.pedidoRepo = pedidoRepo;
        this.almacenamiento = almacenamiento;
        this.anonimizador = anonimizador;
        this.scheduler = scheduler;
        this.maxArchivo = maxArchivo;
    }

    public record Vista(UUID reservaId, String texto, String archivoNombre, String archivoTipo, Instant updatedAt,
                        boolean editable) {
    }

    public record Archivo(byte[] contenido, String tipo, String nombre) {
    }

    /** Guarda o reemplaza el pedido. {@code archivo} null = no se toca el archivo actual. */
    @Transactional
    public Vista guardar(Usuario usuario, UUID reservaId, String texto, byte[] archivo, String nombreArchivo) {
        Reserva reserva = reservaDelPagador(usuario, reservaId);
        if (!editable(reserva, Instant.now())) {
            throw new PedidoPrevioNoEditableException();
        }
        String limpio = texto == null || texto.isBlank() ? null : anonimizador.anonimizar(texto.strip());
        if (limpio != null && limpio.length() > 1000) {
            limpio = limpio.substring(0, 1000);
        }
        PedidoPrevio pedido = pedidoRepo.findById(reservaId).orElseGet(() -> new PedidoPrevio(reservaId));
        pedido.setTexto(limpio);
        if (archivo != null && archivo.length > 0) {
            if (archivo.length > maxArchivo.toBytes()) {
                throw new ArchivoPedidoInvalidoException("El archivo no puede pesar más de "
                        + maxArchivo.toMegabytes() + " MB.");
            }
            TipoArchivoCredencial tipo = TipoArchivoCredencial.detectar(archivo)
                    .orElseThrow(() -> new ArchivoPedidoInvalidoException(
                            "Solo se aceptan fotos (JPG o PNG) o PDF."));
            borrarDelAlmacenamiento(pedido);
            pedido.setArchivoRef(almacenamiento.guardar(archivo, "pedido-" + reservaId));
            pedido.setArchivoNombre(nombreSeguro(nombreArchivo, tipo));
            pedido.setArchivoTipo(tipo.getMediaType());
            programarBorrado(reserva);
        }
        if (pedido.getTexto() == null && pedido.getArchivoRef() == null) {
            throw new ArchivoPedidoInvalidoException("Escribí qué querés ver o adjuntá un archivo.");
        }
        pedido.setUpdatedAt(Instant.now());
        return vista(pedidoRepo.save(pedido), reserva);
    }

    @Transactional(readOnly = true)
    public Optional<Vista> ver(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaDePagadorOTutor(usuario, reservaId);
        return pedidoRepo.findById(reservaId).map(p -> vista(p, reserva));
    }

    @Transactional(readOnly = true)
    public Archivo archivo(Usuario usuario, UUID reservaId) {
        reservaDePagadorOTutor(usuario, reservaId);
        PedidoPrevio pedido = pedidoRepo.findById(reservaId)
                .filter(p -> p.getArchivoRef() != null)
                .orElseThrow(ReservaNoEncontradaException::new);
        return new Archivo(almacenamiento.leer(pedido.getArchivoRef()), pedido.getArchivoTipo(),
                pedido.getArchivoNombre());
    }

    @Transactional
    public void quitarArchivo(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaDelPagador(usuario, reservaId);
        if (!editable(reserva, Instant.now())) {
            throw new PedidoPrevioNoEditableException();
        }
        borrarArchivoDe(reservaId);
    }

    /** Al cancelarse la reserva (ADR-M4-01). Idempotente. */
    @Transactional
    public void borrarArchivoDe(UUID reservaId) {
        borrarArchivo(reservaId);
        desagendarBorrado(reservaId);
    }

    private void borrarArchivo(UUID reservaId) {
        pedidoRepo.findById(reservaId).ifPresent(p -> {
            if (p.getArchivoRef() == null) {
                return;
            }
            borrarDelAlmacenamiento(p);
            p.setArchivoRef(null);
            p.setArchivoNombre(null);
            p.setArchivoTipo(null);
            p.setUpdatedAt(Instant.now());
            if (p.getTexto() == null) {
                pedidoRepo.delete(p);
            } else {
                pedidoRepo.save(p);
            }
        });
    }

    /**
     * Job de retención: borra si la reserva se canceló o ya pasaron 24 hs del fin agendado. Si la
     * clase se movió, {@link #onReservaReprogramada} ya re-agendó el job para el fin nuevo: este
     * disparo viejo no hace nada. No toca el scheduler (corre dentro del propio job).
     */
    @Transactional
    public void vencerArchivo(UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElse(null);
        if (reserva == null || reserva.getEstado() == EstadoReserva.CANCELADA
                || !Instant.now().isBefore(reserva.getHorarioFin().plus(RETENCION_ADJUNTO))) {
            borrarArchivo(reservaId);
        }
    }

    /** La clase cambió de horario: el borrado se mueve con ella (antes o después). */
    @EventListener
    @Transactional
    public void onReservaReprogramada(ReservaReprogramadaEvent evento) {
        pedidoRepo.findById(evento.getReservaId())
                .filter(p -> p.getArchivoRef() != null)
                .flatMap(p -> reservaRepo.findById(p.getReservaId()))
                .ifPresent(this::programarBorrado);
    }

    public TriggerKey triggerBorrado(UUID reservaId) {
        return new TriggerKey("borrado-adjunto-trigger-" + reservaId, ReservaService.GRUPO_JOB);
    }

    private static JobKey jobBorrado(UUID reservaId) {
        return new JobKey("borrado-adjunto-" + reservaId, ReservaService.GRUPO_JOB);
    }

    static boolean editable(Reserva reserva, Instant ahora) {
        return (reserva.getEstado() == EstadoReserva.PENDIENTE_PAGO
                || reserva.getEstado() == EstadoReserva.CONFIRMADA)
                && ahora.isBefore(reserva.getHorario());
    }

    private Vista vista(PedidoPrevio p, Reserva reserva) {
        return new Vista(p.getReservaId(), p.getTexto(), p.getArchivoNombre(), p.getArchivoTipo(), p.getUpdatedAt(),
                editable(reserva, Instant.now()));
    }

    private Reserva reservaDelPagador(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (esPagador(reserva, usuario)) {
            return reserva;
        }
        if (esTutor(reserva, usuario) || reserva.getBeneficiario().getId().equals(usuario.getId())) {
            throw new SoloPagadorReservaException();
        }
        throw new ReservaNoEncontradaException();
    }

    private Reserva reservaDePagadorOTutor(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!esPagador(reserva, usuario) && !esTutor(reserva, usuario)) {
            throw new ReservaNoEncontradaException();
        }
        return reserva;
    }

    private static boolean esPagador(Reserva r, Usuario u) {
        return r.getPagador() != null && r.getPagador().getId().equals(u.getId());
    }

    private static boolean esTutor(Reserva r, Usuario u) {
        return r.getTutor().getId().equals(u.getId());
    }

    private void borrarDelAlmacenamiento(PedidoPrevio p) {
        if (p.getArchivoRef() != null) {
            almacenamiento.borrar(p.getArchivoRef());
        }
    }

    /** Nombre para mostrar: sin rutas ni caracteres raros, con la extensión del tipo real. */
    static String nombreSeguro(String original, TipoArchivoCredencial tipo) {
        String extension = switch (tipo) {
            case PDF -> ".pdf";
            case PNG -> ".png";
            case JPEG -> ".jpg";
        };
        String base = original == null ? "" : original.replaceAll(".*[/\\\\]", "").replaceAll("\\.[^.]*$", "");
        base = base.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (base.isEmpty()) {
            base = "archivo";
        }
        if (base.length() > 150) {
            base = base.substring(0, 150);
        }
        return base + extension;
    }

    private void programarBorrado(Reserva reserva) {
        Instant cuando = reserva.getHorarioFin().plus(RETENCION_ADJUNTO);
        JobDetail detail = JobBuilder.newJob(BorradoAdjuntoPedidoJob.class)
                .withIdentity(jobBorrado(reserva.getId()))
                .usingJobData(BorradoAdjuntoPedidoJob.PARAM_RESERVA_ID, reserva.getId().toString())
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerBorrado(reserva.getId()))
                .startAt(Date.from(cuando))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        try {
            // Nunca se llama desde el propio job (ver vencerArchivo): reemplazar es seguro.
            scheduler.deleteJob(detail.getKey());
            scheduler.scheduleJob(detail, trigger);
        } catch (SchedulerException e) {
            // Fail-closed (Art. V): un adjunto sin su borrado agendado no se guarda.
            throw new IllegalStateException("No se pudo agendar el borrado del adjunto del pedido.", e);
        }
    }

    private void desagendarBorrado(UUID reservaId) {
        try {
            scheduler.deleteJob(jobBorrado(reservaId));
        } catch (SchedulerException e) {
            // Benigno: el job es idempotente (sin archivo no hace nada).
        }
    }
}
