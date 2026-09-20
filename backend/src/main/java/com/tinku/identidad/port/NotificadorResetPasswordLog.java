package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación PROVISORIA de {@link NotificadorResetPassword}: deja el link
 * de recuperación en el log de la aplicación en vez de enviarlo por un canal
 * real. Deliberado — elegir proveedor de email/SMS transaccional es una
 * decisión de infraestructura con su propio ADR (AGENTS.md §2) que todavía no
 * se tomó, y no correspondía decidirla sola dentro de este cambio.
 *
 * Mientras esta sea la única implementación activa, el flujo de "olvidé mi
 * contraseña" solo es operable por quien tenga acceso a los logs del backend
 * (soporte/on-call) — es una limitación conocida, no un bug.
 */
@Component
public class NotificadorResetPasswordLog implements NotificadorResetPassword {

    private static final Logger log = LoggerFactory.getLogger(NotificadorResetPasswordLog.class);

    @Override
    public void notificar(Usuario usuario, String tokenPlano) {
        log.info("Recuperación de contraseña solicitada para usuario {} (dni {}). Token: {}",
                usuario.getId(), usuario.getDni(), tokenPlano);
    }
}
