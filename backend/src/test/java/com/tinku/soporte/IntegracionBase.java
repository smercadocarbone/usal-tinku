package com.tinku.soporte;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.port.MatchingServiceClient;
import com.tinku.matching.port.ReputacionSignalProvider;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración de la remediación de las pruebas en producción
 * (docs/operacion/PLAN_remediacion_hallazgos.md). Crea usuarios directo en la base, como el
 * seed, y entra por el login real: no depende del registro con OCR.
 *
 * <p>El contenedor es uno por JVM (patrón singleton de Testcontainers) y todas las clases que
 * extienden esta comparten configuración, así que Spring reutiliza un único contexto.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegracionBase {

    protected static final String PASSWORD = "password123";

    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UsuarioRepository usuarioRepo;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder passwordEncoder;

    @MockitoBean protected OcrService ocrService;
    @MockitoBean protected MatchingServiceClient matchingClient;
    @MockitoBean protected ReputacionSignalProvider reputacion;
    @MockitoBean protected ReputacionBloqueoProveedor reputacionBloqueo;

    @BeforeEach
    void mocksBase() {
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
    }

    protected static String dniUnico() {
        return String.format("%08d", 70_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    private Usuario base(TipoUsuario tipo, String nombre, LocalDate nacimiento) {
        Usuario u = new Usuario();
        u.setDni(dniUnico());
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setFechaNacimiento(nacimiento);
        u.setTipo(tipo);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setEstadoCuenta(EstadoCuenta.ACTIVA);
        return u;
    }

    /** Adulto con capacidad Estudiante (y opcionalmente Adulto Responsable). */
    protected Usuario adulto(boolean adultoResponsable) {
        Usuario u = base(TipoUsuario.ADULTO, "Ana", LocalDate.of(1990, 1, 1));
        u.setEmail(u.getDni() + "@test.tinku");
        u.setCapacidadEstudiante(true);
        u.setCapacidadAdultoResponsable(adultoResponsable);
        return usuarioRepo.saveAndFlush(u);
    }

    /** Menor a cargo de {@code adultoResponsable}, con capacidad Estudiante como deja el alta real. */
    protected Usuario menor(Usuario adultoResponsable) {
        Usuario u = base(TipoUsuario.MENOR, "Mia", LocalDate.now().minusYears(12));
        u.setAdultoResponsable(adultoResponsable);
        u.setCapacidadEstudiante(true);
        return usuarioRepo.saveAndFlush(u);
    }

    /** Tutor reservable: tarifa y una franja semanal por día con el horario indicado. */
    protected Usuario tutor(LocalTime desde, LocalTime hasta) {
        Usuario u = base(TipoUsuario.TUTOR, "Juan", LocalDate.of(1985, 1, 1));
        u.setEmail(u.getDni() + "@test.tinku");
        u.setActivoParaMatching(true);
        u.setFotoRef("foto-test.jpg"); // FR-ID-028: sin foto no aparece en búsquedas
        u = usuarioRepo.saveAndFlush(u);
        jdbc.update("INSERT INTO pagos.tarifas_tutor (tutor_id, precio_hora) VALUES (?, ?)",
                u.getId(), new BigDecimal("15000"));
        if (desde != null) {
            for (short dia = 0; dia <= 6; dia++) {
                jdbc.update("""
                        INSERT INTO reservas.franjas_disponibilidad (tutor_id, dia_semana, hora_inicio, hora_fin)
                        VALUES (?, ?, ?, ?)""", u.getId(), dia, desde, hasta);
            }
        }
        return u;
    }

    /** Promueve a un usuario como admin activo. {@code rol}: MODERACION_SEGURIDAD o SOPORTE_FINANCIERO. */
    protected void hacerAdmin(Usuario u, String rol) {
        jdbc.update("INSERT INTO admin.admins (usuario_id, rol) VALUES (?, ?)", u.getId(), rol);
    }

    protected String token(Usuario u) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("dni", u.getDni(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return json(res).get("token").asText();
    }

    protected JsonNode json(MvcResult res) throws Exception {
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    protected UUID id(Usuario u) {
        return u.getId();
    }
}
