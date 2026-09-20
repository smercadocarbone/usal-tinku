package com.tinku.resumen.web;

import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.resumen.model.ResumenSesion;
import com.tinku.resumen.repository.ResumenSesionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/sesiones/{id}/resumen (auditoría 2026-09-20): antes de este
 * endpoint el módulo M6 generaba el resumen pero no había NINGÚN cliente
 * capaz de leerlo. Ver {@code ResumenService#obtenerParaParticipante}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ResumenControllerIntegracionTest {

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
    @Autowired JwtUtil jwtUtil;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepo;
    @Autowired ResumenSesionRepository resumenRepo;
    @Autowired AlertaSeguridadRepository alertaRepository;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private record Escena(UUID sesionId, Usuario pagador, Usuario tutor) {
    }

    @Test
    void participante_conResumenGenerado_veElTexto() throws Exception {
        Escena e = escena();
        ResumenSesion fila = new ResumenSesion();
        fila.setSesionId(e.sesionId());
        fila.setEstado(ResumenSesion.ESTADO_GENERADO);
        fila.setResumenFinal("Repasamos ecuaciones de primer grado.");
        resumenRepo.save(fila);

        mockMvc.perform(get("/api/sesiones/" + e.sesionId() + "/resumen")
                        .header("Authorization", "Bearer " + token(e.pagador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.resumenFinal").value("Repasamos ecuaciones de primer grado."));
    }

    @Test
    void participante_sinFilaTodavia_disponibleFalse() throws Exception {
        Escena e = escena();

        mockMvc.perform(get("/api/sesiones/" + e.sesionId() + "/resumen")
                        .header("Authorization", "Bearer " + token(e.tutor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false))
                .andExpect(jsonPath("$.resumenFinal").doesNotExist());
    }

    @Test
    void participante_conFilaSuspendidaPorSeguridad_noFiltraElEstadoReal() throws Exception {
        Escena e = escena();
        ResumenSesion fila = new ResumenSesion();
        fila.setSesionId(e.sesionId());
        fila.setEstado(ResumenSesion.ESTADO_SUSPENDIDO_SEGURIDAD);
        fila.setSuspendidoSeguridad(true);
        resumenRepo.save(fila);
        alertaRepository.save(alertaDe(e.sesionId(), e.tutor().getId()));

        mockMvc.perform(get("/api/sesiones/" + e.sesionId() + "/resumen")
                        .header("Authorization", "Bearer " + token(e.pagador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    void terceroNoParticipante_403() throws Exception {
        Escena e = escena();
        Usuario tercero = guardarUsuario(TipoUsuario.ADULTO, "Otro");

        mockMvc.perform(get("/api/sesiones/" + e.sesionId() + "/resumen")
                        .header("Authorization", "Bearer " + token(tercero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void sesionInexistente_404() throws Exception {
        Usuario cualquiera = guardarUsuario(TipoUsuario.ADULTO, "Alguien");

        mockMvc.perform(get("/api/sesiones/" + UUID.randomUUID() + "/resumen")
                        .header("Authorization", "Bearer " + token(cualquiera)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private Escena escena() {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, "Sergio");
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepository.save(reserva);

        SesionAprendizaje sesion = new SesionAprendizaje();
        sesion.setReservaId(reserva.getId());
        sesion.setEstado(SesionAprendizaje.ESTADO_FINALIZADA);
        sesion.setDuracionAgendadaSegundos(3600);
        sesion.setInicioReal(Instant.now().minusSeconds(1200));
        sesion.setFinReal(Instant.now());
        sesion.setDuracionEfectivaSegundos(1200);
        sesionRepo.save(sesion);

        return new Escena(sesion.getId(), pagador, tutor);
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 43_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(
                tipo == TipoUsuario.TUTOR ? LocalDate.of(1999, 5, 15) : LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    private AlertaSeguridad alertaDe(UUID sesionId, UUID detectadoId) {
        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(sesionId);
        alerta.setRama("menor");
        alerta.setDetectadoId(detectadoId);
        alerta.setEstado(AlertaSeguridad.ESTADO_PENDIENTE_REVISION);
        return alerta;
    }

    private String token(Usuario u) {
        return jwtUtil.generateToken(u.getDni(), u.getTipo().name(), true, true);
    }
}
