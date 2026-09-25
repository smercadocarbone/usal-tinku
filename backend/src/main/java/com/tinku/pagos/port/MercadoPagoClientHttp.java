package com.tinku.pagos.port;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinku.pagos.service.MercadoPagoNoConfiguradoException;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

/**
 * Cliente HTTP de MercadoPago (T-M5-02) via Checkout Pro en modalidad
 * Marketplace: la preferencia lleva {@code marketplace_fee} (BR-PAG-01, 27%)
 * para que el split quede definido al crear el pago (Plan M5 §3.1/§3.2) — al
 * liberar el escrow NO es una segunda transaccion.
 *
 * El match entre la preferencia y la Reserva va por {@code external_reference}
 * = id de la Reserva: es la clave que usara el webhook de M5-B para reconciliar
 * la notificacion de pago sin depender de que el cliente vuelva a la app.
 *
 * Sin access token configurado (T-000-06) el backend arranca igual y la falla
 * ocurre al USAR el servicio con mensaje claro — misma filosofia que
 * LiveKitService y MatchingServiceClient (no tumbar el proceso por una
 * integracion que el flujo de M4 todavia no exige).
 *
 * HTTP/1.1 forzado a nivel cliente: mismo motivo que MatchingServiceClient — un
 * upgrade h2 clear-text no es soportado por la infraestructura y rompe el
 * request con body.
 */
@Component
public class MercadoPagoClientHttp implements MercadoPagoClient {

    private static final String PATH_PREFERENCIAS = "/checkout/preferences";
    private static final String PATH_PAGOS = "/v1/payments/";

    private final RestClient restClient;
    private final String accessToken;
    private final String notificationUrl;
    private final String urlPublica;

    public MercadoPagoClientHttp(String baseUrl, String accessToken, String notificationUrl) {
        this(baseUrl, accessToken, notificationUrl, null);
    }

    @Autowired
    public MercadoPagoClientHttp(
            @Value("${tinku.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${tinku.mercadopago.access-token:}") String accessToken,
            @Value("${tinku.mercadopago.notification-url:}") String notificationUrl,
            @Value("${tinku.app.url-publica:}") String urlPublica) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
        this.urlPublica = urlPublica;
    }

    @Override
    public PreferenciaPago crearPreferencia(PreferenciaRequest request) {
        exigirTokenConfigurado();
        MpPreferenciaRespuesta respuesta;
        try {
            respuesta = restClient.post()
                    .uri(PATH_PREFERENCIAS)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo(request))
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .body(MpPreferenciaRespuesta.class);
        } catch (RestClientException ex) {
            // Conexion rechazada / timeout: mismo contrato que un error del provider.
            throw new MercadoPagoNoDisponibleException();
        }
        if (respuesta == null || respuesta.id() == null || respuesta.initPoint() == null) {
            throw new MercadoPagoNoDisponibleException();
        }
        return new PreferenciaPago(respuesta.id(), respuesta.initPoint(), false);
    }

    @Override
    public PagoMercadoPago getPago(String mpPaymentId) {
        exigirTokenConfigurado();
        MpPagoRespuesta respuesta;
        try {
            respuesta = restClient.get()
                    .uri(PATH_PAGOS + mpPaymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .body(MpPagoRespuesta.class);
        } catch (RestClientException ex) {
            throw new MercadoPagoNoDisponibleException();
        }
        if (respuesta == null || respuesta.status() == null) {
            throw new MercadoPagoNoDisponibleException();
        }
        return new PagoMercadoPago(mpPaymentId, respuesta.status(),
                respuesta.externalReference(), respuesta.transactionAmount());
    }

    @Override
    public List<PagoMercadoPago> buscarPagosPorReferencia(String externalReference) {
        exigirTokenConfigurado();
        MpBusquedaRespuesta respuesta;
        try {
            respuesta = restClient.get()
                    .uri(uri -> uri.path(PATH_PAGOS + "search")
                            .queryParam("external_reference", externalReference)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc")
                            .build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .body(MpBusquedaRespuesta.class);
        } catch (RestClientException ex) {
            throw new MercadoPagoNoDisponibleException();
        }
        if (respuesta == null || respuesta.results() == null) {
            return List.of();
        }
        return respuesta.results().stream()
                .filter(p -> p.id() != null && p.status() != null)
                .map(p -> new PagoMercadoPago(String.valueOf(p.id()), p.status(), p.externalReference(), p.transactionAmount()))
                .toList();
    }

    @Override
    public void reembolsarPago(String mpPaymentId) {
        exigirTokenConfigurado();
        try {
            restClient.post()
                    .uri(PATH_PAGOS + mpPaymentId + "/refunds")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MpReembolsoRequest(null))
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new MercadoPagoNoDisponibleException();
        }
    }

    /** Reembolso PARCIAL por disputa (FR-PAG-010, T-M5-08): el cuerpo lleva el
     * {@code amount} a devolver. Solo el flujo manual de M8 lo invoca. */
    @Override
    public void reembolsarPagoParcial(String mpPaymentId, BigDecimal monto) {
        exigirTokenConfigurado();
        try {
            restClient.post()
                    .uri(PATH_PAGOS + mpPaymentId + "/refunds")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MpReembolsoRequest(monto))
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new MercadoPagoNoDisponibleException();
        }
    }

    private void exigirTokenConfigurado() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new MercadoPagoNoConfiguradoException();
        }
    }

    /** Traduccion de la preferencia de dominio al JSON de /checkout/preferences
     * (snake_case: contrato del provider, no tocar). */
    private MpPreferenciaRequest cuerpo(PreferenciaRequest request) {
        String notificacion = (notificationUrl == null || notificationUrl.isBlank())
                ? null
                : notificationUrl;
        return new MpPreferenciaRequest(
                List.of(new MpItem(request.descripcion(), 1, request.montoBruto())),
                request.comisionPlataforma(),
                request.reservaId().toString(),
                notificacion,
                backUrls(request.reservaId()),
                autoReturn(),
                request.expiraAt() == null ? null : true,
                request.expiraAt() == null ? null : FECHA_MP.format(request.expiraAt()),
                // R2: el efectivo (Rapipago, Pago Fácil, cajero) se acredita después de la
                // ventana de 15 min y terminaría siempre en reembolso.
                new MpMediosDePago(List.of(new MpTipoExcluido("ticket"), new MpTipoExcluido("atm"))));
    }

    /** Formato de fechas de MP: ISO-8601 con milisegundos y offset (hora de Argentina). */
    private static final java.time.format.DateTimeFormatter FECHA_MP =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                    .withZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

    /** Sin back_urls el comprador se quedaba en MercadoPago después de pagar
     *  (producción, 2026-09-25). Vuelve a /pagar, que lee el estado que agrega MP. */
    private MpBackUrls backUrls(java.util.UUID reservaId) {
        if (urlPublica == null || urlPublica.isBlank()) {
            return null;
        }
        String vuelta = urlPublica.replaceAll("/+$", "") + "/pagar?reserva=" + reservaId;
        return new MpBackUrls(vuelta, vuelta, vuelta);
    }

    /** Volver solo apenas se aprueba. MP rechaza auto_return con back_urls que no son
     *  https (dev en localhost): ahí el comprador vuelve con el botón de MP. */
    private String autoReturn() {
        return urlPublica != null && urlPublica.startsWith("https://") ? "approved" : null;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MpPreferenciaRequest(
            List<MpItem> items,
            @JsonProperty("marketplace_fee") BigDecimal marketplaceFee,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("notification_url") String notificationUrl,
            @JsonProperty("back_urls") MpBackUrls backUrls,
            @JsonProperty("auto_return") String autoReturn,
            Boolean expires,
            @JsonProperty("expiration_date_to") String expirationDateTo,
            @JsonProperty("payment_methods") MpMediosDePago paymentMethods) {
    }

    private record MpMediosDePago(
            @JsonProperty("excluded_payment_types") List<MpTipoExcluido> excludedPaymentTypes) {
    }

    private record MpTipoExcluido(String id) {
    }

    private record MpBusquedaRespuesta(List<MpPagoBuscado> results) {
    }

    /** {@code id} llega como número en la API de MP; se acepta cualquier escalar. */
    private record MpPagoBuscado(
            Object id,
            String status,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount) {
    }

    private record MpBackUrls(String success, String failure, String pending) {
    }

    private record MpItem(
            String title,
            int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {
    }

    private record MpPreferenciaRespuesta(
            String id,
            @JsonProperty("init_point") String initPoint) {
    }

    private record MpPagoRespuesta(
            String status,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount) {
    }

    /** Cuerpo de POST /v1/payments/{id}/refunds (T-M5-07, Plan M5 §3.3): el
     * reembolso TOTAL lleva monto null → cuerpo vacío ({@code {}}), que es lo
     * que hace que MP devuelva además su propia comisión (costo cero Tinku). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MpReembolsoRequest(@JsonProperty("amount") BigDecimal amount) {
    }
}