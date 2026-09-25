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

import java.net.http.HttpClient;

/** Cliente HTTP de {@code /oauth/token} (ADR-M5-02). HTTP/1.1 forzado, como el resto de MP. */
@Component
public class MercadoPagoOAuthClientHttp implements MercadoPagoOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public MercadoPagoOAuthClientHttp(
            @Value("${tinku.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${tinku.mercadopago.oauth.client-id:}") String clientId,
            @Value("${tinku.mercadopago.oauth.client-secret:}") String clientSecret) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public TokensMp canjearCodigo(String code, String codeVerifier, String redirectUri) {
        return pedir(new MpTokenRequest(clientId, clientSecret, "authorization_code", code, redirectUri,
                codeVerifier, null));
    }

    @Override
    public TokensMp refrescar(String refreshToken) {
        return pedir(new MpTokenRequest(clientId, clientSecret, "refresh_token", null, null, null, refreshToken));
    }

    private TokensMp pedir(MpTokenRequest cuerpo) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new MercadoPagoNoConfiguradoException();
        }
        MpTokenRespuesta r;
        try {
            r = restClient.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, res) -> {
                        throw new MercadoPagoNoDisponibleException();
                    })
                    .body(MpTokenRespuesta.class);
        } catch (RestClientException e) {
            throw new MercadoPagoNoDisponibleException();
        }
        if (r == null || r.accessToken() == null || r.userId() == null) {
            throw new MercadoPagoNoDisponibleException();
        }
        return new TokensMp(r.accessToken(), r.refreshToken(), String.valueOf(r.userId()), r.publicKey(),
                r.expiresIn() == null ? 0 : r.expiresIn());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MpTokenRequest(
            @JsonProperty("client_id") String clientId,
            @JsonProperty("client_secret") String clientSecret,
            @JsonProperty("grant_type") String grantType,
            String code,
            @JsonProperty("redirect_uri") String redirectUri,
            @JsonProperty("code_verifier") String codeVerifier,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    private record MpTokenRespuesta(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("user_id") Object userId,
            @JsonProperty("public_key") String publicKey,
            @JsonProperty("expires_in") Long expiresIn) {
    }
}
