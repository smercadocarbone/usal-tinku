package com.tinku.pagos.port;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
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

    /** Producción 2026-09-25: sin back_urls el comprador quedaba en MercadoPago. */
    @Test
    void creaPreferencia_conBackUrlsAPagarYAutoReturnEnHttps() throws Exception {
        AtomicReference<String> captor = new AtomicReference<>();
        HttpServer server = serverQueDevuelve("201", RESPUESTA_PREFERENCIA, captor);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(
                    baseUrl, "mp-token", null, "https://tinku.site/");

            cliente.crearPreferencia(pedido());

            JsonNode root = objectMapper.readTree(captor.get());
            String vuelta = "https://tinku.site/pagar?reserva=" + reservaId;
            assertThat(root.get("back_urls").get("success").asText()).isEqualTo(vuelta);
            assertThat(root.get("back_urls").get("failure").asText()).isEqualTo(vuelta);
            assertThat(root.get("back_urls").get("pending").asText()).isEqualTo(vuelta);
            assertThat(root.get("auto_return").asText()).isEqualTo("approved");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void creaPreferencia_urlPublicaSinHttps_backUrlsSinAutoReturn() throws Exception {
        AtomicReference<String> captor = new AtomicReference<>();
        HttpServer server = serverQueDevuelve("201", RESPUESTA_PREFERENCIA, captor);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            new MercadoPagoClientHttp(baseUrl, "mp-token", null, "http://localhost:3000")
                    .crearPreferencia(pedido());

            JsonNode root = objectMapper.readTree(captor.get());
            assertThat(root.get("back_urls").get("success").asText())
                    .isEqualTo("http://localhost:3000/pagar?reserva=" + reservaId);
            assertThat(root.has("auto_return")).isFalse();
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

    // ------------------------------------------------ getPago (T-M5-03 webhook)

    private HttpServer serverPagos(String context, String status, String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(context, exchange -> responder(exchange, status, body));
        server.start();
        return server;
    }

    @Test
    void getPago_parseaPagoAprobado() throws Exception {
        String respuesta = """
                {"id":2000000000,"status":"approved","status_detail":"accredited",
                 "external_reference":"%s","transaction_amount":150.00,"currency_id":"ARS"}
                """.formatted(reservaId);
        HttpServer server = serverPagos("/v1/payments/pago-123", "200", respuesta);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            PagoMercadoPago pago = cliente.getPago("pago-123");

            assertThat(pago.mpPaymentId()).isEqualTo("pago-123");
            assertThat(pago.aprobado()).isTrue();
            assertThat(pago.externalReference()).isEqualTo(reservaId.toString());
            assertThat(pago.monto()).isEqualByComparingTo(new BigDecimal("150.00"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPago_pagoPendiente_noCuentaComoAprobado() throws Exception {
        String respuesta = """
                {"id":2000000001,"status":"pending","status_detail":"pending_waiting_transfer",
                 "external_reference":"%s","transaction_amount":150.00}
                """.formatted(reservaId);
        HttpServer server = serverPagos("/v1/payments/pago-456", "200", respuesta);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            PagoMercadoPago pago = cliente.getPago("pago-456");

            assertThat(pago.aprobado()).isFalse();
            assertThat(pago.externalReference()).isEqualTo(reservaId.toString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPago_sinAccessToken_noLlamaAlProvider_yFallaConMensajeClaro() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/pago-789", exchange -> {
            hits.incrementAndGet();
            responder(exchange, "200", "{}");
        });
        server.start();
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "", null);

            assertThatThrownBy(() -> cliente.getPago("pago-789"))
                    .isInstanceOf(MercadoPagoNoConfiguradoException.class);
            assertThat(hits.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPago_errorDelProvider_seTraduceANoDisponible() throws Exception {
        HttpServer server = serverPagos("/v1/payments/pago-500", "500", "{\"error\":\"boom\"}");
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            assertThatThrownBy(() -> cliente.getPago("pago-500"))
                    .isInstanceOf(MercadoPagoNoDisponibleException.class);
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------ reembolso (T-M5-07)

    private HttpServer serverPara(String path, String status, AtomicInteger hits,
                                  AtomicReference<String> method, AtomicReference<String> body,
                                  AtomicReference<String> authHeader) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            if (hits != null) hits.incrementAndGet();
            if (method != null) method.set(exchange.getRequestMethod());
            if (body != null) body.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            if (authHeader != null) authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            responder(exchange, status, null);
        });
        server.start();
        return server;
    }

    @Test
    void reembolsoPago_posteaBodyVacio_conAuth_alEndpointDeRefunds() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = serverPara("/v1/payments/pago-reembolso/refunds", "201",
                new AtomicInteger(), method, body, authHeader);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            cliente.reembolsarPago("pago-reembolso");

            // FR-PAG-009: body VACÍO — el reembolso total hace que MP devuelva
            // también su propia comisión (costo real cero para Tinku).
            assertThat(method.get()).isEqualTo("POST");
            assertThat(body.get()).isEqualTo("{}");
            assertThat(authHeader.get()).isEqualTo("Bearer mp-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reembolsoPago_errorDelProvider_seTraduceANoDisponible() throws Exception {
        HttpServer server = serverPara("/v1/payments/pago-reembolso-500/refunds", "500",
                new AtomicInteger(), null, null, null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            assertThatThrownBy(() -> cliente.reembolsarPago("pago-reembolso-500"))
                    .isInstanceOf(MercadoPagoNoDisponibleException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reembolsoPago_sinAccessToken_noLlamaAlProvider_yFallaConMensajeClaro() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = serverPara("/v1/payments/pago-reembolso-2/refunds", "201",
                hits, null, null, null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "", null);

            assertThatThrownBy(() -> cliente.reembolsarPago("pago-reembolso-2"))
                    .isInstanceOf(MercadoPagoNoConfiguradoException.class);
            assertThat(hits.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------- reembolso parcial (T-M5-08)

    @Test
    void reembolsoPagoParcial_posteaElMonto_conAuth_alEndpointDeRefunds() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        HttpServer server = serverPara("/v1/payments/pago-parcial/refunds", "201",
                new AtomicInteger(), method, body, authHeader);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            cliente.reembolsarPagoParcial("pago-parcial", new BigDecimal("60.00"));

            // FR-PAG-010: reembolso TOTAL lleva body vacío ({}); el PARCIAL es el
            // único que manda amount explícito, y solo lo invoca el flujo manual
            // de disputa de M8.
            assertThat(method.get()).isEqualTo("POST");
            assertThat(body.get()).isEqualTo("{\"amount\":60.00}");
            assertThat(authHeader.get()).isEqualTo("Bearer mp-token");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reembolsoPagoParcial_errorDelProvider_seTraduceANoDisponible() throws Exception {
        HttpServer server = serverPara("/v1/payments/pago-parcial-500/refunds", "500",
                new AtomicInteger(), null, null, null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "mp-token", null);

            assertThatThrownBy(() -> cliente.reembolsarPagoParcial("pago-parcial-500", new BigDecimal("10.00")))
                    .isInstanceOf(MercadoPagoNoDisponibleException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reembolsoPagoParcial_sinAccessToken_noLlamaAlProvider_yFallaConMensajeClaro() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = serverPara("/v1/payments/pago-parcial-2/refunds", "201",
                hits, null, null, null);
        try {
            String baseUrl = "http://localhost:" + server.getAddress().getPort();
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(baseUrl, "", null);

            assertThatThrownBy(() -> cliente.reembolsarPagoParcial("pago-parcial-2", new BigDecimal("10.00")))
                    .isInstanceOf(MercadoPagoNoConfiguradoException.class);
            assertThat(hits.get()).isZero();
        } finally {
            server.stop(0);
        }
    }

    /** R2: la preferencia vence con la Reserva y excluye efectivo (ticket, cajero). */
    @Test
    void r2_creaPreferencia_conVencimientoYSinEfectivo() throws Exception {
        AtomicReference<String> captor = new AtomicReference<>();
        HttpServer server = serverQueDevuelve("201", RESPUESTA_PREFERENCIA, captor);
        try {
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(
                    "http://localhost:" + server.getAddress().getPort(), "mp-token", null);
            java.time.Instant vence = java.time.Instant.parse("2026-09-25T15:15:00Z");
            cliente.crearPreferencia(new PreferenciaRequest(reservaId, new BigDecimal("150.00"),
                    new BigDecimal("22.50"), "Sesión de tutoría Tinku", vence));

            JsonNode root = objectMapper.readTree(captor.get());
            assertThat(root.get("expires").asBoolean()).isTrue();
            assertThat(root.get("expiration_date_to").asText()).isEqualTo("2026-09-25T12:15:00.000-03:00");
            assertThat(root.get("payment_methods").get("excluded_payment_types").findValuesAsString("id"))
                    .containsExactlyInAnyOrder("ticket", "atm");
        } finally {
            server.stop(0);
        }
    }

    /** R2: la búsqueda por external_reference parsea el id numérico de MP y filtra lo incompleto. */
    @Test
    void r2_buscaPagosPorReferencia() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/payments/search", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            responder(exchange, "200", """
                    {"results":[
                      {"id":123456789,"status":"approved","external_reference":"%s","transaction_amount":150.0},
                      {"id":null,"status":"approved"}]}
                    """.formatted(reservaId));
        });
        server.start();
        try {
            MercadoPagoClientHttp cliente = new MercadoPagoClientHttp(
                    "http://localhost:" + server.getAddress().getPort(), "mp-token", null);
            java.util.List<PagoMercadoPago> pagos = cliente.buscarPagosPorReferencia(reservaId.toString());

            assertThat(query.get()).contains("external_reference=" + reservaId);
            assertThat(pagos).singleElement().satisfies(p -> {
                assertThat(p.mpPaymentId()).isEqualTo("123456789");
                assertThat(p.aprobado()).isTrue();
                assertThat(p.monto()).isEqualByComparingTo("150");
            });
        } finally {
            server.stop(0);
        }
    }
}
