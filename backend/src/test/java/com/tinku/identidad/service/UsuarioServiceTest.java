package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre los casos de US-1 del Spec de M1: alta exitosa, DNI duplicado,
 * y (implícitamente, vía el stub) documento ilegible.
 *
 * El "documento no coincide" y "edad insuficiente" no se pueden ejercitar
 * con el StubOcrService actual (siempre devuelve el mismo resultado fijo)
 * — pendiente: parametrizar el stub para variar el resultado en tests,
 * o mockear OcrService directamente en un test más unitario aparte.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class UsuarioServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("tinku_test");

    @org.springframework.test.context.DynamicPropertySource
    static void configureProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    UsuarioService usuarioService;

    @Autowired
    UsuarioRepository usuarioRepository;

    private RegistroAdultoRequest requestValido() {
        return new RegistroAdultoRequest(
                "00000000", "Nombre Stub", "Apellido Stub",
                LocalDate.of(2000, 1, 1), "password123",
                true, false
        );
    }

    @Test
    void registraUnAdultoConCapacidadEstudianteExitosamente() {
        Usuario usuario = usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes());

        assertNotNull(usuario.getId());
        assertTrue(usuario.isCapacidadEstudiante());
        assertFalse(usuario.isCapacidadAdultoResponsable());
        assertEquals("00000000", usuario.getDni());
    }

    @Test
    void rechazaElRegistroSiElDniYaExiste() {
        usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes());

        assertThrows(DniYaRegistradoException.class, () ->
                usuarioService.registrarAdulto(requestValido(), "foto-valida".getBytes())
        );
    }

    @Test
    void rechazaElRegistroSiElDocumentoEsIlegible() {
        assertThrows(DocumentoIlegibleException.class, () ->
                usuarioService.registrarAdulto(requestValido(), new byte[0])
        );
    }

    @Test
    void rechazaElRegistroSiNingunaCapacidadEstaActiva() {
        RegistroAdultoRequest sinCapacidades = new RegistroAdultoRequest(
                "11111111", "Nombre Stub", "Apellido Stub",
                LocalDate.of(2000, 1, 1), "password123",
                false, false
        );

        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.registrarAdulto(sinCapacidades, "foto-valida".getBytes())
        );
    }
}
