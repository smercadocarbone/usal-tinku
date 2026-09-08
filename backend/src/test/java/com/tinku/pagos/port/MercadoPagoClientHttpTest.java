package com.tinku.pagos.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaRequest;
import com.tinku.pagos.service.MercadoPagoNoConfiguradoException;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el contrato HTTP hacia MercadoPago (T-M5-02) contra un stub local:
 * método/path, header de autorización, cuerpo JSON (items, marketplace_fee,
 * external_reference, notification_url si está configurada) y parseo de la
 * respuesta. No pega contra el provider real (ADR-M5-01).
 */
class MercadoPagoClientHttpTest {

    private static final String RESPUESTA_PREFERENCIA = """
            {"id":"pref-123","init_point":"https://www.mercadopago.com/mla/checkout/start?pref_id=pref-123",
             "sandbox_init_point":"https://sandbox.mercadopago.com/mla/checkout/pay?pref_id=pref-123"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID reservaId = UUID.randomUUID();

    private PreferenciaRequest pedido() {
        return new PreferenciaRequest(reservaId, new BigDecimal("150.00"),
                new BigDecimal("22.50"), "Sesión de tutoría Tinku");
    }

    private HttpServer serverQueDevuelve(String status, String body, AtomicReference<String> captorCuerpo)
            throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/checkout/preferences", exchange -> {
            if (captorCuerpo != null) {
                captorCuerpo.set(new String(exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8));
            }
            responder(exchange, status, body);
        });
        server.start();
        return server;
    }

    private void responder(HttpExchange exchange, String status, String body) throws IOException {
        int code = switch (status) {
            case "201" -> 201;
            case "500" -> 500;
            default -> 200;
        };
        if (body != null && !body.isBlank()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        } else {
            exchange.sendResponseHeaders(code, -1);
        }
        exchange.close();
    }

    @Test
    void creaPreferencia_conHeaderSplitYExternalReference() throws Exception {
        AtomicReference<String> captor = new AtomicReference<>();
        HttpServer server = serverQueDevuelve("201", RESPUESTA_PREFERENCIA, captor);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            PreferenciaPago preferencia = cliente.crearPreferencia(pedido());

            assertThat(preferencia.preferenceId()).isEqualTo("pref-123");
            assertThat(preferencia.initPoint())
                    .startsWith("https://www.mercadopago.com/mla/checkout");

            JsonNode root = objectMapper.readTree(captor.get());
            assertThat(root.get("items").get(0).get("title").asText())
                    .isEqualTo("Sesión de tutoría Tinku");
            assertThat(root.get("items").get(0).get("quantity").asInt()).isEqualTo(1);
            assertThat(root.get("items").get(0).get("unit_price").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(root.get("marketplace_fee").decimalValue())
                    .isEqualByComparingTo(new BigDecimal("22.50"));
            assertThat(root.get("external_reference").asText()).isEqualTo(reservaId.toString());
            assertThat(root.has("notification_url")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void creaPreferencia_incluyeNotificationUrlSiEstaConfigurada() throws Exception {
        AtomicReference<String> captor = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/checkout/preferences", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            captor.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            responder(exchange, "201", RESPUESTA_PREFERENCIA);
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(
                    baseUrl, "mp-token", "https://tinku.app/api/webhooks/mercadopago");

            cliente.crearPreferencia(pedido());

            assertThat(authHeader.get()).isEqualTo("Bearer mp-token");
            JsonNode root = objectMapper.readTree(captor.get());
            assertThat(root.get("notification_url").asText())
                    .isEqualTo("https://tinku.app/api/webhooks/mercadopago");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sinAccessToken_noLlamaAlProvider_yFallaConMensajeClaro() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/checkout/preferences", exchange -> {
            hits.incrementAndGet();
            responder(exchange, "201", RESPUESTA_PREFERENCIA);
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "", null);

            assertThatThrownBy(() -> cliente.crearPreferencia(pedido()))
                    .isInstanceOf(MercadoPagoNoConfiguradoException.class);
            assertThat(hits.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void errorDelProvider_seTraduceANoDisponible() throws Exception {
        HttpServer server = serverQueDevuelve("500", "{\"error\":\"boom\"}", null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            assertThatThrownBy(() -> cliente.crearPreferencia(pedido()))
                    .isInstanceOf(MercadoPagoNoDisponibleException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void respuestaSinIdNiInitPoint_noFabricaLink() throws Exception {
        HttpServer server = serverQueDevuelve("200", "{}", null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            assertThatThrownBy(() -> cliente.crearPreferencia(pedido()))
                    .isInstanceOf(MercadoPagoNoDisponibleException.class);
        } finally {
            server.stop(0);
        }
    }
}