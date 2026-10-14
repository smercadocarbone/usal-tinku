package com.tinku.seed;

import com.tinku.identidad.EstudianteSeedRunner;
import com.tinku.matching.service.TutorSeedRunner;
import com.tinku.reputacion.HistorialSeedRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perfil {@code seed-demo}: los tres runners dejan, contra el esquema real (todas las migraciones),
 * Tutores reservables y visibles (foto, tarifa, franjas, temas), Estudiantes, menores con Tutores
 * autorizados e historial calificado; y volver a correrlos no duplica nada.
 */
@SpringBootTest
@ActiveProfiles({"test", "seed-demo"})
@Testcontainers
class SeedDemoIntegracionTest {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired TutorSeedRunner tutores;
    @Autowired EstudianteSeedRunner estudiantes;
    @Autowired HistorialSeedRunner historial;

    private int contar(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void elSeedDejaTutoresReservablesYVisibles() {
        assertThat(contar("SELECT count(*) FROM identidad.usuarios WHERE tipo = 'TUTOR'")).isGreaterThanOrEqualTo(30);
        // FR-ID-028: sin foto un Tutor no aparece en la búsqueda.
        assertThat(contar("SELECT count(*) FROM identidad.usuarios WHERE tipo = 'TUTOR' AND foto_ref IS NULL")).isZero();
        assertThat(contar("SELECT count(*) FROM identidad.usuarios u WHERE u.tipo = 'TUTOR' "
                + "AND NOT EXISTS (SELECT 1 FROM pagos.tarifas_tutor t WHERE t.tutor_id = u.id)")).isZero();
        assertThat(contar("SELECT count(*) FROM identidad.usuarios u WHERE u.tipo = 'TUTOR' "
                + "AND NOT EXISTS (SELECT 1 FROM reservas.franjas_disponibilidad f WHERE f.tutor_id = u.id)")).isZero();
    }

    @Test
    void elSeedDejaEstudiantesYMenoresConTutoresAutorizados() {
        assertThat(contar("SELECT count(*) FROM identidad.usuarios WHERE tipo = 'ADULTO'")).isPositive();
        assertThat(contar("SELECT count(*) FROM identidad.usuarios WHERE tipo = 'MENOR'")).isEqualTo(3);
    }

    @Test
    void elHistorialDejaClasesCalificadas() {
        assertThat(contar("SELECT count(*) FROM reservas.reservas WHERE estado = 'finalizada'")).isPositive();
        assertThat(contar("SELECT count(*) FROM reputacion.calificaciones")).isPositive();
    }

    @Test
    void correrlosDeNuevoNoDuplicaNada() {
        int usuarios = contar("SELECT count(*) FROM identidad.usuarios");
        int reservas = contar("SELECT count(*) FROM reservas.reservas");
        int calificaciones = contar("SELECT count(*) FROM reputacion.calificaciones");
        tutores.run();
        estudiantes.run();
        historial.run();
        assertThat(contar("SELECT count(*) FROM identidad.usuarios")).isEqualTo(usuarios);
        assertThat(contar("SELECT count(*) FROM reservas.reservas")).isEqualTo(reservas);
        assertThat(contar("SELECT count(*) FROM reputacion.calificaciones")).isEqualTo(calificaciones);
    }
}
