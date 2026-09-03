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
}
