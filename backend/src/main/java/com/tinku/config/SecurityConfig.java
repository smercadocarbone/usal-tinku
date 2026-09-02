package com.tinku.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API stateless con JWT, sin sesiones de servidor
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/usuarios/registro", "/api/usuarios/login").permitAll()
                .anyRequest().authenticated()
            );
            // TODO (T-M1-05 en adelante): agregar el filtro JWT real antes de
            // implementar cualquier endpoint que no sea de registro/login.

        return http.build();
    }
}
