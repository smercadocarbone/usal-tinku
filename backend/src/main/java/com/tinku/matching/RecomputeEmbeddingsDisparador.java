package com.tinku.matching;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pide el recompute de embeddings (contrato 2c) cuando un Tutor cambia sus temas.
 * Sin esto nadie lo llamaba y un Tutor real que elegía temas nunca aparecía en la
 * búsqueda semántica (producción, 2026-09-25).
 *
 * Después del commit (el servicio Python lee la base: antes del commit no vería los
 * temas nuevos) y en un hilo propio (el recompute embebe todos los perfiles; el PUT
 * no espera). Un solo hilo: los pedidos se encolan en vez de pisarse. Si el servicio
 * está caído se loguea y listo — no es plata ni seguridad (AGENTS §1.3): el próximo
 * cambio de temas, o el recompute manual del runbook, lo pone al día.
 */
@Component
public class RecomputeEmbeddingsDisparador {

    private static final Logger LOG = LoggerFactory.getLogger(RecomputeEmbeddingsDisparador.class);

    private final MatchingServiceClient matchingClient;
    private final ExecutorService hilo = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "recompute-embeddings");
        t.setDaemon(true);
        return t;
    });

    public RecomputeEmbeddingsDisparador(MatchingServiceClient matchingClient) {
        this.matchingClient = matchingClient;
    }

    public void dispararDespuesDelCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    encolar();
                }
            });
        } else {
            encolar();
        }
    }

    private void encolar() {
        hilo.submit(() -> {
            try {
                matchingClient.recomputarEmbeddings();
            } catch (RuntimeException e) {
                LOG.warn("No se pudo recalcular los embeddings de matching ({}); el Tutor aparece "
                        + "en la búsqueda con el próximo recompute.", e.getClass().getSimpleName());
            }
        });
    }

    /**
     * Al arrancar, el servicio Python puede estar todavía cargando el modelo (en Coolify los
     * servicios levantan juntos): se reintenta unas veces con espera en el hilo de fondo.
     * No es plata ni seguridad (AGENTS §1.3); si todos fallan, lo pone al día el próximo
     * cambio de temas.
     */
    public void dispararConReintentos(int intentos, java.time.Duration espera) {
        hilo.submit(() -> {
            for (int i = 1; i <= intentos; i++) {
                try {
                    matchingClient.recomputarEmbeddings();
                    LOG.info("Embeddings de matching recalculados al arrancar (intento {})", i);
                    return;
                } catch (RuntimeException e) {
                    if (i == intentos) {
                        LOG.warn("No se pudo recalcular los embeddings al arrancar tras {} intentos ({})",
                                intentos, e.getClass().getSimpleName());
                        return;
                    }
                    try {
                        Thread.sleep(espera.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }

    @PreDestroy
    void cerrar() {
        hilo.shutdown();
    }
}
