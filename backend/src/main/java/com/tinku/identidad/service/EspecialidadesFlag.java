package com.tinku.identidad.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Flag de ADR-M1-06 (T-ESP-01..04): {@code tinku.especialidades.habilitado}. Apagado por defecto en
 * todos los perfiles porque falta el texto legal de la cláusula de consentimiento para consultar
 * registros oficiales con el DNI del Tutor. Con el flag apagado, todo lo nuevo responde 404 y el
 * resto del sistema se comporta como antes de V56.
 *
 * <p>En prod/staging no arranca con el flag encendido mientras la versión de la cláusula sea el
 * marcador {@value #VERSION_PENDIENTE}: no se piden consentimientos de un texto que no existe.</p>
 */
@Component
public class EspecialidadesFlag {

    /** Marcador de application.yml: la cláusula todavía no tiene texto (pendiente del dueño). */
    public static final String VERSION_PENDIENTE = "pendiente-del-duenio";

    private final boolean habilitado;

    public EspecialidadesFlag(@Value("${tinku.especialidades.habilitado:false}") boolean habilitado,
                              @Value("${tinku.clausulas.consulta-registros.version:" + VERSION_PENDIENTE + "}")
                              String versionClausula,
                              Environment env) {
        if (habilitado && VERSION_PENDIENTE.equals(versionClausula)
                && env.acceptsProfiles(Profiles.of("prod", "staging"))) {
            throw new IllegalStateException("tinku.especialidades.habilitado=true exige "
                    + "TINKU_CLAUSULA_CONSULTA_REGISTROS_VERSION con la versión real de la cláusula "
                    + "de consentimiento (ADR-M1-06, pendiente del dueño).");
        }
        this.habilitado = habilitado;
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Corta con 404 si el flag está apagado: para afuera, la función no existe. */
    public void exigir() {
        if (!habilitado) {
            throw new FuncionNoHabilitadaException();
        }
    }
}
