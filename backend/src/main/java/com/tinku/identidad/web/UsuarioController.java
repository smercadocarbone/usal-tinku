package com.tinku.identidad.web;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.identidad.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Ver Plan_M1_Identidad_Perfiles.md, sección 3 (tabla de endpoints).
 * FR-ID-015/016 (capacidades) todavía no implementados — ver
 * Tasks_Tinku_Implementacion.md, T-M1-08 (Chunk M1-D).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;

    public UsuarioController(UsuarioService usuarioService, UsuarioRepository usuarioRepository) {
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroAdultoRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario usuario = usuarioService.registrarAdulto(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(usuario));
    }

    @PostMapping(value = "/menores", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registrarMenor(
            @Valid @RequestPart("datos") RegistroMenorRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni,
            Authentication authentication
    ) throws IOException {
        // El menor no se registra solo: lo hace su Adulto Responsable
        // autenticado (FR-ID-020, Artículo II). El principal es User(dni).
        String dniAdulto = authentication.getName();
        Usuario adulto = usuarioRepository.findByDni(dniAdulto)
                .orElseThrow(() -> new IllegalStateException("Adulto responsable no encontrado"));

        Usuario menor = usuarioService.registrarMenor(request, fotoDni.getBytes(), adulto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(menor));
    }
}
