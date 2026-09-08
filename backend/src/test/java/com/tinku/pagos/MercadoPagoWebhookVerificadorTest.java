package com.tinku.pagos;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de la firma del webhook de MercadoPago (T-M5-03): manifest
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} (pares ausentes
 * omitidos, data.id en minúsculas) firmado con HMAC-SHA256 en hexadecimal contra
 * el secret. Cualquier desvío (otro secret, header inválido, ts viejo reinyectado,
 * o secret de webhook ausente) → rechazado. Falta closed: sin webhook-secret
 * configurado (T-000-06) nunca acepta.
 */
class MercadoPagoWebhookVerificadorTest {

    private static final String SECRET = "test-mercadopago-webhook-secret";
    private static final String OTRO_SECRET = "otro-secret-bien-distinto";

    private final MercadoPagoWebhookVerificador verificador =
            new MercadoPagoWebhookVerificador(SECRET);

    private long ahoraMs() {
        return Instant.now().toEpochMilli();
    }

    @Test
    void firmaValida_acepta() {
        long ts = ahoraMs();
        String firma = firmar(SECRET, ts, "123456789", "req-uuid-abc");

        assertThat(verificador.esFirmaValida(firma, "req-uuid-abc", "123456789")).isTrue();
    }

    @Test
    void sinRequestId_manifestLosOmitteYAcepta() {
        long ts = ahoraMs();
        String firma = firmar(SECRET, ts, "123456789", null);

        assertThat(verificador.esFirmaValida(firma, null, "123456789")).isTrue();
    }

    @Test
    void dataIdConMayusculas_seNormalizaAMinusculasYCoincide() {
        long ts = ahoraMs();
        // El manifest se computa sobre el id en minúsculas (es lo que firmó MP).
        String firma = firmar(SECRET, ts, "ABC123xyz", "req-uuid-abc");

        // El verificador vuelve a minimizar la misma entrada → el HMAC coincide.
        assertThat(verificador.esFirmaValida(firma, "req-uuid-abc", "ABC123xyz")).isTrue();
    }

    @Test
    void tsEnSegundos_seNormalizaAMillisYAcepta() {
        // Docs viejas de MP mandan ts en segundos; el verificador normaliza.
        long tsSegundos = ahoraMs() / 1000;
        String firma = firmarConTs(SECRET, String.valueOf(tsSegundos), "123456789", null);

        assertThat(verificador.esFirmaValida(firma, null, "123456789")).isTrue();
    }

    @Test
    void firmadoConOtroSecret_rechaza() {
        long ts = ahoraMs();
        String firma = firmar(OTRO_SECRET, ts, "123456789", "req-uuid-abc");

        assertThat(verificador.esFirmaValida(firma, "req-uuid-abc", "123456789")).isFalse();
    }

    @Test
    void tsViejo_replayRechazado() {
        long ts = ahoraMs() - 10 * 60 * 1000; // 10 min atrás > tolerancia de 5 min
        String firma = firmar(SECRET, ts, "123456789", null);

        assertThat(verificador.esFirmaValida(firma, null, "123456789")).isFalse();
    }

    @Test
    void headerMalFormado_rechaza() {
        assertThat(verificador.esFirmaValida("ts=123,v1=abc,sin-igual", null, "123")).isFalse();
        assertThat(verificador.esFirmaValida("v1=solo-este-par", null, "123")).isFalse();
        assertThat(verificador.esFirmaValida("123456789", null, "123")).isFalse();
    }

    @Test
    void sinXSignature_rechaza() {
        assertThat(verificador.esFirmaValida(null, "req-uuid", "123")).isFalse();
        assertThat(verificador.esFirmaValida("  ", "req-uuid", "123")).isFalse();
    }

    @Test
    void sinWebhookSecretConfigurado_failClosed() {
        MercadoPagoWebhookVerificador sinSecret = new MercadoPagoWebhookVerificador("");
        long ts = ahoraMs();
        String firma = firmar(SECRET, ts, "123456789", null);

        assertThat(sinSecret.esFirmaValida(firma, null, "123456789")).isFalse();
    }

    private String firmar(String secret, long tsMillis, String dataId, String xRequestId) {
        return firmarConTs(secret, String.valueOf(tsMillis), dataId, xRequestId);
    }

    private String firmarConTs(String secret, String ts, String dataId, String xRequestId) {
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifest.append("id:").append(dataId.toLowerCase()).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return "ts=" + ts + ",v1=" + hmacHex(secret, manifest.toString());
    }

    private String hmacHex(String secret, String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(texto.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}