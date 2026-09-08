package com.tinku.pagos.port;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinku.pagos.service.MercadoPagoNoConfiguradoException;
import com.tinku.pagos.service.MercadoPagoNoDisponibleException;
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
 * Marketplace: la preferencia lleva {@code marketplace_fee} (BR-PAG-01, 15%)
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

    private final RestClient restClient;
    private final String accessToken;
    private final String notificationUrl;

    public MercadoPagoClientHttp(
            @Value("${tinku.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${tinku.mercadopago.access-token:}") String accessToken,
            @Value("${tinku.mercadopago.notification-url:}") String notificationUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.accessToken = accessToken;
        this.notificationUrl = notificationUrl;
    }

    @Override
    public PreferenciaPago crearPreferencia(PreferenciaRequest request) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new MercadoPagoNoConfiguradoException();
        }
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
        return new PreferenciaPago(respuesta.id(), respuesta.initPoint());
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
                notificacion);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MpPreferenciaRequest(
            List<MpItem> items,
            @JsonProperty("marketplace_fee") BigDecimal marketplaceFee,
            @JsonProperty("external_reference") String externalReference,
            @JsonProperty("notification_url") String notificationUrl) {
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
}