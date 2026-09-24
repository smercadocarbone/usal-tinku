package com.tinku.admin.notificacion;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.shared.email.EmailEnvioException;
import com.tinku.shared.email.EnviadorEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Envío por email de los avisos del outbox (FASE2-03, ADR-000-06). Lo dispara
 * {@code EnvioEmailsJob} (Quartz persistido, A4). Reintentos: 3, backoff
 * 5 min / 15 min / 1 h (Tabla de Tiempos); agotados, o sin email del destinatario,
 * el email se descarta con log — la bandeja in-app no depende de este canal.
 * Fail-closed: sin proveedor configurado no toca nada (quedan pendientes).
 */
@Service
public class EnvioEmailNotificacionesService {

    private static final Logger log = LoggerFactory.getLogger(EnvioEmailNotificacionesService.class);

    /** Tabla_Tiempos_Tinku.md — "Reintentos de email de notificaciones". */
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1));

    private static final int LOTE = 20;

    private final NotificacionRepository repo;
    private final UsuarioRepository usuarioRepo;
    private final EnviadorEmail enviador;
    private final PlantillasEmail plantillas;

    public EnvioEmailNotificacionesService(NotificacionRepository repo, UsuarioRepository usuarioRepo,
                                           EnviadorEmail enviador, PlantillasEmail plantillas) {
        this.repo = repo;
        this.usuarioRepo = usuarioRepo;
        this.enviador = enviador;
        this.plantillas = plantillas;
    }

    /** @return cuántos avisos se procesaron (enviados, reprogramados o descartados). */
    @Transactional
    public int procesarPendientes() {
        if (!enviador.configurado()) {
            return 0;
        }
        Instant ahora = Instant.now();
        List<Notificacion> lote = repo.pendientesDeEmail(ahora, PageRequest.of(0, LOTE));
        lote.forEach(n -> procesar(n, ahora));
        return lote.size();
    }

    private void procesar(Notificacion n, Instant ahora) {
        Usuario destinatario = usuarioRepo.findById(n.getDestinatarioId()).orElse(null);
        if (destinatario == null || destinatario.getEmail() == null || destinatario.getEmail().isBlank()) {
            log.warn("Aviso {} ({}) sin email del destinatario: queda solo en la bandeja", n.getId(), n.getTipo());
            n.setEmailDescartadoAt(ahora);
            n.setProximoIntentoEmailAt(null);
            return;
        }
        try {
            enviador.enviar(plantillas.para(n, destinatario.getEmail(), destinatario.getNombre()));
            n.setEnviadaEmailAt(ahora);
            n.setProximoIntentoEmailAt(null);
        } catch (EmailEnvioException e) {
            int fallos = n.getIntentosEmail() + 1;
            n.setIntentosEmail(fallos);
            if (fallos > BACKOFF.size()) {
                log.error("Email del aviso {} ({}) descartado tras {} intentos: {}", n.getId(), n.getTipo(),
                        fallos, e.getMessage());
                n.setEmailDescartadoAt(ahora);
                n.setProximoIntentoEmailAt(null);
            } else {
                n.setProximoIntentoEmailAt(ahora.plus(BACKOFF.get(fallos - 1)));
            }
        }
    }
}
