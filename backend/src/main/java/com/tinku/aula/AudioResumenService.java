package com.tinku.aula;

import com.tinku.aula.evento.AudioResumenRecibidoEvent;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.AdicionalResumen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-M3-04 (T08, Constitución v2.4 Art. V): audio de la clase para el resumen. Graba el navegador
 * del Tutor y lo sube al terminar; acá se decide si corresponde grabar, se recibe el archivo
 * (volviendo a controlar las cuatro condiciones, sin confiar en el cliente) y se borra.
 * Nunca video, nunca con un Menor.
 */
@Service
public class AudioResumenService {

    private static final Logger log = LoggerFactory.getLogger(AudioResumenService.class);

    /** Límite de la API de transcripción; 180 min a 16 kbps ≈ 21,6 MB. */
    public static final int MAX_BYTES = 25 * 1024 * 1024;

    private final SesionAprendizajeRepository sesionRepo;
    private final ReservaRepository reservaRepo;
    private final AdicionalResumen adicional;
    private final Almacenamiento almacenamiento;
    private final ApplicationEventPublisher events;

    public AudioResumenService(SesionAprendizajeRepository sesionRepo, ReservaRepository reservaRepo,
                               AdicionalResumen adicional, Almacenamiento almacenamiento,
                               ApplicationEventPublisher events) {
        this.sesionRepo = sesionRepo;
        this.reservaRepo = reservaRepo;
        this.adicional = adicional;
        this.almacenamiento = almacenamiento;
        this.events = events;
    }

    /** Lo que el token le dice al navegador: solo el Tutor graba, y solo si se cumplen las cuatro condiciones. */
    @Transactional(readOnly = true)
    public boolean debeGrabar(Usuario usuario, UUID sesionId) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId).orElse(null);
        if (sesion == null || sesion.getAudioReferencia() != null) {
            return false;
        }
        Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElse(null);
        return reserva != null
                && reserva.getTutor().getId().equals(usuario.getId())
                && adicional.permiteGrabar(reserva);
    }

    @Transactional
    public void recibir(Usuario usuario, UUID sesionId, byte[] audio) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId).orElseThrow(SesionNoEncontradaException::new);
        Reserva reserva = reservaRepo.findById(sesion.getReservaId()).orElseThrow(SesionNoEncontradaException::new);
        if (!reserva.getTutor().getId().equals(usuario.getId())) {
            throw new SoloParticipanteException(); // solo el navegador del Tutor graba
        }
        if (!adicional.permiteGrabar(reserva)) {
            log.error("AUDIO_RESUMEN_RECHAZADO sesionId={} — no se cumplen las condiciones de ADR-M3-04", sesionId);
            throw new AudioResumenInvalidoException("Esta clase no tiene habilitada la grabación para el resumen.");
        }
        if (sesion.getAudioReferencia() != null || sesion.getAudioBorradoAt() != null) {
            throw new AudioResumenInvalidoException("El audio de esta clase ya se recibió.");
        }
        if (audio == null || audio.length == 0 || audio.length > MAX_BYTES) {
            throw new AudioResumenInvalidoException("El audio tiene que pesar entre 1 byte y 25 MB.");
        }
        if (!esAudio(audio)) {
            throw new AudioResumenInvalidoException("El audio tiene que ser WebM u Ogg.");
        }
        sesion.setAudioReferencia(almacenamiento.guardar(audio, "audio-resumen-" + sesionId));
        sesion.setAudioRecibidoAt(Instant.now());
        sesionRepo.save(sesion);
        events.publishEvent(new AudioResumenRecibidoEvent(sesionId));
        log.info("AUDIO_RESUMEN_RECIBIDO sesionId={} bytes={}", sesionId, audio.length);
    }

    /** Para el transcript. {@code null} si no hay audio (nunca llegó o ya se borró). */
    @Transactional(readOnly = true)
    public byte[] leer(UUID sesionId) {
        return sesionRepo.findById(sesionId)
                .filter(s -> s.getAudioReferencia() != null && s.getAudioBorradoAt() == null)
                .map(s -> almacenamiento.leer(s.getAudioReferencia()))
                .orElse(null);
    }

    /** PT6: borra el audio (al transcribir o por el tope de 24 hs). Idempotente. */
    @Transactional
    public void borrar(UUID sesionId) {
        sesionRepo.findById(sesionId).ifPresent(s -> {
            if (s.getAudioReferencia() != null && s.getAudioBorradoAt() == null) {
                almacenamiento.borrar(s.getAudioReferencia());
                s.setAudioBorradoAt(Instant.now());
                sesionRepo.save(s);
                log.info("AUDIO_RESUMEN_BORRADO sesionId={}", sesionId);
            }
        });
    }

    /** WebM/Matroska (EBML 1A 45 DF A3) u Ogg ("OggS"). */
    static boolean esAudio(byte[] b) {
        boolean ebml = b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
        boolean ogg = b.length >= 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S';
        return ebml || ogg;
    }
}
