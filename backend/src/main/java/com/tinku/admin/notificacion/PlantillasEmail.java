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
            case CLASE_RESERVADA -> new MensajeEmail(email, "Tinku: te reservaron una clase", """
                    Hola %s:

                    Te reservaron una clase de %s minutos para el %s (hora de Argentina). Ya está paga.

                    Mirá el detalle en:
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, n.getDatos().getOrDefault("duracion", "—"),
                    fecha(n.getDatos().get("horario")), urlPublica, n.getDatos().get("reservaId")));
            case CLASE_CANCELADA -> new MensajeEmail(email, "Tinku: se canceló una clase", """
                    Hola %s:

                    %s canceló la clase del %s (hora de Argentina). La devolución o el pago siguen la                     política de cancelación de Tinku.

                    Mirá el detalle en:
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, "tutor".equals(n.getDatos().get("canceladaPor")) ? "El tutor" : "El alumno",
                    fecha(n.getDatos().get("horario")), urlPublica, n.getDatos().get("reservaId")));
            case CLASE_REPROGRAMADA -> new MensajeEmail(email, "Tinku: cambiaron el horario de una clase", """
                    Hola %s:

                    La clase del %s pasó al %s (hora de Argentina), dentro de tus horarios disponibles.

                    Mirá el detalle en:
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, fecha(n.getDatos().get("horarioAnterior")),
                    fecha(n.getDatos().get("horario")), urlPublica, n.getDatos().get("reservaId")));
            case RECORDATORIO_CLASE -> new MensajeEmail(email, "Tinku: mañana tenés una clase", """
                    Hola %s:

                    Te recordamos que tenés una clase el %s (hora de Argentina). La sala se abre 5                     minutos antes.

                    Mirá el detalle en:
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), urlPublica,
                    n.getDatos().get("reservaId")));
            case CLASE_POR_EMPEZAR -> new MensajeEmail(email, "Tinku: tu clase empieza en 5 minutos", """
                    Hola %s:

                    Tu clase de las %s está por empezar y la sala ya está abierta.

                    Entrá desde:
                    %s/aula/%s
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), urlPublica,
                    n.getDatos().get("sesionId")));
            case CLASE_EMPEZO -> new MensajeEmail(email, "Tinku: tu clase ya empezó", """
                    Hola %s:

                    Tu clase de las %s ya empezó y todavía no entraste. Te están esperando.

                    Entrá ahora desde:
                    %s/aula/%s
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), urlPublica,
                    n.getDatos().get("sesionId")));
            case PAGO_LIBERADO -> new MensajeEmail(email, "Tinku: te liberamos el pago de una clase", """
                    Hola %s:

                    Liberamos el pago de una clase que diste. Lo ves en tu cuenta de MercadoPago y en:
                    %s/cuenta/cobros
                    """.formatted(nombre, urlPublica));
            case CREDENCIAL_REVISADA -> new MensajeEmail(email, "aprobada".equals(n.getDatos().get("resultado"))
                    ? "Tinku: aprobamos tu título" : "Tinku: no pudimos aprobar tu título", """
                    Hola %s:

                    %s

                    Mirá el estado de tu perfil en:
                    %s/cuenta
                    """.formatted(nombre, "aprobada".equals(n.getDatos().get("resultado"))
                    ? "Revisamos tu título y lo aprobamos. Ya podés aparecer en las búsquedas."
                    : "Revisamos tu título y no pudimos aprobarlo. Podés cargarlo de nuevo desde tu cuenta.",
                    urlPublica));
            case CAP_REVISADO -> new MensajeEmail(email, "Tinku: revisamos tu certificado de antecedentes", """
                    Hola %s:

                    %s

                    Mirá el estado en:
                    %s/cuenta
                    """.formatted(nombre, switch (n.getDatos().getOrDefault("resultado", "")) {
                        case "aprobado" -> "Lo aprobamos: ya podés dar clases a menores.";
                        case "rechazado" -> "No pudimos aprobarlo. Podés ver el detalle y volver a cargarlo desde tu cuenta.";
                        default -> "Necesita una revisión adicional del equipo. Te avisamos cuando esté.";
                    }, urlPublica));
            case NOTA_CLASE -> new MensajeEmail(email, "Tinku: el tutor te dejó una nota de la clase", """
                    Hola %s:

                    El tutor te dejó una nota sobre la clase de tu hijo o hija. La podés leer en:
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, urlPublica, n.getDatos().get("reservaId")));
            case REPROGRAMACION_PEDIDA -> new MensajeEmail(email, "Tinku: el tutor te propone otro horario", """
                    Hola %s:

                    El tutor no puede dar la clase del %s y te propone pasarla al %s.
                    Podés aceptar el horario nuevo o cancelar y recibir la devolución completa:
                    %s/cuenta/reservas/%s

                    Si no respondés hasta una hora antes de la clase, se cancela y te devolvemos el pago.
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), fecha(n.getDatos().get("horarioPropuesto")),
                    urlPublica, n.getDatos().get("reservaId")));
            case REPROGRAMACION_ACEPTADA -> new MensajeEmail(email, "Tinku: aceptaron el horario nuevo", """
                    Hola %s:

                    Aceptaron tu propuesta: la clase pasa al %s.
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, fecha(n.getDatos().get("horario")), urlPublica, n.getDatos().get("reservaId")));
            case REPROGRAMACION_RECHAZADA -> new MensajeEmail(email, "Tinku: la clase se canceló", """
                    Hola %s:

                    %s La clase del %s quedó cancelada y le devolvemos el pago a quien la pagó.
                    %s/cuenta/reservas/%s
                    """.formatted(nombre, "vencido".equals(n.getDatos().get("motivo"))
                            ? "Nadie respondió tu pedido de cambio de horario a tiempo."
                            : "El alumno prefirió cancelar en vez de pasar la clase al horario que propusiste.",
                    fecha(n.getDatos().get("horario")), urlPublica, n.getDatos().get("reservaId")));
            case VIDEO_REVISADO -> new MensajeEmail(email, "aprobado".equals(n.getDatos().get("resultado"))
                    ? "Tinku: tu video ya está en tu perfil" : "Tinku: no pudimos publicar tu video", """
                    Hola %s:

                    %s
                    %s/cuenta/perfil-tutor
                    """.formatted(nombre, "aprobado".equals(n.getDatos().get("resultado"))
                            ? "Revisamos tu video de presentación y ya se ve en tu perfil."
                            : "Revisamos tu video de presentación y no lo pudimos publicar. Motivo: "
                                    + n.getDatos().getOrDefault("motivo", "-") + ". Podés subir otro.",
                    urlPublica));
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
