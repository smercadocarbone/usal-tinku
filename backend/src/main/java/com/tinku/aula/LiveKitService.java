package com.tinku.aula;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.SecretKey;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Integracion con LiveKit (T-M3-02): creacion de sala + generacion de tokens.
 *
 * Tinku es 1:1 (FR-AULA-001), asi que el alcance minimo de M3 es: una sala
 * creada de forma diferida en T-5 (job de M3-03) y dos tokens de acceso
 * (Tutor + Estudiante). El contrato con LiveKit es el documentado por el propio
 * provider: tokens JWT HS256 firmados con el API secret (claims iss/sub/nbf/exp
 * + grant "video") y sala via la API Twirp POST /twirp/livekit.RoomService/CreateRoom.
 *
 * HTTP/1.1 forzado a nivel cliente: mismo motivo que MatchingServiceClient — un
 * upgrade h2 clear-text no es soportado por infraestructuras que solo exponen
 * HTTP/1.1 y rompe el request con body.
 *
 * Sin credenciales configuradas (T-000-06 en Fase 0) el backend arranca igual
 * y la falla ocurre al USAR el servicio, con un mensaje claro — misma filosofia
 * que MatchingServiceHealthCheck: no tumbar el proceso por una integracion
 * que todavia no se usa (los jobs de sala recien existen en T-M3-03).
 */
@Component
public class LiveKitService {

    private static final String PATH_CREATE_ROOM = "/twirp/livekit.RoomService/CreateRoom";

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final SecretKey secretKey;
    private final long tokenTtlSegundos;

    public LiveKitService(
            @Value("${tinku.livekit.base-url}") String baseUrl,
            @Value("${tinku.livekit.api-key}") String apiKey,
            @Value("${tinku.livekit.api-secret}") String apiSecret,
            @Value("${tinku.livekit.token-ttl-segundos}") long tokenTtlSegundos) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.secretKey = apiKey.isBlank() || apiSecret.isBlank()
                ? null
                : Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
        this.tokenTtlSegundos = tokenTtlSegundos;
        String restBase = baseUrl
                .replaceFirst("^wss://", "https://")
                .replaceFirst("^ws://", "http://");
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.restClient = RestClient.builder()
                .baseUrl(restBase)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
    }

    /**
     * {@code POST /api/sesiones/{id}/token} devuelve el token del participante.
     * Sobre LiveKit, el token solo vale para la sala nombrada: el participante
     * no puede abrir otra sala con este token, ni crear ni administrar salas
     * (roomCreate/roomAdmin en false) — solo unirse a la sala indicada.
     *
     * <p>{@code identidad} es el UUID del usuario, nunca el DNI: LiveKit difunde el
     * identity a todos los participantes de la sala (AUD-003). Lo que el otro
     * participante ve en pantalla es {@code nombreVisible} (claim {@code name}).
     */
    public String generarTokenParticipante(String identidad, String nombreVisible, String nombreSala) {
        verificarConfigurado();
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(apiKey)
                .subject(identidad)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(tokenTtlSegundos)))
                .claim("nbf", now.getEpochSecond())
                .claim("name", nombreVisible)
                .claim("video", new VideoClaim(nombreSala, true, false, false))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Creacion diferida de sala (job de T-5, T-M3-03) vía la API Twirp de
     * LiveKit. Requiere un token de servidor con permiso {@code roomCreate}.
     */
    public String crearSala(String nombreSala) {
        verificarConfigurado();
        try {
            RoomCreada room = restClient.post()
                    .uri(PATH_CREATE_ROOM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + tokenServidor())
                    .body("{\"name\":" + jsonQuote(nombreSala) + "}")
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (request, response) -> {
                                throw new IllegalStateException(
                                        "LiveKit rechazo la creacion de la sala "
                                                + nombreSala + " (" + response.getStatusCode() + ").");
                            })
                    .body(RoomCreada.class);
            return room.name();
        } catch (RestClientException e) {
            throw new IllegalStateException("LiveKit no responde al crear la sala "
                    + nombreSala + ": " + e.getMessage(), e);
        }
    }

    /** Token de servidor para llamadas a la API (no para participantes). */
    private String tokenServidor() {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(apiKey)
                .subject(apiKey)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(tokenTtlSegundos)))
                .claim("nbf", now.getEpochSecond())
                .claim("video", new VideoClaim("", false, true, false))
                .signWith(secretKey)
                .compact();
    }

    /** URL del servidor LiveKit (wss://...) que el frontend necesita para conectarse. */
    public String getBaseUrl() {
        return baseUrl;
    }

    private void verificarConfigurado() {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "LiveKit no configurado: defini LIVEKIT_URL, LIVEKIT_API_KEY y "
                            + "LIVEKIT_API_SECRET (T-000-06, Fase 0).");
        }
    }

    private static String jsonQuote(String valor) {
        return "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Grant "video" de un access token de LiveKit (claves camelCase exactas). */
    public record VideoClaim(String room, boolean roomJoin, boolean roomCreate, boolean roomAdmin) {
    }

    /** Respuesta de CreateRoom: lo importante es el nombre (el cliente conecta por nombre). */
    public record RoomCreada(String name, String sid) {
    }
}