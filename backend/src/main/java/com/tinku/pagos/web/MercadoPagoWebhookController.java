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
        String dataIdQuery = request.getParameter("data.id");
        String dataIdBody = notificacion.path("data").path("id").asText(null);
        String mpPaymentId = (dataIdQuery != null && !dataIdQuery.isBlank())
                ? dataIdQuery
                : dataIdBody;
        if (!verificador.esFirmaValida(xSignature, xRequestId, mpPaymentId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (mpPaymentId == null || mpPaymentId.isBlank()
                || !"payment".equals(notificacion.path("type").asText())) {
            // No es una notificación de pago (hay otros topics): ack sin efecto.
            return ResponseEntity.ok().build();
        }

        escrowService.procesarPagoAprobado(mpPaymentId);
        return ResponseEntity.ok().build();
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