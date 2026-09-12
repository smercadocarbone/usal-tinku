package com.tinku.admin.service;

import com.tinku.admin.web.SaludInfraestructuraResponse;
import com.tinku.admin.web.SaludInfraestructuraResponse.ServiceStatus;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.StubOcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Datos REALES de la pestaña "Salud de Infraestructura" (M8). Cada servicio se
 * sondea en vivo al pedir el endpoint (nunca cacheado): base de datos con una
 * conexión JDBC real, matching con su /health real, MercadoPago con una llamada
 * autenticada real; OCR y LiveKit reportan su compuerta de configuración (ambos
 * son fail-closed sin credenciales, el mismo criterio del resto del backend).
 *
 * {@code isTestMode} deriva del perfil (dev/test = prueba; un perfil {@code prod}
 * apagaría casi todo por diseño). {@code hasSeedData} mira la base REAL: los
 * tutores de la semilla de dev usan el dominio {@code @test.tinku}.
 */
@Service
public class SaludInfraestructuraService {

    private static final int TIMEOUT_PROBE_MS = 2_000;

    private final Environment environment;
    private final JdbcTemplate jdbc;
    private final DataSource datasource;
    private final OcrService ocrService;

    private final RestClient clienteMatching;
    private final RestClient clienteMercadoPago;
    private final String mpAccessToken;
    private final String livekitBaseUrl;
    private final String livekitApiKey;
    private final String livekitApiSecret;

    public SaludInfraestructuraService(Environment environment,
                                       JdbcTemplate jdbc,
                                       DataSource datasource,
                                       OcrService ocrService,
                                       @Value("${tinku.matching-service.base-url:}") String matchingBaseUrl,
                                       @Value("${tinku.mercadopago.base-url:https://api.mercadopago.com}") String mpBaseUrl,
                                       @Value("${tinku.mercadopago.access-token:}") String mpAccessToken,
                                       @Value("${tinku.livekit.base-url:}") String livekitBaseUrl,
                                       @Value("${tinku.livekit.api-key:}") String livekitApiKey,
                                       @Value("${tinku.livekit.api-secret:}") String livekitApiSecret) {
        this.environment = environment;
        this.jdbc = jdbc;
        this.datasource = datasource;
        this.ocrService = ocrService;
        this.clienteMatching = clienteConTimeout(matchingBaseUrl);
        this.clienteMercadoPago = clienteConTimeout(mpBaseUrl);
        this.mpAccessToken = mpAccessToken;
        this.livekitBaseUrl = livekitBaseUrl;
        this.livekitApiKey = livekitApiKey;
        this.livekitApiSecret = livekitApiSecret;
    }

    public SaludInfraestructuraResponse datos() {
        Instant ahora = Instant.now();
        return new SaludInfraestructuraResponse(
                !environment.acceptsProfiles("prod"),
                hasSeedData(),
                ocrEngine(ahora),
                mercadoPago(ahora),
                liveKit(ahora),
                iaMatching(ahora),
                database(ahora));
    }

    // ------------------------------------------------------------- servicios

    private ServiceStatus database(Instant ahora) {
        long t0 = System.nanoTime();
        try (Connection connection = datasource.getConnection()) {
            if (!connection.isValid(2)) {
                return ServiceStatus.of("Base de datos", "offline", null, ahora);
            }
            return ServiceStatus.of("Base de datos", "operational",
                    ((System.nanoTime() - t0) / 1_000_000), ahora);
        } catch (SQLException e) {
            return ServiceStatus.of("Base de datos", "offline", null, ahora);
        }
    }

    /** /health real del proceso Python de matching (T-000-08). Sin base URL
     * configurada el cliente no existe y el servicio reporta degradado. */
    private ServiceStatus iaMatching(Instant ahora) {
        if (clienteMatching == null) {
            return ServiceStatus.of("Motor de Matching", "degraded", null, ahora);
        }
        long t0 = System.nanoTime();
        try {
            int status = clienteMatching.get().uri("/health")
                    .exchange((request, response) -> {
                        response.getBody().close();
                        return response.getStatusCode().value();
                    });
            long latencia = (System.nanoTime() - t0) / 1_000_000;
            return ServiceStatus.of("Motor de Matching",
                    status == 200 ? "operational" : "degraded", latencia, ahora);
        } catch (RestClientException e) {
            return ServiceStatus.of("Motor de Matching", "offline", null, ahora);
        }
    }

    /** Estado real de la credencial + conectividad: con token consulta pagos; sin
     * token degradado (fail-closed); 401/403 = credencial rechazada; sin red =
     * offline. */
    private ServiceStatus mercadoPago(Instant ahora) {
        if (mpAccessToken == null || mpAccessToken.isBlank()) {
            return ServiceStatus.of("MercadoPago", "degraded", null, ahora);
        }
        long t0 = System.nanoTime();
        try {
            int status = clienteMercadoPago.get().uri("/v1/payments/search?limit=1")
                    .header("Authorization", "Bearer " + mpAccessToken)
                    .exchange((request, response) -> {
                        response.getBody().close();
                        return response.getStatusCode().value();
                    });
            long latencia = (System.nanoTime() - t0) / 1_000_000;
            String estado = status == 200 ? "operational" : "degraded";
            return ServiceStatus.of("MercadoPago", estado, latencia, ahora);
        } catch (RestClientException e) {
            return ServiceStatus.of("MercadoPago", "offline", null, ahora);
        }
    }

    /** LiveKit es fail-closed sin credenciales (mismo criterio que el resto de
     * las integraciones): sin base+clave+secreto no hay token posible. No se
     * abre un WebSocket en cada sondeo (no sería "un vistazo"). */
    private ServiceStatus liveKit(Instant ahora) {
        boolean configurada = !livekitBaseUrl.isBlank()
                && !livekitApiKey.isBlank() && !livekitApiSecret.isBlank();
        return ServiceStatus.of("LiveKit", configurada ? "operational" : "degraded", null, ahora);
    }

    /** OCR: en dev/test el bean es el stub (OCR falso) → degraded; en prod el
     * real (Tesseract) → operational. In-process: sin latencia de red. */
    private ServiceStatus ocrEngine(Instant ahora) {
        boolean real = !(ocrService instanceof StubOcrService);
        return ServiceStatus.of("Motor OCR", real ? "operational" : "degraded", null, ahora);
    }

    // ----------------------------------------------------------- banderas

    private boolean hasSeedData() {
        Integer conSemilla = jdbc.queryForObject(
                "SELECT count(*) FROM identidad.usuarios WHERE email LIKE '%@test.tinku'",
                Integer.class);
        return conSemilla != null && conSemilla > 0;
    }

    private static RestClient clienteConTimeout(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_PROBE_MS);
        factory.setReadTimeout(TIMEOUT_PROBE_MS);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}