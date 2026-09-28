package com.tinku.pagos.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BR-PAG-01 = 21 % del monto bruto (ADR-M5-04; antes 27 %, T-TES-05). Contexto mínimo
 * (sin BD): solo {@link ComisionPlataforma} con su {@code @Value} del default — el runner no
 * carga {@code application.yml}, así que el test cubre el fallback (21) y los tests de
 * integración cubren la property configurada.
 */
class ComisionPlataformaTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ComisionPlataforma.class);

    @Test
    void calcular_aplicaEl21PorCientoDelMontoBruto() {
        runner.run(ctx -> {
            ComisionPlataforma comision = ctx.getBean(ComisionPlataforma.class);
            assertThat(comision.calcular(new BigDecimal("15000.00")))
                    .isEqualByComparingTo(new BigDecimal("3150.00"));
        });
    }
}