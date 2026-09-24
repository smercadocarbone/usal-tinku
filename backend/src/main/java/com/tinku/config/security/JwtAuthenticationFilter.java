package com.tinku.config.security;

import com.tinku.identidad.service.UsuarioDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UsuarioDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UsuarioDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (jwtUtil.isTokenValid(token)) {
                try {
                    // AUD-027: principal = UUID; el cv del token tiene que ser el vigente.
                    UserDetails userDetails = userDetailsService.cargarParaToken(
                            jwtUtil.extractUsuarioId(token), jwtUtil.extractCredentialsVersion(token));

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (UsernameNotFoundException | IllegalArgumentException e) {
                    // IllegalArgumentException: sub con DNI (token previo a AUD-027) → sin sesión, no 500.
                    // Auditoría 2026-09-18: JWT firmado válido pero la cuenta
                    // fue suspendida (M9) o eliminada después de emitirse —
                    // UsuarioDetailsService ya lo detecta, pero antes de este
                    // fix la excepción llegaba sin capturar hasta acá, antes
                    // de ExceptionTranslationFilter, y volaba como 500. Sin
                    // setear el SecurityContext, la request sigue como no
                    // autenticada y el resto de la cadena la rechaza con 401.
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
