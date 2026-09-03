package com.tinku;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Test minimo de arranque (T-000-09). Sirve como smoke test del pipeline de
 * CI: si el contexto de Spring no levanta, nada mas tiene sentido correr.
 *
 * Usa Testcontainers (PostgreSQL real) — el mismo patron que los tests de
 * integracion de cada modulo — en vez de una base compartida de desarrollo,
 * para que no dependa de un Postgres local corriendo (no confiable en CI,
 * Chunk 000-F).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class TinkuApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void contextLoads() {
        // Si el contexto de Spring no levanta (ej. conexion a la base,
        // migraciones de Flyway, beans), este test falla.
    }
}
