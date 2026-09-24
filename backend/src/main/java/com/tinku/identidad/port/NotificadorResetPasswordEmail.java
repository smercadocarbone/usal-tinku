package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;
import com.tinku.shared.email.EnviadorEmail;
import com.tinku.shared.email.MensajeEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * "Olvidé mi contraseña" por email (FASE2-03, ADR-000-06). Reemplaza al
 * {@code NotificadorResetPasswordLog} provisorio de AUD-008.
 *
 * <ul>
 *   <li>El token viaja SOLO en el email: nunca en el log ni en el outbox de
 *       notificaciones (un token en la base sería el mismo agujero de AUD-008).
 *       Por eso no hay reintentos persistidos: si el envío falla, el usuario pide otro.</li>
 *   <li>Se manda después del commit y fuera del hilo del request: la respuesta del
 *       endpoint tarda lo mismo exista o no el DNI (FR-ID-018, sin oráculo de tiempos).</li>
 *   <li>Sin proveedor configurado o sin email del usuario: solo un log sin token.</li>
 * </ul>
 */
@Component
public class NotificadorResetPasswordEmail implements NotificadorResetPassword {

    private static final Logger log = LoggerFactory.getLogger(NotificadorResetPasswordEmail.class);

    private final EnviadorEmail enviador;
    private final String urlPublica;
    private final Executor ejecutor;

    @Autowired
    public NotificadorResetPasswordEmail(EnviadorEmail enviador,
                                         @Value("${tinku.app.url-publica}") String urlPublica) {
        this(enviador, urlPublica, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "email-reset-password");
            t.setDaemon(true);
            return t;
        }));
    }

    NotificadorResetPasswordEmail(EnviadorEmail enviador, String urlPublica, Executor ejecutor) {
        this.enviador = enviador;
        this.urlPublica = urlPublica.replaceAll("/+$", "");
        this.ejecutor = ejecutor;
    }

    @Override
    public void notificar(Usuario usuario, String tokenPlano) {
        if (!enviador.configurado() || usuario.getEmail() == null || usuario.getEmail().isBlank()) {
            log.info("Recuperación de contraseña solicitada para usuario {} sin canal de email disponible",
                    usuario.getId());
            return;
        }
        MensajeEmail mensaje = new MensajeEmail(usuario.getEmail(), "Tinku: cambiá tu contraseña", """
                Hola %s:

                Pediste cambiar tu contraseña de Tinku. Entrá a este enlace para elegir una nueva:

                %s/resetear-password?token=%s

                El enlace vence en 1 hora y sirve una sola vez. Si no lo pediste vos, ignorá este email:
                tu contraseña sigue siendo la misma.
                """.formatted(usuario.getNombre(), urlPublica, tokenPlano));
        Runnable envio = () -> {
            try {
                enviador.enviar(mensaje);
            } catch (RuntimeException e) {
                log.warn("No se pudo mandar el email de recuperación al usuario {}: {}", usuario.getId(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Solo si el token quedó guardado: un email con un token revertido no sirve.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutor.execute(envio);
                }
            });
        } else {
            ejecutor.execute(envio);
        }
    }
}
