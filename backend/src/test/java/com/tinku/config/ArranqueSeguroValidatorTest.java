package com.tinku.config;

import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.StubOcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AUD-004 + AUD-034: el artefacto no puede arrancar con el OCR falso ni con el
 * JWT secret placeholder fuera de dev/test. Contexto mínimo (sin BD): solo el
 * validador y el OcrService, que es lo único que se decide acá.
 */
class ArranqueSeguroValidatorTest {

    private static final String SECRET_REAL = "un-secret-real-de-al-menos-32-bytes-0123456789";

    private ApplicationContextRunner runner(String perfiles, OcrService ocr, String jwtSecret) {
        return runner(perfiles, ocr, jwtSecret, "un-matching-token-real");
    }

    private ApplicationContextRunner runner(
            String perfiles, OcrService ocr, String jwtSecret, String matchingToken) {
        return new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment()
                        .setActiveProfiles(perfiles.isEmpty() ? new String[0] : perfiles.split(",")))
                .withPropertyValues(
                        "tinku.jwt.secret=" + jwtSecret,
                        "tinku.matching-service.token=" + matchingToken)
                .withBean(OcrService.class, () -> ocr)
                .withUserConfiguration(ArranqueSeguroValidator.class);
    }

    @Test
    void elArtefactoNoTraePerfilActivoHardcodeado() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        // El perfil viene del entorno. Un default `dev` en el jar activa el OCR falso en prod.
        assertThat(props).doesNotContainKey("spring.profiles.active");
    }

    @Test
    void prodConStubOcr_abortaElArranque() {
        runner("prod", new StubOcrService(), SECRET_REAL).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("StubOcrService");
        });
    }

    @Test
    void prodYDevJuntosConStubOcr_abortaElArranque() {
        // `prod` gana: sumarle `dev` no habilita el stub en un despliegue real.
        runner("prod,dev", new StubOcrService(), SECRET_REAL).run(ctx ->
                assertThat(ctx).hasFailed());
    }

    @Test
    void prodConJwtPlaceholder_abortaElArranque() {
        runner("prod", mock(OcrService.class), ArranqueSeguroValidator.JWT_SECRET_PLACEHOLDER)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure()).rootCause()
                            .hasMessageContaining("tinku.jwt.secret");
                });
    }

    @Test
    void sinPerfilConJwtPlaceholder_abortaElArranque() {
        runner("", mock(OcrService.class), ArranqueSeguroValidator.JWT_SECRET_PLACEHOLDER)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void prodConOcrRealYSecretReal_arranca() {
        runner("prod", mock(OcrService.class), SECRET_REAL).run(ctx ->
                assertThat(ctx).hasNotFailed());
    }

    @Test
    void prodConMatchingTokenVacio_abortaElArranque() {
        // AUD-015: sin el token compartido, el backend llamaria a un matching
        // fail-closed (503) — abortar temprano, mismo patron que el JWT.
        runner("prod", mock(OcrService.class), SECRET_REAL, "").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause()
                    .hasMessageContaining("tinku.matching-service.token");
        });
    }

    @Test
    void devConStubYPlaceholder_arranca() {
        runner("dev", new StubOcrService(), ArranqueSeguroValidator.JWT_SECRET_PLACEHOLDER)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
