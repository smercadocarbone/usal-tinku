package com.tinku.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests HTTP de T-000-05: el filtro JWT + el endpoint de login de punta a
 * punta, sobre la SecurityFilterChain real. Cubre el flujo login exitoso,
 * login con credenciales inválidas (401), y que una ruta protegida rechaza
 * una petición sin token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class SecurityHttpTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UsuarioService usuarioService;

    private void registrarUsuario(String dni, String password) {
        usuarioService.registrarAdulto(
                new RegistroAdultoRequest(
                        dni, "Nombre Stub", "Apellido Stub",
                        java.time.LocalDate.of(2000, 1, 1), dni + "@tinku.test", password,
                        true, false),
                "foto-valida".getBytes());
    }

    @Test
    void loginConCredencialesCorrectasDevuelve200YTipo() throws Exception {
        registrarUsuario("00000000", "password123");

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("00000000", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("ADULTO"));
    }

    @Test
    void loginConPasswordIncorrectoDevuelve401() throws Exception {
        registrarUsuario("00000000", "password123");

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("00000000", "password-incorrecta"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registroSigueSiendoAccesibleSinAutenticacion() throws Exception {
        MockMultipartFile datos = new MockMultipartFile(
                "datos", "", "application/json",
                objectMapper.writeValueAsBytes(new RegistroAdultoRequest(
                        "22222222", "Nombre Stub", "Apellido Stub",
                        java.time.LocalDate.of(2000, 1, 1), "22222222@tinku.test", "password123",
                        true, false)));
        MockMultipartFile foto = new MockMultipartFile(
                "fotoDni", "foto.jpg", "image/jpeg", "foto-valida".getBytes());

        mockMvc.perform(multipart("/api/usuarios/registro").file(datos).file(foto))
                .andExpect(status().isCreated());
    }

    @Test
    void rutaProtegidaSinTokenEsBloqueada() throws Exception {
        // Sin JWT, una ruta protegida (no pública) es rechazada. Sin un
        // entry point de autenticación configurado, Spring Security responde
        // 403 para acceso anónimo a ruta protegida — lo importante es que el
        // request NUNCA llega al controller sin estar autenticado.
        mockMvc.perform(post("/api/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflightCorsDesdeElOrigenDelFrontendDevEsPermitido() throws Exception {
        mockMvc.perform(options("/api/usuarios/login")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void preflightDesdeUnOrigenNoPermitidoEsRechazado() throws Exception {
        mockMvc.perform(options("/api/usuarios/login")
                        .header("Origin", "http://malicioso.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
