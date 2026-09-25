package com.tinku.admin;

import tools.jackson.databind.ObjectMapper;
import com.tinku.admin.notificacion.Notificacion;
import com.tinku.admin.notificacion.NotificacionRepository;
import com.tinku.admin.notificacion.NotificadorOutbox;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FASE2-03 (AUD-014): outbox de notificaciones y bandeja in-app. El aviso de
 * kill-switch al Adulto Responsable se prueba en {@code KillswitchIntegracionTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class NotificacionesIntegracionTest {

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

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired NotificacionRepository notificacionRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @MockitoSpyBean NotificadorOutbox notificador;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private Usuario adulto() {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 43_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Nombre" + CONTADOR.get());
        u.setApellido("Paz");
        u.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        u.setTipo(TipoUsuario.ADULTO);
        u.setPasswordHash("hash");
        u.setCapacidadEstudiante(true);
        u.setCapacidadAdultoResponsable(true);
        return usuarioRepository.save(u);
    }

    private String token(Usuario u) {
        return "Bearer " + jwtUtil.generateToken(u);
    }

    private Notificacion aviso(Usuario destinatario) {
        Notificacion n = new Notificacion();
        n.setDestinatarioId(destinatario.getId());
        n.setTipo(TipoNotificacion.DENUNCIA_RECIBIDA);
        n.setDatos(Map.of("denunciaId", UUID.randomUUID().toString()));
        return notificacionRepository.save(n);
    }

    private org.springframework.test.web.servlet.ResultActions denunciarPerfil(Usuario denunciante, Usuario denunciado)
            throws Exception {
        return mvc.perform(post("/api/denuncias")
                .header("Authorization", token(denunciante))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "denunciadoId", denunciado.getId().toString(), "motivo", "acoso"))));
    }

    @Test
    void denunciaPresentada_notificaAlDenunciado_sinRevelarAlDenunciante() throws Exception {
        Usuario denunciante = adulto();
        Usuario denunciado = adulto();

        denunciarPerfil(denunciante, denunciado).andExpect(status().isCreated());

        String bandeja = mvc.perform(get("/api/notificaciones").header("Authorization", token(denunciado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("DENUNCIA_RECIBIDA"))
                .andExpect(jsonPath("$[0].datos.descargoVenceAt").isNotEmpty())
                .andExpect(jsonPath("$[0].leida").value(false))
                .andReturn().getResponse().getContentAsString();
        // FR-SEC-006: el denunciante es anónimo para el denunciado.
        assertThat(bandeja).doesNotContain(denunciante.getId().toString())
                .doesNotContain(denunciante.getNombre()).doesNotContain(denunciante.getDni());
        assertThat(notificacionRepository.findByDestinatarioId(denunciante.getId())).isEmpty();
    }

    @Test
    void bandeja_soloElDestinatarioVeYMarca() throws Exception {
        Usuario duenio = adulto();
        Usuario otro = adulto();
        Notificacion n = aviso(duenio);

        mvc.perform(get("/api/notificaciones/no-leidas").header("Authorization", token(duenio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(get("/api/notificaciones").header("Authorization", token(otro)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/notificaciones/{id}/leida", n.getId()).header("Authorization", token(otro)))
                .andExpect(status().isNotFound());
        assertThat(notificacionRepository.findById(n.getId()).orElseThrow().getLeidaAt()).isNull();

        mvc.perform(post("/api/notificaciones/{id}/leida", n.getId()).header("Authorization", token(duenio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
        assertThat(notificacionRepository.findById(n.getId()).orElseThrow().getLeidaAt()).isNotNull();
        mvc.perform(get("/api/notificaciones/no-leidas").header("Authorization", token(duenio)))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    /** Outbox: si el hecho falla después de escribir el aviso, el aviso no existe. */
    @Test
    void notificacion_seRevierteConLaTransaccion() throws Exception {
        Usuario denunciante = adulto();
        Usuario denunciado = adulto();
        doAnswer(inv -> {
            inv.callRealMethod();
            throw new IllegalStateException("falla posterior al aviso");
        }).when(notificador).notificar(any(), any(), any());

        try {
            denunciarPerfil(denunciante, denunciado);
        } catch (Exception esperada) {
            // Según el handler global, el error sale como 5xx o se propaga: da igual acá.
        }

        List<Notificacion> avisos = notificacionRepository.findByDestinatarioId(denunciado.getId());
        assertThat(avisos).isEmpty();
        assertThat(denunciaRepository.findAll()).noneMatch(d -> d.getDenunciadoId().equals(denunciado.getId()));
        org.mockito.Mockito.verify(notificador).notificar(any(), any(), any());
    }
}
