package com.tinku.shared;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

/**
 * Confirma el mecanismo de eventos de dominio en memoria (Constitucion, Articulo IX):
 * al publicar {@link DomainEventExample.EjemploEvent}, el listener
 * {@link DomainEventExample.EjemploListener} reacciona. Es la validacion de
 * T-000-04 — el patron que los modulos reales van a repetir para sus eventos
 * (sesion.finalizada, denuncia.registrada, sancion aplicada, etc.).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DomainEventExampleTest {

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
    ApplicationEventPublisher publisher;

    @SpyBean
    DomainEventExample.EjemploListener listener;

    @Test
    void listenerReaccionaAlPublicarElEvento() {
        DomainEventExample.EjemploEvent evento =
                new DomainEventExample.EjemploEvent(this, "mensaje-de-prueba");

        publisher.publishEvent(evento);

        verify(listener, times(1)).onEjemplo(evento);
    }
}
