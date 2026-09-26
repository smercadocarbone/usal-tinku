package com.tinku.reservas.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.NotaClase;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.NotaClaseRepository;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.resumen.anonimizacion.AnonimizadorTranscript;
import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Nota del Tutor al Adulto Responsable (FR-RES-026): en una clase finalizada con un Menor, el
 * Tutor cuenta brevemente cómo le fue. La ven el Tutor y el AR (el pagador); el Menor no. Texto
 * filtrado con el anonimizador de M6. No se graba nada: es texto que escribe una persona.
 */
@Service
public class NotaClaseService {

    /** Tabla_Tiempos: corrección de la nota, mismo criterio que la calificación (48 hs). */
    public static final Duration VENTANA_EDICION = Duration.ofHours(48);

    private final ReservaRepository reservaRepo;
    private final NotaClaseRepository notaRepo;
    private final AnonimizadorTranscript anonimizador;
    private final Notificador notificador;

    public NotaClaseService(ReservaRepository reservaRepo, NotaClaseRepository notaRepo,
                            AnonimizadorTranscript anonimizador, Notificador notificador) {
        this.reservaRepo = reservaRepo;
        this.notaRepo = notaRepo;
        this.anonimizador = anonimizador;
        this.notificador = notificador;
    }

    public record Vista(UUID reservaId, String texto, Instant createdAt, Instant updatedAt, boolean editable) {
    }

    @Transactional
    public Vista escribir(Usuario tutor, UUID reservaId, String texto) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        if (!reserva.getTutor().getId().equals(tutor.getId())) {
            if (esPagadorOBeneficiario(reserva, tutor)) {
                throw new SoloTutorException("Solo el Tutor de la clase escribe la nota.");
            }
            throw new ReservaNoEncontradaException();
        }
        if (reserva.getBeneficiario().getTipo() != TipoUsuario.MENOR
                || reserva.getEstado() != EstadoReserva.FINALIZADA) {
            throw new NotaClaseNoPermitidaException(
                    "La nota es para el adulto responsable de un chico, después de una clase terminada.");
        }
        if (texto == null || texto.isBlank()) {
            throw new NotaClaseNoPermitidaException("Escribí la nota.");
        }
        String limpio = anonimizador.anonimizar(texto.strip());
        if (limpio.length() > 1000) {
            limpio = limpio.substring(0, 1000);
        }
        Instant ahora = Instant.now();
        Optional<NotaClase> existente = notaRepo.findById(reservaId);
        if (existente.isPresent() && !editable(existente.get(), ahora)) {
            throw new NotaClaseNoPermitidaException("Pasaron más de 48 horas: la nota ya no se puede cambiar.");
        }
        NotaClase nota = existente.orElseGet(() -> new NotaClase(reservaId));
        nota.setTexto(limpio);
        nota.setUpdatedAt(ahora);
        notaRepo.save(nota);
        if (existente.isEmpty() && reserva.getPagador() != null) {
            notificador.notificar(reserva.getPagador().getId(), TipoNotificacion.NOTA_CLASE,
                    Map.of("reservaId", reservaId.toString()));
        }
        return vista(nota, ahora);
    }

    /** La ven el Tutor y el pagador (el AR). Cualquier otro, incluido el Menor: 404. */
    @Transactional(readOnly = true)
    public Optional<Vista> ver(Usuario usuario, UUID reservaId) {
        Reserva reserva = reservaRepo.findById(reservaId).orElseThrow(ReservaNoEncontradaException::new);
        boolean tutor = reserva.getTutor().getId().equals(usuario.getId());
        boolean pagador = reserva.getPagador() != null && reserva.getPagador().getId().equals(usuario.getId());
        if (!tutor && !pagador) {
            throw new ReservaNoEncontradaException();
        }
        Instant ahora = Instant.now();
        return notaRepo.findById(reservaId).map(n -> {
            Vista v = vista(n, ahora);
            return tutor ? v : new Vista(v.reservaId(), v.texto(), v.createdAt(), v.updatedAt(), false);
        });
    }

    private static boolean editable(NotaClase nota, Instant ahora) {
        return ahora.isBefore(nota.getCreatedAt().plus(VENTANA_EDICION));
    }

    private static Vista vista(NotaClase n, Instant ahora) {
        return new Vista(n.getReservaId(), n.getTexto(), n.getCreatedAt(), n.getUpdatedAt(), editable(n, ahora));
    }

    private static boolean esPagadorOBeneficiario(Reserva r, Usuario u) {
        return (r.getPagador() != null && r.getPagador().getId().equals(u.getId()))
                || r.getBeneficiario().getId().equals(u.getId());
    }
}
