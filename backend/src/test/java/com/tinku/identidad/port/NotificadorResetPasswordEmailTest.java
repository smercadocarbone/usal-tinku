package com.tinku.identidad.port;

import com.tinku.identidad.model.Usuario;
import com.tinku.shared.email.EmailEnvioException;
import com.tinku.shared.email.EnviadorEmail;
import com.tinku.shared.email.MensajeEmail;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** FASE2-03 + ADR-000-06: "olvidé mi contraseña" por email (antes, solo un log). */
class NotificadorResetPasswordEmailTest {

    private final EnviadorEmail enviador = mock(EnviadorEmail.class);
    // Ejecutor síncrono: en producción el envío va fuera del hilo del request.
    private final NotificadorResetPasswordEmail notificador =
            new NotificadorResetPasswordEmail(enviador, "https://tinku.site", Runnable::run);

    private Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setNombre("Ana");
        u.setEmail(email);
        return u;
    }

    @Test
    void conEmailYProveedor_mandaElLinkConElToken() {
        when(enviador.configurado()).thenReturn(true);

        notificador.notificar(usuario("ana@example.com"), "tok-123");

        ArgumentCaptor<MensajeEmail> msg = ArgumentCaptor.forClass(MensajeEmail.class);
        verify(enviador).enviar(msg.capture());
        assertThat(msg.getValue().para()).isEqualTo("ana@example.com");
        assertThat(msg.getValue().texto()).contains("https://tinku.site/resetear-password?token=tok-123");
    }

    @Test
    void sinProveedorConfigurado_noEnvia() {
        when(enviador.configurado()).thenReturn(false);

        notificador.notificar(usuario("ana@example.com"), "tok-123");

        verify(enviador, never()).enviar(any());
    }

    @Test
    void usuarioSinEmail_noEnvia() {
        when(enviador.configurado()).thenReturn(true);

        notificador.notificar(usuario(null), "tok-123");

        verify(enviador, never()).enviar(any());
    }

    @Test
    void fallaDelProveedor_noSePropaga() {
        when(enviador.configurado()).thenReturn(true);
        doThrow(new EmailEnvioException("caído")).when(enviador).enviar(any());

        assertThatCode(() -> notificador.notificar(usuario("ana@example.com"), "tok-123"))
                .doesNotThrowAnyException();
    }
}
