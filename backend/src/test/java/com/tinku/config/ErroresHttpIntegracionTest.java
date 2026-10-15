package com.tinku.config;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.port.MatchingServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRANS-1 (pruebas en producción, 2026-10-14; M3C-10 H-3): los errores de validación salían como
 * 403 sin cuerpo porque el dispatch de error a {@code /error} no estaba permitido por Spring Security.
 *
 * <p>Usa un servidor real en un puerto aleatorio: MockMvc no hace el dispatch de error del
 * contenedor, así que con MockMvc el defecto no se reproduce.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ErroresHttpIntegracionTest {

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16")).withDatabaseName("tinku_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int puerto;
    @Autowired UsuarioRepository usuarioRepo;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;

    private final HttpClient http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    private HttpResponse<String> enviar(HttpRequest.Builder req) throws Exception {
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + puerto + path);
    }

    private Usuario tutor() {
        Usuario u = new Usuario();
        u.setDni(String.valueOf(80_000_000 + (int) (Math.random() * 9_000_000)));
        u.setNombre("Juan");
        u.setApellido("Prueba");
        u.setFechaNacimiento(LocalDate.of(1985, 1, 1));
        u.setTipo(TipoUsuario.TUTOR);
        u.setEmail(u.getDni() + "@test.tinku");
        u.setPasswordHash(passwordEncoder.encode("password123"));
        u.setEstadoCuenta(EstadoCuenta.ACTIVA);
        u = usuarioRepo.saveAndFlush(u);
        jdbc.update("INSERT INTO pagos.tarifas_tutor (tutor_id, precio_hora) VALUES (?, ?)", u.getId(),
                new BigDecimal("15000"));
        return u;
    }

    private String token(Usuario u) throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/usuarios/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"dni\":\"" + u.getDni() + "\",\"password\":\"password123\"}")));
        assertThat(r.statusCode()).isEqualTo(200);
        return objectMapper.readTree(r.body()).get("token").asText();
    }

    @Test
    void login_jsonMalFormado_400_conCuerpo() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/usuarios/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{no es json")));
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("\"error\"");
    }

    @Test
    void reserva_uuidMalFormado_400() throws Exception {
        String token = token(tutor());
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/reservas/no-es-un-uuid"))
                .header("Authorization", "Bearer " + token).GET());
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("no es válido");
    }

    @Test
    void horarios_sinFecha_400() throws Exception {
        Usuario t = tutor();
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(
                        uri("/api/tutores/" + t.getId() + "/horarios?duracionMinutos=60"))
                .header("Authorization", "Bearer " + token(t)).GET());
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("Falta el parámetro");
    }

    @Test
    void foto_masDe5MB_413() throws Exception {
        Usuario t = tutor();
        String limite = "----tinku" + UUID.randomUUID();
        byte[] grande = new byte[5 * 1024 * 1024 + 512 * 1024]; // > max-file-size (5MB), < max-request-size (6MB)
        byte[] cabecera = ("--" + limite + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"f.jpg\"\r\n"
                + "Content-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] cierre = ("\r\n--" + limite + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] cuerpo = new byte[cabecera.length + grande.length + cierre.length];
        System.arraycopy(cabecera, 0, cuerpo, 0, cabecera.length);
        System.arraycopy(grande, 0, cuerpo, cabecera.length, grande.length);
        System.arraycopy(cierre, 0, cuerpo, cabecera.length + grande.length, cierre.length);
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/tutores/me/foto"))
                .header("Authorization", "Bearer " + token(t))
                .header("Content-Type", "multipart/form-data; boundary=" + limite)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cuerpo)));
        assertThat(r.statusCode()).isEqualTo(413);
    }

    @Test
    void rutaProtegida_sinJwt_sigue403() throws Exception {
        HttpResponse<String> r = enviar(HttpRequest.newBuilder(uri("/api/usuarios/me")).GET());
        assertThat(r.statusCode()).isEqualTo(403);
        HttpResponse<String> e = enviar(HttpRequest.newBuilder(uri("/error")).GET());
        assertThat(e.body()).doesNotContain("password").doesNotContain("Exception");
    }
}
