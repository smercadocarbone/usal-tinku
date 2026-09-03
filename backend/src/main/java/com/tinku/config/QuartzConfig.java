package com.tinku.config;

import org.springframework.context.annotation.Configuration;

/**
 * Quartz con JobStore persistido en PostgreSQL (Constitucion, Articulo IV/X).
 *
 * NO se usa el JobStore en memoria por defecto de Quartz: cualquier timeout de
 * negocio (escrow a 24hs, aprobacion de reserva, ventana del kill-switch de 12hs,
 * etc.) debe sobrevivir a un reinicio o redeploy del proceso. Ver Tabla_Tiempos_Tinku.md
 * para el listado completo de plazos que dependen de este mecanismo.
 *
 * La config de Spring Boot (spring.quartz.job-store-type=jdbc y las tablas
 * QRTZ_* via Flyway, V3__quartz_tables.sql en schema `public`) vive en
 * application.yml. Este clase NO define QuartzProperties: Spring Boot ya crea
 * y bindea ese bean desde spring.quartz.* — definir uno propio (aunque fuera
 * con @Bean) generaria un segundo bean vacio y romperia el arranque por
 * ambiguedad. La unica responsabilidad de esta clase es exponer los
 * JobDetail/Trigger beans especificos de cada modulo a medida que se
 * implementen (ej. T-M1-07 backoff de OCR, T-M4-06 timeout de pendiente_pago,
 * T-M3-04 no-show a T+10, etc.) en vez de dispersar la configuracion de
 * Quartz por todo el codebase.
 */
@Configuration
public class QuartzConfig {
}
