package com.tinku.admin.notificacion;

import com.tinku.shared.notificacion.Notificador;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementación del puerto {@link Notificador}: una fila en {@code admin.notificaciones}
 * dentro de la transacción del llamador (outbox, FASE2-03). Si el tipo va por email, la
 * fila queda pendiente para {@link EnvioEmailNotificacionesService}; acá no se llama a
 * ningún proveedor ni se agenda nada, así un aviso nunca demora ni rompe el hecho que
 * lo origina.
 */
@Component
public class NotificadorOutbox implements Notificador {

    private final NotificacionRepository repo;

    public NotificadorOutbox(NotificacionRepository repo) {
        this.repo = repo;
    }

    @Override
    public void notificar(UUID destinatarioId, TipoNotificacion tipo, Map<String, String> datos) {
        // Contrato de outbox: sin la transacción del hecho, el aviso podría quedar huérfano.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Notificador.notificar exige la transacción del hecho que la origina.");
        }
        Notificacion n = new Notificacion();
        n.setDestinatarioId(destinatarioId);
        n.setTipo(tipo);
        n.setDatos(new HashMap<>(datos));
        if (tipo.porEmail()) {
            n.setProximoIntentoEmailAt(Instant.now());
        }
        repo.save(n);
    }
}
