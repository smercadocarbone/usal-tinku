package com.tinku;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test minimo de arranque (T-000-09). Sirve como smoke test del pipeline de CI:
 * si el contexto de Spring no levanta, nada mas tiene sentido correr.
 *
 * Requiere un PostgreSQL disponible (ver Testcontainers en el pom.xml para
 * los tests de integracion reales de cada modulo, no usar una base compartida
 * de desarrollo para tests automatizados).
 */
@SpringBootTest
@ActiveProfiles("test")
class TinkuApplicationTests {

    @Test
    void contextLoads() {
        // Si este test falla, revisar antes que nada la conexion a la base
        // configurada en application-test.yml, no asumir que es un bug de negocio.
    }
}
