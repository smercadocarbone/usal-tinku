package com.tinku.admin.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra la auditoría transversal (T-M8-02) sobre TODAS las rutas
 * {@code /api/admin/**} del panel — incluidas las de otros módulos que ya
 * existían (M7/M9). El interceptor se audita solo
 *  1. post-request (afterCompletion) para tener el status definitivo,
 *  2. en status 2xx (un 403 del gate no es una acción de Admin).
 */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

    private final AuditoriaInterceptor auditoriaInterceptor;

    public AdminWebConfig(AuditoriaInterceptor auditoriaInterceptor) {
        this.auditoriaInterceptor = auditoriaInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // /api/admin/salud queda EXCLUIDA: sondear infraestructura es lectura, no
        // una acción de Admin — auditarla inundaría log_auditoria_admin a cada
        // refresco del panel.
        registry.addInterceptor(auditoriaInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/salud");
    }
}