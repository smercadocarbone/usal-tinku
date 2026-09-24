package com.tinku.aula.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.LiveKitWebhookService;
import com.tinku.aula.LiveKitWebhookVerificador;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Webhook de LiveKit (T-M3-02): registra quién se unió a la sala. Ruta pública
 * (SecurityConfig) porque la autenticación ES la firma HS256 + hash del body, no
 * el JWT de Tinku. LiveKit reintenta los no-2xx, así que el controller devuelve
 * 2xx siempre que la firma sea válida y 401 si no lo es (fail-closed).
 */
@RestController
@RequestMapping("/api/webhooks/livekit")
public class LiveKitWebhookController {

    private final LiveKitWebhookVerificador verificador;
    private final LiveKitWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public LiveKitWebhookController(LiveKitWebhookVerificador verificador,
                                    LiveKitWebhookService webhookService,
                                    ObjectMapper objectMapper) {
        this.verificador = verificador;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> recibir(HttpServletRequest request,
                                        @RequestHeader(value = "Authorization", required = false)
                                        String authorization) throws IOException {
        byte[] cuerpo = request.getInputStream().readAllBytes();
        if (!verificador.esFirmaValida(authorization, cuerpo)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        JsonNode evento = objectMapper.readTree(cuerpo);
        String nombreEvento = evento.path("event").asText();
        String sala = evento.path("room").path("name").asText();
        switch (nombreEvento) {
            case "participant_joined" -> webhookService.registrarJoin(sala,
                    evento.path("participant").path("identity").asText());
            case "participant_left" -> webhookService.registrarSalida(sala,
                    evento.path("participant").path("identity").asText());
            case "room_finished" -> webhookService.registrarSalaTerminada(sala);
            default -> { /* evento de LiveKit que no usamos: 2xx igual (reintentos) */ }
        }
        return ResponseEntity.ok().build();
    }
}