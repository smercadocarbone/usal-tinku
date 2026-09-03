package com.tinku.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Verificacion de la comunicacion interna backend Java -> proceso Python del
 * Motor de Matching al arrancar en dev (T-000-08). Solo levanta la conexion y
 * loguea el resultado; si el servicio Python no esta corriendo, arranca con
 * un warning claro en vez de tirar abajo el backend — el matching real no se
 * usa todavia hasta implementar M2.
 */
@Component
@Profile("dev")
public class MatchingServiceHealthCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MatchingServiceHealthCheck.class);

    private final MatchingServiceClient client;

    public MatchingServiceHealthCheck(MatchingServiceClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            MatchingServiceClient.EstadoSalud salud = client.health();
            log.info("Motor de Matching responde: status={}, service={}",
                    salud.status(), salud.service());
        } catch (Exception e) {
            log.warn("Motor de Matching NO responde (T-000-08). Verificar que "
                    + "tinku-matching-service este levantado (uvicorn main:app --port 8000). "
                    + "Causa: {}", e.getMessage());
        }
    }
}
