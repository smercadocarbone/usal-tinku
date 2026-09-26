package com.tinku.pagos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Validación de firma del webhook de MercadoPago (T-M5-03). Los webhooks llegan
 * con:
 *
 * <pre>
 *   x-signature: ts=<epoch_millis>,v1=<hmac_hex>
 *   x-request-id: <uuid>            (opcional — par omitido del manifest si falta)
 *   ?data.id=<mp_payment_id>        (query param opcional, misma info que el body)
 * </pre>
 *
 * El manifest es {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} con los
 * pares ausentes omitidos y {@code data.id} en minúsculas, y la firma es
 * {@code HMAC-SHA256(secret, manifest)} en hexadecimal comparada en tiempo
 * constante contra {@code v1} (verificado contra la doc oficial de MP en la
 * sesión de diseño de este chunk).
 *
 * Mismo patrón de seguridad que {@code LiveKitWebhookVerificador}: la
 * autenticación ES la firma del proveedor (SecurityConfig hace la ruta pública).
 * Fail-closed (T-000-06): sin {@code tinku.mercadopago.webhook-secret}
 * configurado o ante cualquier error de parseo/firma, el webhook se rechaza —
 * una notificación no firmada/indescifrable jamás debe confirmar un pago. La
 * tolerancia anti-replay sobre {@code ts} evita que un mensaje capturado se
 * reinyecte más tarde.
 */
@Component
public class MercadoPagoWebhookVerificador {

    static final Duration TOLERANCIA_REPLAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(MercadoPagoWebhookVerificador.class);

    private final String webhookSecret;

    public MercadoPagoWebhookVerificador(
            @Value("${tinku.mercadopago.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean esFirmaValida(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return rechazar("MP_WEBHOOK_SECRET sin configurar", dataId);
        }
        if (xSignature == null || xSignature.isBlank()) {
            return rechazar("sin header x-signature", dataId);
        }
        try {
            Map<String, String> partes = parsearHeader(xSignature);
            String ts = partes.get("ts");
            String v1 = partes.get("v1");
            if (ts == null || v1 == null || v1.isBlank()) {
                return rechazar("x-signature sin ts o v1", dataId);
            }
            long tsMillis = aMillis(ts);
            long desfasajeMillis = Math.abs(System.currentTimeMillis() - tsMillis);
            if (desfasajeMillis > TOLERANCIA_REPLAY.toMillis()) {
                return rechazar("ts fuera de la tolerancia anti-replay (" + desfasajeMillis / 1000 + " s)", dataId);
            }
            String manifest = manifest(dataId, xRequestId, ts);
            if (constantTimeEquals(hmacSha256Hex(webhookSecret, manifest), v1)) {
                return true;
            }
            // Aviso IPN (?id=&topic=, el formato viejo): MP arma el manifest con el data.id de
            // la URL y omite los pares ausentes, así que firma sin id (producción, 2026-09-26:
            // todos los IPN se rechazaban). No afloja nada: el id sigue sin ser de confianza y
            // EscrowService consulta el pago real a MP antes de confirmar cualquier cosa.
            String sinId = manifest(null, xRequestId, ts);
            if (dataId != null && constantTimeEquals(hmacSha256Hex(webhookSecret, sinId), v1)) {
                return true;
            }
            // El manifest no tiene secretos (id, request-id, ts): se loguea para poder comparar.
            return rechazar("la firma no coincide (manifest " + manifest + ") — ¿MP_WEBHOOK_SECRET es "
                    + "de la misma aplicación que MP_ACCESS_TOKEN?", dataId);
        } catch (IllegalArgumentException e) {
            return rechazar("x-signature ilegible", dataId);
        }
    }

    /** Un rechazo silencioso era imposible de diagnosticar en producción: se deja el motivo
     *  (nunca el secreto ni la firma) en el log. */
    private boolean rechazar(String motivo, String dataId) {
        LOG.warn("Webhook de MercadoPago rechazado: {} (data.id={})", motivo, dataId);
        return false;
    }

    private Map<String, String> parsearHeader(String xSignature) {
        Map<String, String> partes = new HashMap<>();
        for (String par : xSignature.split(",")) {
            int eq = par.indexOf('=');
            if (eq == -1) {
                continue;
            }
            partes.put(par.substring(0, eq).trim(), par.substring(eq + 1).trim());
        }
        return partes;
    }

    /** Canonical string: valores ausentes = par omitido; data.id en minúsculas. */
    private String manifest(String dataId, String xRequestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.toLowerCase()).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return manifest.toString();
    }

    /** Los docs de MP muestran ts en millis; versiones viejas en segundos. Normaliza. */
    private long aMillis(String ts) {
        long valor = Long.parseLong(ts);
        return valor < 1_000_000_000_000L ? valor * 1000L : valor;
    }

    private String hmacSha256Hex(String secret, String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private boolean constantTimeEquals(String esperado, String recibido) {
        return java.security.MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }
}