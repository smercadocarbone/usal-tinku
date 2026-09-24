package com.tinku.identidad.service;

import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String dni) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByDni(dni)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado con DNI: " + dni));

        if (usuario.getEstadoCuenta() != EstadoCuenta.ACTIVA) {
            throw new UsernameNotFoundException(
                    "Cuenta inactiva para DNI: " + dni);
        }

        return new User(
                usuario.getDni(),
                usuario.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo().name())));
    }

    /**
     * Para el filtro JWT (AUD-027): carga por id, exige cuenta ACTIVA y que la versión de
     * credenciales del token sea la vigente. El principal queda con el UUID como nombre.
     */
    public UserDetails cargarParaToken(UUID usuarioId, Integer cvDelToken) throws UsernameNotFoundException {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario del token inexistente"));
        if (u.getEstadoCuenta() != EstadoCuenta.ACTIVA) {
            throw new UsernameNotFoundException("Cuenta inactiva");
        }
        if (cvDelToken == null || cvDelToken != u.getCredentialsVersion()) {
            throw new UsernameNotFoundException("Token emitido antes de un cambio de credenciales");
        }
        return new User(u.getId().toString(), u.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getTipo().name())));
    }
}
