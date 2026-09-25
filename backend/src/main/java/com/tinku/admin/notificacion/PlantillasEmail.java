package com.tinku.admin.notificacion;

import com.tinku.shared.email.MensajeEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Textos de los emails de cada {@code TipoNotificacion} (FASE2-03). Mismo criterio que
 * la bandeja: solo lo que el destinatario debe saber, y el detalle queda en la app.
 * Artículo II: nunca un dato del menor ni de lo detectado en un email.
 */
@Component
public class PlantillasEmail {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(AR);

    private final String urlPublica;

    public PlantillasEmail(@Value("${tinku.app.url-publica}") String urlPublica) {
        this.urlPublica = urlPublica.replaceAll("/+$", "");
    }

    public MensajeEmail para(Notificacion n, String email, String nombre) {
        return switch (n.getTipo()) {
            case KILLSWITCH_MENOR -> new MensajeEmail(email, "Tinku: cortamos una clase por seguridad", """
                    Hola %s:

                    Durante una clase de tu hijo o hija se activó el corte de seguridad de Tinku y la clase \
                    terminó en ese momento. El equipo de Tinku ya está revisando lo que pasó.

                    No tenés que hacer nada ahora. Podés ver el aviso en:
                    %s/cuenta/notificaciones
                    """.formatted(nombre, urlPublica));
            case DENUNCIA_RECIBIDA -> new MensajeEmail(email, "Tinku: recibiste una denuncia", """
                    Hola %s:

                    Recibimos una denuncia sobre tu cuenta. Tenés tiempo hasta el %s (hora de Argentina) \
                    para contar tu versión; el equipo de Tinku la tiene en cuenta antes de decidir.

                    Presentá tu descargo en:
                    %s/cuenta/seguridad
                    """.formatted(nombre, fecha(n.getDatos().get("descargoVenceAt")), urlPublica));
            case CLASE_CANCELADA_TUTOR_SIN_HABILITACION -> new MensajeEmail(email,
                    "Tinku: cancelamos una clase de tu hijo o hija", """
                    Hola %s:

                    Cancelamos la clase del %s (hora de Argentina) porque el tutor ya no está habilitado \
                    para dar clases a menores. Te devolvemos el total de lo que pagaste.

                    Podés buscar otro tutor en:
                    %s/buscar
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), urlPublica));
            case MP_CUENTA_DESCONECTADA -> new MensajeEmail(email,
                    "Tinku: volvé a conectar tu MercadoPago", """
                    Hola %s:

                    No pudimos renovar la conexión con tu cuenta de MercadoPago. Hasta que la vuelvas a \
                    conectar, los alumnos no pueden reservarte clases nuevas. Las que ya tenés siguen igual.

                    Conectala de nuevo en:
                    %s/cuenta/cobros
                    """.formatted(nombre, urlPublica));
        };
    }

    private static String fecha(String iso) {
        try {
            return FECHA.format(Instant.parse(iso));
        } catch (DateTimeParseException | NullPointerException e) {
            return "vencimiento del plazo";
        }
    }
}
