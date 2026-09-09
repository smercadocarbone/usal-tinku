package com.tinku.identidad.web;

import com.tinku.identidad.dto.CapResponse;
import com.tinku.identidad.dto.CargarCapRequest;
import com.tinku.identidad.dto.CargarCredencialRequest;
import com.tinku.identidad.dto.CredencialResponse;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.service.CertificadoService;
import com.tinku.identidad.service.CredencialService;
import com.tinku.identidad.service.UsuarioService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Alta de Tutor (FR-ID-007, T-M1-09, endpoint público de autorregistro) y
 * carga de Credencial Académica (US-4, T-M1-10, autenticado: solo el Tutor).
 */
@RestController
@RequestMapping("/api/tutores")
public class TutorController {

    private final UsuarioService usuarioService;
    private final CredencialService credencialService;
    private final CertificadoService certificadoService;
    private final UsuarioActual usuarioActual;
    private final Almacenamiento almacenamiento;

    public TutorController(UsuarioService usuarioService,
                           CredencialService credencialService,
                           CertificadoService certificadoService,
                           UsuarioActual usuarioActual,
                           Almacenamiento almacenamiento) {
        this.usuarioService = usuarioService;
        this.credencialService = credencialService;
        this.certificadoService = certificadoService;
        this.usuarioActual = usuarioActual;
        this.almacenamiento = almacenamiento;
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroTutorRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario tutor = usuarioService.registrarTutor(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(tutor));
    }

    @PostMapping(value = "/credenciales", consumes = "multipart/form-data")
    public ResponseEntity<CredencialResponse> cargarCredencial(
            @Valid @RequestPart("datos") CargarCredencialRequest request,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication authentication
    ) throws IOException {
        Usuario tutor = usuarioActual.obtener(authentication);
        String archivoUrl = almacenamiento.guardar(archivo.getBytes(), archivo.getOriginalFilename());
        CredencialAcademica credencial =
                credencialService.cargarCredencial(tutor, request.tipoDocumento(), archivoUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(credencial));
    }

    private CredencialResponse toResponse(CredencialAcademica c) {
        return new CredencialResponse(c.getId(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCreatedAt());
    }

    @PostMapping(value = "/antecedentes-penales", consumes = "multipart/form-data")
    public ResponseEntity<CapResponse> cargarCap(
            @Valid @RequestPart("datos") CargarCapRequest request,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication authentication
    ) throws IOException {
        Usuario tutor = usuarioActual.obtener(authentication);
        String archivoUrl = almacenamiento.guardar(archivo.getBytes(), archivo.getOriginalFilename());
        CertificadoAntecedentesPenales cap =
                certificadoService.cargarCap(tutor, archivoUrl, request.fechaEmision());
        return ResponseEntity.status(HttpStatus.CREATED).body(CapResponse.from(cap));
    }
}
