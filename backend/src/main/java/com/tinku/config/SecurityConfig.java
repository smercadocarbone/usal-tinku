package com.tinku.config;

import com.tinku.config.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // API stateless con JWT, sin sesiones de servidor
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Rutas publicas: registro de adulto, registro de tutor
                // (autorregistro con DNI, FR-ID-007) y login. El alta de
                // menor NO es pública: la hace el Adulto Responsable
                // autenticado (FR-ID-020, Artículo II).
                .requestMatchers("/api/usuarios/registro", "/api/usuarios/login",
                        "/api/tutores/registro").permitAll()
                // Webhook de LiveKit: la autenticación ES su firma HS256 sobre
                // el body (T-M3-02), no el JWT de Tinku — mismo patrón que el
                // webhook de MercadoPago (M5-B). Sin firma válida → 401.
                .requestMatchers("/api/webhooks/livekit").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
