package com.tinku.reservas.web;

import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UX-05 §4: la Reserva que ve cada participante trae nombres, duración y las
 * acciones calculadas en el servidor (pagar, cancelar y qué pasa con la plata si
 * cancela ahora, {@code PoliticaCancelacion}). Así la UI no reimplementa reglas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ReservaVistaIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;

    private Usuario usuario(TipoUsuario tipo, String nombre, String apellido) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 43_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setFechaNacimiento(LocalDate.of(1988, 1, 1));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
        }
        return usuarioRepository.save(u);
    }

    private String token(Usuario u) {
        return "Bearer " + jwtUtil.generateToken(u);
    }

    /** Reserva de 90 min. Desde D6/AUD-020 la duración es de la Reserva y la vista no lee la
     *  franja: no hace falta crear una (antes se armaba desde la hora actual y, cerca de la
     *  medianoche, cruzaba el día y violaba el CHECK de franjas_disponibilidad). */
    private Reserva reserva(Usuario pagador, Usuario tutor, Instant horario, EstadoReserva estado) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(pagador);
        r.setTutor(tutor);
        r.definirHorario(horario, 90);
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(estado);
        return reservaRepository.save(r);
    }

    /** Mediodía en Argentina dentro de N días. */
    private Instant enDias(int dias) {
        return ZonedDateTime.now(AR).plusDays(dias).withHour(12).truncatedTo(ChronoUnit.HOURS).toInstant();
    }

    @Test
    void elPagadorVeNombresDuracionYQuePuedePagarYCancelarConReembolsoTotal() throws Exception {
        Usuario ana = usuario(TipoUsuario.ADULTO, "Ana", "Gómez");
        Usuario jorge = usuario(TipoUsuario.TUTOR, "Jorge", "Martínez");
        Reserva r = reserva(ana, jorge, enDias(3), EstadoReserva.PENDIENTE_PAGO);

        mvc.perform(get("/api/reservas/{id}", r.getId()).header("Authorization", token(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tutorNombre").value("Jorge"))
                .andExpect(jsonPath("$.tutorApellido").value("Martínez"))
                .andExpect(jsonPath("$.beneficiarioNombre").value("Ana"))
                .andExpect(jsonPath("$.duracionMinutos").value(90))
                .andExpect(jsonPath("$.pagoVenceAt").isNotEmpty())
                .andExpect(jsonPath("$.puedePagar").value(true))
                .andExpect(jsonPath("$.puedeCancelar").value(true))
                .andExpect(jsonPath("$.cancelarReembolsaTotal").value(true))
                .andExpect(jsonPath("$.dni").doesNotExist());

        // En la lista, el Tutor ve la misma clase pero no puede pagarla.
        mvc.perform(get("/api/reservas").header("Authorization", token(jorge)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].puedePagar").value(false))
                .andExpect(jsonPath("$[0].puedeCancelar").value(true));
    }

    @Test
    void confirmadaConMenosDe24hs_siCancelaElPagadorSeLePagaAlTutor_siCancelaElTutorSeReembolsa() throws Exception {
        Usuario ana = usuario(TipoUsuario.ADULTO, "Ana", "Pérez");
        Usuario jorge = usuario(TipoUsuario.TUTOR, "Jorge", "Ruiz");
        Reserva r = reserva(ana, jorge, Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MINUTES),
                EstadoReserva.CONFIRMADA);

        mvc.perform(get("/api/reservas/{id}", r.getId()).header("Authorization", token(ana)))
                .andExpect(jsonPath("$.puedePagar").value(false))
                .andExpect(jsonPath("$.pagoVenceAt").doesNotExist())
                .andExpect(jsonPath("$.puedeCancelar").value(true))
                .andExpect(jsonPath("$.cancelarReembolsaTotal").value(false));
        mvc.perform(get("/api/reservas/{id}", r.getId()).header("Authorization", token(jorge)))
                .andExpect(jsonPath("$.cancelarReembolsaTotal").value(true));
    }

    @Test
    void unaClaseFinalizadaNoOfreceNiPagarNiCancelar() throws Exception {
        Usuario ana = usuario(TipoUsuario.ADULTO, "Ana", "Sosa");
        Usuario jorge = usuario(TipoUsuario.TUTOR, "Jorge", "Paz");
        Reserva r = reserva(ana, jorge, enDias(-2), EstadoReserva.FINALIZADA);

        mvc.perform(get("/api/reservas/{id}", r.getId()).header("Authorization", token(ana)))
                .andExpect(jsonPath("$.puedePagar").value(false))
                .andExpect(jsonPath("$.puedeCancelar").value(false))
                .andExpect(jsonPath("$.cancelarReembolsaTotal").doesNotExist());
    }
}
