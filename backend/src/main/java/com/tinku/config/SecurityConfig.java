package com.tinku.config;

import com.tinku.config.security.AdminActivoAuthorizationManager;
import com.tinku.config.security.JwtAuthenticationFilter;
import com.tinku.config.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Autenticacion propia via JWT (Constitucion: sin delegar a un proveedor
 * externo de auth), hashing de contrasenas con bcrypt (NFR-SEC-02).
 *
 * IMPORTANTE - roles del sistema (no confundir los dos niveles):
 *  - usuarios.tipo: adulto | menor | tutor (M1) - mas capacidad_estudiante /
 *    capacidad_adulto_responsable como flags independientes en el tipo adulto.
 *  - admins.rol: moderacion_seguridad | soporte_financiero (M8) - tabla
 *    completamente separada de `usuarios`, nunca debe compartir JWT ni
 *    autorizacion con las cuentas de usuarios finales (Plan tecnico de M8).
 *
 * Cada modulo agrega sus reglas de autorizacion especificas (ej. bloquear a
 * un `menor` de POST /api/denuncias, FR-SEC-001) en su propio filtro o a nivel
 * de metodo (@PreAuthorize) - no centralizar toda la logica de negocio aca.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AdminActivoAuthorizationManager adminActivoAuthorizationManager;

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           AdminActivoAuthorizationManager adminActivoAuthorizationManager) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.adminActivoAuthorizationManager = adminActivoAuthorizationManager;
    }

    /**
     * CORS para el frontend (Next.js en dev corre en otro origin, p.ej.
     * http://localhost:3000, y llama a este backend en :8080). Orígenes
     * permitidos en `cors.allowed-origins` (env CORS_ALLOWED_ORIGINS,
     * lista separada por comas). Nunca comodines: se manda Authorization
     * header, y con allowCredentials no se combina con `*`.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toList());
        config.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PUT.name(), HttpMethod.PATCH.name(), HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of("Origin", "Content-Type", "Authorization", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Value("${tinku.rate-limit.verificar-dni-por-minuto:5}")
    private int limiteVerificarDni;
    @Value("${tinku.rate-limit.publicos-por-minuto:20}")
    private int limitePublicos;
    @Value("${tinku.rate-limit.webhooks-por-minuto:120}")
    private int limiteWebhooks;

    /** Header con la IP real del cliente cuando hay un proxy de confianza adelante
     *  (Cloudflare Tunnel: {@code CF-Connecting-IP}). Vacío = {@code remoteAddr}. */
    @Value("${tinku.rate-limit.header-ip-cliente:}")
    private String headerIpCliente;

    /** No es un @Bean: si lo fuera, Spring Boot además lo registraría como filtro de servlet y correría dos veces. */
    private RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter(limiteVerificarDni, limitePublicos, limiteWebhooks, headerIpCliente,
                java.time.Clock.systemUTC());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable) // API stateless con JWT, sin sesiones de servidor
            // FASE2-02: el límite por IP corre antes que cualquier autenticación.
            .addFilterBefore(rateLimitFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Rutas publicas: registro de adulto, registro de tutor
                // (autorregistro con DNI, FR-ID-007) y login. El alta de
                // menor NO es pública: la hace el Adulto Responsable
                // autenticado (FR-ID-020, Artículo II).
                .requestMatchers("/api/usuarios/registro", "/api/usuarios/verificar-dni",
                        "/api/usuarios/login", "/api/tutores/registro",
                        "/api/tutores/verificar-dni",
                        "/api/usuarios/recuperar-password", "/api/usuarios/resetear-password").permitAll()
                // Webhook de LiveKit: la autenticación ES su firma HS256 sobre
                // el body (T-M3-02), no el JWT de Tinku — mismo patrón que el
                // webhook de MercadoPago (T-M5-03): la autenticación ES la firma
                // x-signature (HMAC-SHA256). Sin firma válida → 401.
                .requestMatchers("/api/webhooks/livekit",
                        "/api/webhooks/mercadopago").permitAll()
                // Defensa en profundidad (auditoría 2026-09-18): antes solo
                // `authenticated()` cubría /api/admin/**, dejando la autorización
                // real 100% en manos de que cada controller nuevo recuerde llamar
                // a AdminModeracionGate. Esto no reemplaza ese gate granular por
                // rol — sigue siendo necesario para distinguir Moderación de
                // Soporte Financiero — pero cierra el filter chain contra un
                // endpoint admin nuevo que se agregue sin el gate.
                .requestMatchers("/api/admin/**").access(adminActivoAuthorizationManager)
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex.accessDeniedHandler(adminAccesoDenegadoHandler()));

        return http.build();
    }

    /**
     * Mismo cuerpo/formato que {@code AdminExceptionHandler.accesoDenegado()}
     * (403, {@code {"error": "..."}}) para cuando la denegación ocurre en el
     * filter chain (AdminActivoAuthorizationManager) y nunca llega al
     * @RestControllerAdvice de cada módulo — evita que el contrato de la API
     * difiera según en qué capa se cortó la request.
     */
    private AccessDeniedHandler adminAccesoDenegadoHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"No autorizado para esta acción de administración.\"}");
        };
    }
}
