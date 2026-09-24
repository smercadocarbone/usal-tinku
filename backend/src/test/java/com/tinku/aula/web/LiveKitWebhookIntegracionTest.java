package com.tinku.aula.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Webhook de LiveKit (T-M3-02) de punta a punta: firma HS256 sobre el body y
 * registro de joins contra la Reserva de la sesión. Se construyen los datos
 * mínimos por repositorio (no se repite el flujo completo de reservas — ese ya
 * queda cubierto por ReservasFlujosIntegracionTest); acá se ejercita el contrato
 * del webhook: firma válida → 200 + timestamp de join; firma inválida → 401;
 * sala desconocida → 200 sin efectos (LiveKit reintenta los no-2xx).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class LiveKitWebhookIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;

    @Value("${tinku.livekit.api-key}") String apiKey;
    @Value("${tinku.livekit.api-secret}") String apiSecret;

    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 30_000_000 + CONTADOR_DNIS.getAndIncrement());
    }

    private record EscenarioWebhook(String nombreSala, UUID tutorId, UUID beneficiarioId) {
    }

    private EscenarioWebhook prepararSesionConReserva() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico(), "Ana");
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico(), "Pablo");
        Usuario menor = new Usuario();
        menor.setDni(dniUnico());
        menor.setNombre("Sofia");
        menor.setApellido("Perez");
        menor.setFechaNacimiento(LocalDate.of(2015, 7, 20));
        menor.setTipo(TipoUsuario.MENOR);
        menor.setPasswordHash("hash");
        menor.setAdultoResponsable(ar);
        usuarioRepository.save(menor);

        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(menor);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        String nombreSala = "sala-webhook-" + CONTADOR_DNIS.get();
        SesionAprendizaje sesion = new SesionAprendizaje();
        sesion.setReservaId(reserva.getId());
        sesion.setLivekitRoomId(nombreSala);
        sesionRepository.save(sesion);

        return new EscenarioWebhook(nombreSala, tutor.getId(), menor.getId());
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String dni, String nombre) {
        Usuario u = new Usuario();
        u.setDni(dni);
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

    @Test
    void webhookFirmado_registraJoinDelTutorYDelEstudiante() throws Exception {
        EscenarioWebhook esc = prepararSesionConReserva();

        // Tutor entra (identity = id del usuario).
        String bodyTutor = cuerpo("participant_joined", esc.tutorId().toString(), esc.nombreSala());
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType("application/webhook+json")
                        .header("Authorization", "Bearer " + firmar(bodyTutor.getBytes(StandardCharsets.UTF_8)))
                        .content(bodyTutor))
                .andExpect(status().isOk());

        // Estudiante (beneficiario) entra (identity = id del usuario; el DNI ya no se acepta, AUD-003).
        String bodyEst = cuerpo("participant_joined", esc.beneficiarioId().toString(), esc.nombreSala());
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType("application/webhook+json")
                        .header("Authorization", "Bearer " + firmar(bodyEst.getBytes(StandardCharsets.UTF_8)))
                        .content(bodyEst))
                .andExpect(status().isOk());

        SesionAprendizaje sesion = sesionRepository.findByLivekitRoomId(esc.nombreSala()).orElseThrow();
        assertThat(sesion.getTutorJoinedAt()).isNotNull();
        assertThat(sesion.getEstudianteJoinedAt()).isNotNull();
    }

    /** Limpieza de AUD-003 (FASE3-03): el DNI como identity era compatibilidad con tokens
     *  previos a FASE 1 (TTL 1 h, vencidos hace rato). Ya no registra el join. */
    @Test
    void aud003_identityConDni_noRegistraElJoin() throws Exception {
        EscenarioWebhook esc = prepararSesionConReserva();

        String body = cuerpo("participant_joined", dniDel(esc.tutorId()), esc.nombreSala());
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType("application/webhook+json")
                        .header("Authorization", "Bearer " + firmar(body.getBytes(StandardCharsets.UTF_8)))
                        .content(body))
                .andExpect(status().isOk());

        assertThat(sesionRepository.findByLivekitRoomId(esc.nombreSala()).orElseThrow().getTutorJoinedAt()).isNull();
    }

    @Test
    void webhookConFirmaInvalida_queda401() throws Exception {
        String body = cuerpo("participant_joined", "quien-sea", "sala-x");
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer token-que-no-es-jwt")
                        .content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookDeSalaDesconocida_ack2xxSinEfectos() throws Exception {
        String body = cuerpo("participant_joined", "alguien", "sala-inexistente");

        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType("application/webhook+json")
                        .header("Authorization", "Bearer "
                                + firmar(body.getBytes(StandardCharsets.UTF_8)))
                        .content(body))
                .andExpect(status().isOk());
        assertThat(sesionRepository.findByLivekitRoomId("sala-inexistente")).isNotPresent();
    }

    private String dniDel(UUID userId) {
        return usuarioRepository.findById(userId).orElseThrow().getDni();
    }

    private String cuerpo(String evento, String identidad, String sala) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("id", "evt_" + UUID.randomUUID());
        payload.put("event", evento);
        payload.putObject("room").put("name", sala).put("sid", "RO_abc");
        payload.putObject("participant").put("identity", identidad).put("name", identidad);
        return objectMapper.writeValueAsString(payload);
    }

    private String firmar(byte[] body) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(apiKey)
                .claim("sha256", Base64.getEncoder().encodeToString(hash))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}