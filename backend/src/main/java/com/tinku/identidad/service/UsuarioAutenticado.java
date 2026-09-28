package com.tinku.identidad.service;

import com.tinku.identidad.model.Usuario;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/**
 * Principal del filtro JWT (AUD-027, AUD-036.1): el nombre es el UUID del usuario y además lleva
 * el {@link Usuario} que el filtro ya cargó para validar la cuenta, así {@code UsuarioActual} no
 * lo vuelve a buscar. Con {@code open-in-view: false} es la misma entidad desacoplada que antes
 * devolvía el repositorio.
 */
public class UsuarioAutenticado extends User {

    private final transient Usuario usuario;

    public UsuarioAutenticado(Usuario usuario) {
        super(usuario.getId().toString(), usuario.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getTipo().name())));
        this.usuario = usuario;
    }

    public Usuario getUsuario() {
        return usuario;
    }
}
