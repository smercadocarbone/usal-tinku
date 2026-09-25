package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cubre los casos de US-1 del Spec de M1: alta exitosa, DNI duplicado,
 * documento ilegible y edad insuficiente.
 *
 * El stub de OCR hace eco de lo declarado (dev/test), por lo que la edad
 * y el DNI se pueden variar desde la request sin mockear OcrService. El
 * caso "documento no coincide" se cubre por separado en
 * {@code UsuarioServiceRegistroUnitTest} (mock fino del OCR).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class UsuarioServiceTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16"))
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
                LocalDate.of(2000, 1, 1), "00000000@tinku.test", "password123",
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
                LocalDate.of(2000, 1, 1), "11111111@tinku.test", "password123",
                false, false
        );

        assertThrows(IllegalArgumentException.class, () ->
                usuarioService.registrarAdulto(sinCapacidades, "foto-valida".getBytes())
        );
    }

    @Test
    void rechazaElRegistroSiElDocumentoCorrespondeAMenorDeEdad() {
        // El stub hace eco de la fecha declarada, así que una fecha de menor
        // habilita el caso "edad insuficiente" (antes imposible con el stub de
        // resultados fijos). La edad se computa sobre lo EXTRAÍDO, nunca lo
        // declarado (Plan M1 sección 2.1, paso 4b).
        RegistroAdultoRequest menor = new RegistroAdultoRequest(
                "33333333", "Nombre Stub", "Apellido Stub",
                LocalDate.now().minusYears(15), "33333333@tinku.test", "password123",
                true, false
        );

        assertThrows(EdadInsuficienteException.class, () ->
                usuarioService.registrarAdulto(menor, "foto-valida".getBytes())
        );
    }
}
