package com.tinku.identidad.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tinku.identidad.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AUD-008 (auditoria 2026-09-21): el token de reset de contrasenia y el DNI
 * del usuario no pueden aparecer en el log — el endpoint que dispara la
 * notificacion es publico, y cualquiera con acceso de lectura a los logs
 * podria tomar cualquier cuenta con esos dos datos.
 */
class NotificadorResetPasswordLogTest {

    private static final String TOKEN_PLANO = "token-secreto-123";
    private static final String DNI = "30123456";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(NotificadorResetPasswordLog.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void notificarNoLoguearElTokenNiElDni() {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setDni(DNI);

        new NotificadorResetPasswordLog().notificar(usuario, TOKEN_PLANO);

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN_PLANO);
            assertThat(event.getFormattedMessage()).doesNotContain(DNI);
        }
    }
}
