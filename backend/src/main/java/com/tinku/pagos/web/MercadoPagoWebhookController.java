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
        String dataIdQuery = request.getParameter("data.id");
        if (!verificador.esFirmaValida(xSignature, xRequestId, dataIdQuery)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode notificacion = objectMapper.readTree(request.getInputStream().readAllBytes());
        // NOTA: el manifest firma el data.id del QUERY PARAM (lowercased); el body
        // lo repite. Para procesar usamos query si viene, sino el del body.
        String dataIdBody = notificacion.path("data").path("id").asText(null);
        String mpPaymentId = (dataIdQuery != null && !dataIdQuery.isBlank())
                ? dataIdQuery
                : dataIdBody;
        if (mpPaymentId == null || mpPaymentId.isBlank()
                || !"payment".equals(notificacion.path("type").asText())) {
            // No es una notificación de pago (hay otros topics): ack sin efecto.
            return ResponseEntity.ok().build();
        }

        escrowService.procesarPagoAprobado(mpPaymentId);
        return ResponseEntity.ok().build();
    }
}