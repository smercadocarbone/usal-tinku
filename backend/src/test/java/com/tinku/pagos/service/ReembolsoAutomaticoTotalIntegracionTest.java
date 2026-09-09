package com.tinku.pagos.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T-M5-10 — "el reembolso automático nunca deja un monto parcial": una regla
 * automática del módulo (acá {@code sesion.interrumpida}) dispara el reembolso
 * a través de la CADENA REAL — {@code ReembolsoProveedorMercadoPago} →
 * {@code MercadoPagoClientHttp} (sin mocks de esos dos) contra un stub HTTP
 * local — y se verifica:
 *
 * <ul>
 *   <li>el POST a {@code /v1/payments/{id}/refunds} va con body {@code {}}
 *       (FR-PAG-009): el parcial requeriría un {@code amount} en el body, que la
 *       función única de reembolso no admite (T-M5-07);</li>
 *   <li>la transacción queda {@code reembolsado} con {@code montoBruto} ÍNTEGRO
 *       (15000) — no existe ninguna "salida parcial" del escrow automático;</li>
 *   <li>el evento repetido no re-embolsa (idempotencia, una sola operación
 *       completa).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ReembolsoAutomaticoTotalIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    /** Stub de MercadoPago levantado ANTES del contexto (puerto asignado por el OS). */
    private static final HttpServer STUB_MP = iniciarStubMercadoPago();

    private static final AtomicInteger HITS_REEMBOLSO = new AtomicInteger();
    private static final AtomicReference<String> METHODO = new AtomicReference<>();
    private static final AtomicReference<String> URI = new AtomicReference<>();
    private static final AtomicReference<String> CUERPO = new AtomicReference<>();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("tinku.mercadopago.base-url",
                () -> "http://localhost:" + STUB_MP.getAddress().getPort());
        registry.add("tinku.mercadopago.access-token", () -> "mp-token");
    }

    @AfterAll
    static void pararStub() {
        STUB_MP.stop(0);
    }

    @org.junit.jupiter.api.BeforeEach
    void resetStub() {
        HITS_REEMBOLSO.set(0);
        METHODO.set(null);
        URI.set(null);
        CUERPO.set(null);
    }

    private static HttpServer iniciarStubMercadoPago() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/refunds")) {
                    HITS_REEMBOLSO.incrementAndGet();
                    METHODO.set(exchange.getRequestMethod());
                    URI.set(path);
                    CUERPO.set(new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8));
                    responder(exchange, 201, null);
                } else {
                    // Cualquier otra llamada del módulo falla ruidoso: si el
                    // reembolso saliera por otro lado, el test TIENE que enterarse.
                    responder(exchange, 404, null);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo levantar el stub de MercadoPago", e);
        }
    }

    private static void responder(HttpExchange exchange, int code, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(code, -1);
        } else {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @Autowired ApplicationEventPublisher events;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired TransaccionRepository transaccionRepository;

    @MockBean LiberacionProveedor liberacion;

    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 45_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(dniUnico());
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(LocalDate.of(1990, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    private record Escena(UUID reservaId, String mpPaymentId) {
    }

    private Escena escenaConEscrow() {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(ar);
        reserva.setTutor(guardarUsuario(TipoUsuario.TUTOR, "Pablo"));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("pago-total-" + CONTADOR_DNIS.get());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepository.save(transaccion);

        return new Escena(reserva.getId(), transaccion.getMpPaymentId());
    }

    // ---------------------------------------------------------------- tests

    @Test
    void reembolsoAutomatico_reembolsaElTotalNuncaUnParcial() {
        Escena e = escenaConEscrow();

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        // La regla automática salió por la cadena REAL y llegó a MP como un
        // reembolso TOTAL: POST /v1/payments/{id}/refunds con body vacío {}
        // (FR-PAG-009). Un parcial habría exigido un amount en el body, que
        // ReembolsoProveedorMercadoPago no expone (T-M5-07).
        assertThat(HITS_REEMBOLSO.get()).isEqualTo(1);
        assertThat(METHODO.get()).isEqualTo("POST");
        assertThat(URI.get()).isEqualTo("/v1/payments/" + e.mpPaymentId() + "/refunds");
        assertThat(CUERPO.get()).isEqualTo("{}");

        // El escrow quedó reembolsado con el monto ÍNTEGRO: no hay ninguna
        // salida parcial automática posible (no existe estado "reembolsado_parcial").
        Transaccion t = transaccionRepository.findByReservaId(e.reservaId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(t.getMontoBruto()).isEqualByComparingTo("15000");
        assertThat(t.getComisionPlataforma()).isEqualByComparingTo("2250.00");
        assertThat(t.getLiberarAt()).isNull();
        // Y no se rozó el dinero del Tutor al mismo tiempo.
        verify(liberacion, never()).liberarAlTutor(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reembolsoAutomatico_esIdempotente_unaSolaOperacionCompleta() {
        Escena e = escenaConEscrow();

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));
        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        // El reenvío del evento no re-embolsa: una sola operación TOTAL (no se
        // puede "partir" el reembolso en dos).
        assertThat(HITS_REEMBOLSO.get()).isEqualTo(1);
        Transaccion t = transaccionRepository.findByReservaId(e.reservaId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
    }
}