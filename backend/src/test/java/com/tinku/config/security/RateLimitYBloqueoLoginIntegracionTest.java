package com.tinku.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.VerificarDniRequest;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FASE2-02 / AUD-012: límite de requests por IP en los endpoints públicos,
 * bloqueo escalado por intentos fallidos de login (por DNI) y política mínima de
 * contraseña. Con límites bajos propios: la suite general corre con límites altos
 * (application-test.yml) para no depender del filtro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "tinku.rate-limit.verificar-dni-por-minuto=3",
        "tinku.rate-limit.publicos-por-minuto=1000",
        "tinku.login.intentos-antes-de-bloqueo=5"
})
class RateLimitYBloqueoLoginIntegracionTest {

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

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @MockBean OcrService ocrService;

    private static final String PASSWORD = "claveSegura123";
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    @BeforeEach
    void ocr() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, "10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
    }

    private String dniNuevo() {
        return String.format("%08d", 44_000_000 + CONTADOR.incrementAndGet());
    }

    private Usuario adulto(String dni) {
        Usuario u = new Usuario();
        u.setDni(dni);
        u.setNombre("Ana");
        u.setApellido("Gomez");
        u.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        u.setTipo(TipoUsuario.ADULTO);
        u.setCapacidadEstudiante(true);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return usuarioRepository.save(u);
    }

    private MvcResult login(String dni, String password) throws Exception {
        return mvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, password))))
                .andReturn();
    }

    private MockMultipartFile json(String nombre, Object dto) throws Exception {
        return new MockMultipartFile(nombre, nombre, MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    void verificarDni_superaElLimitePorIp_429ConRetryAfter() throws Exception {
        VerificarDniRequest datos = new VerificarDniRequest("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15));
        for (int i = 0; i < 3; i++) {
            int st = mvc.perform(multipart("/api/usuarios/verificar-dni").file(json("datos", datos)).file(foto())
                            .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                    .andReturn().getResponse().getStatus();
            assertThat(st).isNotEqualTo(429);
        }
        mvc.perform(multipart("/api/usuarios/verificar-dni").file(json("datos", datos)).file(foto())
                        .with(r -> { r.setRemoteAddr("10.9.9.9"); return r; }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // Otra IP no se ve afectada: el límite es por cliente.
        int otra = mvc.perform(multipart("/api/usuarios/verificar-dni").file(json("datos", datos)).file(foto())
                        .with(r -> { r.setRemoteAddr("10.9.9.10"); return r; }))
                .andReturn().getResponse().getStatus();
        assertThat(otra).isNotEqualTo(429);
    }

    @Test
    void login_cincoFallosSeguidos_bloqueaYResponde429_inclusoConPasswordCorrecta() throws Exception {
        String dni = dniNuevo();
        adulto(dni);
        for (int i = 0; i < 5; i++) {
            assertThat(login(dni, "incorrecta999").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult bloqueado = login(dni, PASSWORD);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
        assertThat(bloqueado.getResponse().getHeader("Retry-After")).isNotNull();
    }

    @Test
    void login_dniInexistente_bloqueadoIgual_mismoMensaje() throws Exception {
        String existente = dniNuevo();
        adulto(existente);
        String inexistente = dniNuevo();
        for (int i = 0; i < 5; i++) {
            login(existente, "incorrecta999");
            login(inexistente, "incorrecta999");
        }
        MvcResult a = login(existente, "incorrecta999");
        MvcResult b = login(inexistente, "incorrecta999");
        assertThat(a.getResponse().getStatus()).isEqualTo(429);
        assertThat(b.getResponse().getStatus()).isEqualTo(429);
        assertThat(a.getResponse().getContentAsString()).isEqualTo(b.getResponse().getContentAsString());
    }

    @Test
    void login_exitoso_reseteaElContador() throws Exception {
        String dni = dniNuevo();
        adulto(dni);
        for (int i = 0; i < 4; i++) {
            login(dni, "incorrecta999");
        }
        assertThat(login(dni, PASSWORD).getResponse().getStatus()).isEqualTo(200);
        for (int i = 0; i < 4; i++) {
            login(dni, "incorrecta999");
        }
        // 4 + éxito + 4: nunca llegó a 5 seguidos.
        assertThat(login(dni, PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void registro_passwordIgualAlDni_422() throws Exception {
        String dni = dniNuevo();
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        mvc.perform(multipart("/api/usuarios/registro")
                        .file(json("datos", new RegistroAdultoRequest(dni, "Ana", "Gomez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, true, false)))
                        .file(foto()))
                .andExpect(status().isCreated());
        String otro = dniNuevo();
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, otro, "Ana", "Gomez", LocalDate.of(1990, 5, 15)));
        mvc.perform(multipart("/api/usuarios/registro")
                        .file(json("datos", new RegistroAdultoRequest(otro, "Ana", "Gomez", LocalDate.of(1990, 5, 15),
                                otro + "@tinku.test", "dni" + otro, true, false)))
                        .file(foto()))
                .andExpect(status().isUnprocessableEntity());
        assertThat(usuarioRepository.findByDni(otro)).isEmpty();
    }

    @Test
    void registro_passwordSinNumeroOCorta_400ConElCampo() throws Exception {
        String dni = dniNuevo();
        mvc.perform(multipart("/api/usuarios/registro")
                        .file(json("datos", new RegistroAdultoRequest(dni, "Ana", "Gomez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", "soloLetrasLargas", true, false)))
                        .file(foto()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/usuarios/registro")
                        .file(json("datos", new RegistroAdultoRequest(dni, "Ana", "Gomez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", "corta12", true, false)))
                        .file(foto()))
                .andExpect(status().isBadRequest());
        assertThat(usuarioRepository.findByDni(dni)).isEmpty();
    }
}
