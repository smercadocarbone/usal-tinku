package com.tinku.pagos.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.pagos.MercadoPagoWebhookVerificador;
import com.tinku.pagos.service.EscrowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Webhook de MercadoPago (T-M5-03, Plan M5 §4). Ruta pública (SecurityConfig)
 * porque la autenticación ES la firma {@code x-signature} (HMAC-SHA256 sobre el
 * manifest), no el JWT de Tinku. Mismo patrón que el webhook de LiveKit —
 * {@code LiveKitWebhookController}: sin firma válida → 401 (fail-closed), con
 * firma válida → ack 2xx siempre, para que el provider no reintente en loop.
 *
 * El abuso de ORIGEN (payload fabricado que no viene de MP) lo frena el
 * verificador; el abuso de CONTENIDO (pago que no corresponde a la Reserva) lo
 * frena {@code EscrowService.procesarPagoAprobado} con la reconciliación contra
 * el pago real y el guard de monto.
 */
@RestController
@RequestMapping("/api/webhooks/mercadopago")
public class MercadoPagoWebhookController {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoWebhookVerificador verificador;
    private final EscrowService escrowService;
    private final ObjectMapper objectMapper;

    public MercadoPagoWebhookController(MercadoPagoWebhookVerificador verificador,
                                        EscrowService escrowService,
                                        ObjectMapper objectMapper) {
        this.verificador = verificador;
        this.escrowService = escrowService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> recibir(HttpServletRequest request,
                                        @RequestHeader(value = "x-signature", required = false)
                                        String xSignature,
                                        @RequestHeader(value = "x-request-id", required = false)
                                        String xRequestId) throws IOException {
        // El cuerpo se lee ANTES de verificar: MP no siempre agrega ?data.id= a la URL
        // (producción, 2026-09-25) y en ese caso firma el data.id del cuerpo. Leerlo acá
        // no le da confianza a nada: si el id no es el firmado, la firma no coincide.
        JsonNode notificacion = leerCuerpo(request);
        String mpPaymentId = idDelPago(request, notificacion);
        if (!verificador.esFirmaValida(xSignature, xRequestId, mpPaymentId)) {
            // Qué formato mandó MP (nombres de parámetros y campos, sin valores sensibles):
            // producción mostró avisos sin data.id y hacía falta verlo para entenderlos.
            LOG.warn("Webhook de MP rechazado. Query: {} · campos del cuerpo: {}",
                    request.getParameterMap().keySet(), camposDe(notificacion));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (mpPaymentId == null || mpPaymentId.isBlank() || !esDePago(request, notificacion)) {
            // No es una notificación de pago (merchant_order u otros topics): ack sin efecto.
            return ResponseEntity.ok().build();
        }

        escrowService.procesarPagoAprobado(mpPaymentId);
        return ResponseEntity.ok().build();
    }

    /**
     * El id del pago según el formato del aviso. Webhooks: {@code ?data.id=} o
     * {@code data.id} en el cuerpo. IPN (el formato viejo, que MP sigue mandando a la
     * notification_url): {@code ?id=&topic=payment} o {@code resource} en el cuerpo
     * (un id o una URL que termina en el id).
     */
    private static String idDelPago(HttpServletRequest request, JsonNode n) {
        for (String candidato : new String[]{
                request.getParameter("data.id"),
                n.path("data").path("id").asText(null),
                request.getParameter("id"),
                ultimoSegmento(n.path("resource").asText(null))}) {
            if (candidato != null && !candidato.isBlank()) {
                return candidato;
            }
        }
        return null;
    }

    private static boolean esDePago(HttpServletRequest request, JsonNode n) {
        return "payment".equals(n.path("type").asText(null))
                || "payment".equals(request.getParameter("type"))
                || "payment".equals(request.getParameter("topic"))
                || "payment".equals(n.path("topic").asText(null));
    }

    private static String ultimoSegmento(String resource) {
        if (resource == null || resource.isBlank()) {
            return null;
        }
        String s = resource.replaceAll("/+$", "");
        return s.substring(s.lastIndexOf('/') + 1);
    }

    private static java.util.List<String> camposDe(JsonNode n) {
        java.util.List<String> campos = new java.util.ArrayList<>();
        n.fieldNames().forEachRemaining(campos::add);
        return campos;
    }

    /** Cuerpo vacío o que no es JSON = notificación sin id (el verificador decide). */
    private JsonNode leerCuerpo(HttpServletRequest request) throws IOException {
        byte[] bytes = request.getInputStream().readAllBytes();
        if (bytes.length == 0) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}