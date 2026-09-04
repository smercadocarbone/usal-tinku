package com.tinku.identidad.web;

import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Alta de Tutor (FR-ID-007, T-M1-09). Endpoint público (el Tutor se
 * autorregistra con su DNI), mismo flujo de OCR que el alta de adulto.
 */
@RestController
@RequestMapping("/api/tutores")
public class TutorController {

    private final UsuarioService usuarioService;

    public TutorController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroTutorRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario tutor = usuarioService.registrarTutor(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(tutor));
    }
}
