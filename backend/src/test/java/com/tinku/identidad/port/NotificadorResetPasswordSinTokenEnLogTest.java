package com.tinku.identidad.port;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.email.EmailEnvioException;
import com.tinku.shared.email.EnviadorEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AUD-008 (auditoria 2026-09-21): el token de reset de contrasenia y el DNI
 * del usuario no pueden aparecer en el log — el endpoint que dispara la
 * notificacion es publico, y cualquiera con acceso de lectura a los logs
 * podria tomar cualquier cuenta con esos dos datos.
 *
 * FASE2-03: el notificador provisorio (solo log) se reemplazo por el de email; este
 * test sigue cubriendo AUD-008 en los dos caminos que loguean (sin canal y con falla
 * del proveedor).
 */
class NotificadorResetPasswordSinTokenEnLogTest {

    private static final String TOKEN_PLANO = "token-secreto-123";
    private static final String DNI = "30123456";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(NotificadorResetPasswordEmail.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    private Usuario usuario(String email) {
        Usuario usuario = new Usuario();
        usuario.setId(UUID.randomUUID());
        usuario.setDni(DNI);
        usuario.setEmail(email);
        return usuario;
    }

    private void sinTokenNiDni() {
        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN_PLANO);
            assertThat(event.getFormattedMessage()).doesNotContain(DNI);
        }
    }

    @Test
    void sinCanalDeEmail_noLogueaElTokenNiElDni() {
        EnviadorEmail enviador = mock(EnviadorEmail.class);
        when(enviador.configurado()).thenReturn(false);

        new NotificadorResetPasswordEmail(enviador, "https://tinku.site", Runnable::run)
                .notificar(usuario("ana@example.com"), TOKEN_PLANO);

        sinTokenNiDni();
    }

    @Test
    void conFallaDelProveedor_noLogueaElTokenNiElDni() {
        EnviadorEmail enviador = mock(EnviadorEmail.class);
        when(enviador.configurado()).thenReturn(true);
        doThrow(new EmailEnvioException("Resend respondió HTTP 500")).when(enviador).enviar(any());

        new NotificadorResetPasswordEmail(enviador, "https://tinku.site", Runnable::run)
                .notificar(usuario("ana@example.com"), TOKEN_PLANO);

        sinTokenNiDni();
    }

    @Test
    void notificarNoLoguearElTokenNiElDni() {
        EnviadorEmail enviador = mock(EnviadorEmail.class);
        when(enviador.configurado()).thenReturn(false);

        new NotificadorResetPasswordEmail(enviador, "https://tinku.site", Runnable::run)
                .notificar(usuario(null), TOKEN_PLANO);

        assertThat(appender.list).isNotEmpty();
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage()).doesNotContain(TOKEN_PLANO);
            assertThat(event.getFormattedMessage()).doesNotContain(DNI);
        }
    }
}
