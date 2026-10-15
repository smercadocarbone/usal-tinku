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
    @Autowired com.tinku.identidad.service.HabilitacionMenoresCap habilitacion;
    @Autowired com.tinku.reservas.service.FranjaService franjas;

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

    /** R-H H-1 (T3-06): los Tutores autorizados de los menores del seed pueden dar clases a menores. */
    @Test
    void losTutoresAutorizadosDeLosMenoresTienenCapVigente() {
        java.util.List<java.util.UUID> autorizados = jdbc.queryForList(
                "SELECT DISTINCT tutor_id FROM identidad.autorizaciones_tutor", java.util.UUID.class);
        assertThat(autorizados).hasSize(5);
        for (java.util.UUID tutor : autorizados) {
            assertThat(habilitacion.habilitadoParaMenores(tutor)).as(tutor.toString()).isTrue();
        }
    }

    /** R-H H-2 (T1-2, T7-3): franjas de 3 h (35 por Tutor) en vez de 105 de 1 h. */
    @Test
    void lasFranjasDelSeedSonDeTresHoras() {
        java.util.UUID maria = jdbc.queryForObject("SELECT id FROM identidad.usuarios WHERE dni = '30111222'", java.util.UUID.class);
        assertThat(contar("SELECT count(*) FROM reservas.franjas_disponibilidad WHERE activa AND tutor_id = '" + maria + "'"))
                .isEqualTo(35);
        assertThat(contar("SELECT count(*) FROM reservas.franjas_disponibilidad WHERE tutor_id = '" + maria
                + "' AND hora_fin - hora_inicio <> interval '3 hours'")).isZero();
        java.time.LocalDate dia = java.time.LocalDate.now(com.tinku.reservas.service.ReservasZonaHoraria.ZONA).plusDays(3);
        assertThat(franjas.cabeEnDisponibilidad(maria, dia.atTime(9, 0)
                .atZone(com.tinku.reservas.service.ReservasZonaHoraria.ZONA).toInstant(), 180)).isTrue();
        assertThat(franjas.cabeEnDisponibilidad(maria, dia.atTime(12, 30)
                .atZone(com.tinku.reservas.service.ReservasZonaHoraria.ZONA).toInstant(), 90)).isTrue();
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
        int caps = contar("SELECT count(*) FROM identidad.certificados_antecedentes_penales");
        tutores.run();
        estudiantes.run();
        historial.run();
        assertThat(contar("SELECT count(*) FROM identidad.usuarios")).isEqualTo(usuarios);
        assertThat(contar("SELECT count(*) FROM reservas.reservas")).isEqualTo(reservas);
        assertThat(contar("SELECT count(*) FROM reputacion.calificaciones")).isEqualTo(calificaciones);
        assertThat(contar("SELECT count(*) FROM identidad.certificados_antecedentes_penales")).isEqualTo(caps);
    }
}
