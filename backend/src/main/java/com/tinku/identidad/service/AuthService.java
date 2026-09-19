package com.tinku.identidad.service;

import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.TokenResponse;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.model.EstadoCuenta;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final long expirationMinutes;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Value("${tinku.jwt.expiration-minutes}") long expirationMinutes) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.expirationMinutes = expirationMinutes;
    }

    public TokenResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByDni(request.dni())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        // Auditoría 2026-09-18: sin este chequeo, una cuenta SUSPENDIDA por
        // sanción de M9 (kill-switch, denuncia fundada) podía loguearse con
        // normalidad — credenciales válidas no implican cuenta activa. Mismo
        // criterio que UsuarioDetailsService.loadUserByUsername (M8/JWT), que
        // sí lo chequeaba, dejando este camino como el único agujero real.
        if (usuario.getEstadoCuenta() != EstadoCuenta.ACTIVA) {
            throw new DisabledException("Cuenta suspendida");
        }

        String token = jwtUtil.generateToken(
                usuario.getDni(),
                usuario.getTipo().name(),
                usuario.isCapacidadEstudiante(),
                usuario.isCapacidadAdultoResponsable());

        return new TokenResponse(token, usuario.getTipo().name(), expirationMinutes);
    }
}
