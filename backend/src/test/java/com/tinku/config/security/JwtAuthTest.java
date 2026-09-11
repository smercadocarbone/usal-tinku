package com.tinku.config.security;

import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.TokenResponse;
import com.tinku.identidad.service.AuthService;
import com.tinku.identidad.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-000-05: verifica el flujo de login con JWT + bcrypt (NFR-SEC-02) del
 * extremo a extremo: registro de un usuario, login con credenciales
 * correctas (obtiene token), y rechazo con credenciales inválidas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class JwtAuthTest {

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
    UsuarioService usuarioService;

    @Autowired
    AuthService authService;

    @Autowired
    JwtUtil jwtUtil;

    private RegistroAdultoRequest requestValido() {
        return new RegistroAdultoRequest(
                "00000000", "Nombre Stub", "Apellido Stub",
                LocalDate.of(2000, 1, 1), "00000000@tinku.test", "password123",
                true, false
        );
    }

    @Test
    void loginConCredencialesCorrectasDevuelveTokenValido() {
        usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes());

        TokenResponse response = authService.login(
                new LoginRequest("00000000", "password123"));

        assertNotNull(response.token());
        assertEquals("ADULTO", response.tipo());
        assertTrue(jwtUtil.isTokenValid(response.token()));
        assertEquals("00000000", jwtUtil.extractDni(response.token()));
    }

    @Test
    void loginConPasswordIncorrectoEsRechazado() {
        usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes());

        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("00000000", "password-incorrecta"))
        );
    }

    @Test
    void loginConDniInexistenteEsRechazado() {
        assertThrows(BadCredentialsException.class, () ->
                authService.login(new LoginRequest("99999999", "password123"))
        );
    }

    @Test
    void jwtAutenticaAlUsuarioYExponeLosClaims() {
        usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes());
        TokenResponse response = authService.login(
                new LoginRequest("00000000", "password123"));

        var claims = jwtUtil.validateToken(response.token());
        assertEquals("00000000", claims.getSubject());
        assertEquals("ADULTO", claims.get("tipo"));
        assertEquals(true, claims.get("cap_est"));
        assertEquals(false, claims.get("cap_ar"));
    }
}
