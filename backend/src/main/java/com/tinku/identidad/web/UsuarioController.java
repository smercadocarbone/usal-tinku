package com.tinku.identidad.web;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Ver Plan_M1_Identidad_Perfiles.md, sección 3 (tabla de endpoints).
 * FR-ID-002 (alta de menor) y FR-ID-015/016 (capacidades) todavía no
 * implementados en este controller — ver Tasks_Tinku_Implementacion.md,
 * T-M1-06 y T-M1-08.
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroAdultoRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario usuario = usuarioService.registrarAdulto(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(usuario));
    }
}
