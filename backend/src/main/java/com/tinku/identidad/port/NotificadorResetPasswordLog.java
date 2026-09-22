package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementación PROVISORIA de {@link NotificadorResetPassword}: deja
 * constancia en el log de la aplicación de que se solicitó un reset, en vez
 * de enviar el token por un canal real. Deliberado — elegir proveedor de
 * email/SMS transaccional es una decisión de infraestructura con su propio
 * ADR (AGENTS.md §2) que todavía no se tomó, y no correspondía decidirla
 * sola dentro de este cambio.
 *
 * AUD-008 (auditoría 2026-09-21, CRÍTICA): el log NO incluye el token en
 * claro ni el DNI del usuario. El endpoint que dispara este flujo
 * ({@code POST /api/usuarios/recuperar-password}) es público, así que
 * cualquiera con acceso de lectura a los logs (agregador, consola cloud,
 * soporte, un backup) podía tomar el control de cualquier cuenta —incluidas
 * las de menores y las de Admin— con solo el DNI, porque el atacante
 * controla cuándo se genera el token.
 *
 * Consecuencia asumida y deliberada: sin el token en el log, el flujo de
 * "olvidé mi contraseña" queda INOPERABLE — nadie puede completarlo, ni
 * siquiera soporte/on-call— hasta que exista un canal real de notificación
 * (FASE 2, Task 2.3, AUD-014). Es el resultado correcto de fail-closed en
 * este contexto: preferimos un flujo roto a un flujo que regala cuentas.
 */
@Component
public class NotificadorResetPasswordLog implements NotificadorResetPassword {

    private static final Logger log = LoggerFactory.getLogger(NotificadorResetPasswordLog.class);

    @Override
    public void notificar(Usuario usuario, String tokenPlano) {
        log.info("Recuperación de contraseña solicitada para usuario {}", usuario.getId());
    }
}
