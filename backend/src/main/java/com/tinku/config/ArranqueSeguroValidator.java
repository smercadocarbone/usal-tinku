package com.tinku.config;

import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.StubOcrService;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Aborta el arranque si el artefacto quedaría corriendo con piezas de desarrollo
 * fuera de dev/test (AUD-004, AUD-034):
 * <ul>
 *   <li>{@link StubOcrService}: no hace OCR, hace eco de lo que declaró el usuario —
 *       la verificación de identidad (y de edad de los adultos) quedaría anulada.</li>
 *   <li>{@code tinku.jwt.secret} vacío o igual al placeholder público del repo:
 *       cualquiera podría firmar tokens de Admin.</li>
 * </ul>
 * {@code prod} gana siempre: {@code prod,dev} no habilita el stub en un despliegue real.
 */
@Component
public class ArranqueSeguroValidator implements InitializingBean {

    public static final String JWT_SECRET_PLACEHOLDER =
            "CAMBIAR_EN_TODOS_LOS_AMBIENTES_AL_AGREGAR_UN_SECRET_REAL";

    private final Environment environment;
    private final OcrService ocrService;
    private final String jwtSecret;

    public ArranqueSeguroValidator(Environment environment, OcrService ocrService,
                                   @Value("${tinku.jwt.secret:}") String jwtSecret) {
        this.environment = environment;
        this.ocrService = ocrService;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void afterPropertiesSet() {
        boolean entornoDeDesarrollo = environment.acceptsProfiles(Profiles.of("dev", "test"))
                && !environment.acceptsProfiles(Profiles.of("prod"));
        if (entornoDeDesarrollo) {
            return;
        }
        if (StubOcrService.class.isAssignableFrom(AopUtils.getTargetClass(ocrService))) {
            throw new IllegalStateException("StubOcrService activo fuera de dev/test: el OCR de "
                    + "identidad no verificaría nada. Revisá SPRING_PROFILES_ACTIVE (AUD-004).");
        }
        if (jwtSecret == null || jwtSecret.isBlank() || JWT_SECRET_PLACEHOLDER.equals(jwtSecret)) {
            throw new IllegalStateException("tinku.jwt.secret vacío o con el placeholder del "
                    + "repositorio fuera de dev/test. Definí JWT_SECRET (AUD-034).");
        }
    }
}
