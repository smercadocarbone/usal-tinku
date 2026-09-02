package com.tinku.config;

import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz con JobStore persistido en PostgreSQL (Constitucion, Articulo IV/X).
 *
 * NO se usa el JobStore en memoria por defecto de Quartz: cualquier timeout de
 * negocio (escrow a 24hs, aprobacion de reserva, ventana del kill-switch de 12hs,
 * etc.) debe sobrevivir a un reinicio o redeploy del proceso. Ver Tabla_Tiempos_Tinku.md
 * para el listado completo de plazos que dependen de este mecanismo.
 *
 * La configuracion real de tablas QRTZ_* se aplica via Flyway
 * (ver src/main/resources/db/migration/V1__quartz_tables.sql, pendiente de
 * generar con el script oficial de Quartz para PostgreSQL antes del primer
 * job real - ver Tasks, T-000-03).
 *
 * Spring Boot detecta automaticamente application.yml -> spring.quartz.job-store-type=jdbc
 * y usa esta clase solo para overrides puntuales si hicieran falta mas adelante.
 */
@Configuration
public class QuartzConfig {

    // Placeholder intencional: la config base vive en application.yml
    // (spring.quartz.*). Esta clase queda como el lugar donde agregar
    // JobDetail/Trigger beans especificos de cada modulo a medida que
    // se implementen (ej. T-M1-07 backoff de OCR, T-M4-06 timeout de
    // pendiente_pago, T-M3-04 no-show a T+10, etc.) en vez de dispersar
    // la configuracion de Quartz por todo el codebase.

    QuartzProperties quartzProperties() {
        return new QuartzProperties();
    }
}
