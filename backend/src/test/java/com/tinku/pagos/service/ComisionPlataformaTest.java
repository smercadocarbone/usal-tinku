package com.tinku.pagos.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-TES-05 (T05-comision-27, DT1): BR-PAG-01 = 27 % del monto bruto, calibrado
 * en el Cap. 5 de la tesis. Contexto mínimo (sin BD): solo {@link ComisionPlataforma}
 * con su {@code @Value} del default — el runner no carga {@code application.yml},
 * así que el test cubre el fallback (27) y los tests de integración cubren la
 * property configurada.
 */
class ComisionPlataformaTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ComisionPlataforma.class);

    @Test
    void calcular_aplicaEl27PorCientoDelMontoBruto() {
        runner.run(ctx -> {
            ComisionPlataforma comision = ctx.getBean(ComisionPlataforma.class);
            assertThat(comision.calcular(new BigDecimal("15000.00")))
                    .isEqualByComparingTo(new BigDecimal("4050.00"));
        });
    }
}