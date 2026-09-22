package com.tinku.aula;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrato de la integracion con LiveKit (T-M3-02) verificado sin depender de
 * una cuenta real (T-000-06): el token se firma y se re-verifica con el mismo
 * secret (HS256 determinista), y la creacion de sala se prueba contra un stub
 * HTTP que emula la API Twirp — misma filosofia que MatchingServiceClientTest.
 */
class LiveKitServiceTest {

    private static final String API_KEY = "APItest123456789";
    private static final String API_SECRET = "0123456789012345678901234567890123456789"; // >= 32 bytes (HS256)
    private static final long TTL_SEGUNDOS = 3600;

    private static HttpServer serverOk;
    private static HttpServer serverError;
    private static int portOk;
    private static int portError;

    private static volatile String authHeaderRecibido;
    private static volatile String bodyRecibido;

    @BeforeAll
    static void startStubs() throws IOException {
        serverOk = HttpServer.create(new InetSocketAddress(0), 0);
        serverOk.createContext("/twirp/livekit.RoomService/CreateRoom", exchange -> {
            authHeaderRecibido = exchange.getRequestHeaders().getFirst("Authorization");
            bodyRecibido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] body = "{\"name\":\"sala-42\",\"sid\":\"RO_abc\",\"emptyTimeout\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        serverOk.start();
        portOk = serverOk.getAddress().getPort();

        serverError = HttpServer.create(new InetSocketAddress(0), 0);
        serverError.createContext("/twirp/livekit.RoomService/CreateRoom", exchange -> {
            byte[] body = "{\"code\":3,\"msg\":\"permission denied\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        serverError.start();
        portError = serverError.getAddress().getPort();
    }

    @AfterAll
    static void stopStubs() {
        serverOk.stop(0);
        serverError.stop(0);
    }

    @Test
    void tokenDeParticipanteSeVerificaYDeclaraLaSalaCorrecta() {
        LiveKitService service = new LiveKitService("http://localhost:" + portOk,
                API_KEY, API_SECRET, TTL_SEGUNDOS);

        String token = service.generarTokenParticipante("tutor-1", "Pablo", "sala-42");

        Claims cl = parsear(token);
        assertThat(cl.getIssuer()).isEqualTo(API_KEY);
        assertThat(cl.getSubject()).isEqualTo("tutor-1");
        assertThat(cl.getExpiration().getTime() - cl.getIssuedAt().getTime())
                .isEqualTo(TTL_SEGUNDOS * 1000);
        Map<?, ?> video = cl.get("video", Map.class);
        assertThat(video.get("room")).isEqualTo("sala-42");
        assertThat(video.get("roomJoin")).isEqualTo(true);
        assertThat(video.get("roomCreate")).isEqualTo(false);
        assertThat(video.get("roomAdmin")).isEqualTo(false);
    }

    @Test
    void tokenDeParticipanteLlevaElNombreVisibleEnElClaimName() {
        LiveKitService service = new LiveKitService("http://localhost:" + portOk,
                API_KEY, API_SECRET, TTL_SEGUNDOS);

        String token = service.generarTokenParticipante(
                "3f1c9a52-0000-0000-0000-000000000001", "Pablo", "sala-42");

        // AUD-003: el otro participante ve el name, no el identity (que es un UUID opaco).
        Claims cl = parsear(token);
        assertThat(cl.get("name", String.class)).isEqualTo("Pablo");
        assertThat(cl.getSubject()).isEqualTo("3f1c9a52-0000-0000-0000-000000000001");
    }

    @Test
    void crearSalaPosteaAlTwirpConTokenDeServidorYParseaLaRespuesta() {
        LiveKitService service = new LiveKitService("http://localhost:" + portOk,
                API_KEY, API_SECRET, TTL_SEGUNDOS);

        String nombre = service.crearSala("sala-42");

        assertThat(nombre).isEqualTo("sala-42");
        assertThat(bodyRecibido).contains("\"name\":\"sala-42\"");

        assertThat(authHeaderRecibido).startsWith("Bearer ");
        Map<?, ?> video = parsear(authHeaderRecibido.substring("Bearer ".length()))
                .get("video", Map.class);
        assertThat(video.get("roomCreate")).isEqualTo(true);
        assertThat(video.get("roomJoin")).isEqualTo(false);
    }

    @Test
    void crearSalaPropagaElErrorDeLiveKit() {
        LiveKitService service = new LiveKitService("http://localhost:" + portError,
                API_KEY, API_SECRET, TTL_SEGUNDOS);

        assertThatThrownBy(() -> service.crearSala("sala-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rechazo");
    }

    @Test
    void sinCredencialesFallaAlUsarloConMensajeClaro() {
        LiveKitService service = new LiveKitService("", "", "", TTL_SEGUNDOS);

        assertThatThrownBy(() -> service.crearSala("sala-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVEKIT_API_KEY");
        assertThatThrownBy(() -> service.generarTokenParticipante("x", "X", "sala-42"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIVEKIT_API_KEY");
    }

    private Claims parsear(String token) {
        SecretKey key = Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}