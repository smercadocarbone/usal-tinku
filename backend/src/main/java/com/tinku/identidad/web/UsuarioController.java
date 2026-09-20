package com.tinku.identidad.web;

import com.tinku.identidad.dto.ActualizarCapacidadesRequest;
import com.tinku.identidad.dto.ActualizarEmailRequest;
import com.tinku.identidad.dto.CambiarPasswordRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.ResetearPasswordRequest;
import com.tinku.identidad.dto.SolicitarResetPasswordRequest;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.dto.VerificarDniRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.PasswordResetService;
import com.tinku.identidad.service.UsuarioService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Ver Plan_M1_Identidad_Perfiles.md, sección 3 (tabla de endpoints).
 * Capacidades combinables (T-M1-08) y alta de menor (T-M1-06) implementados;
 * el alta de Tutor vive en {@code TutorController} (T-M1-09).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UsuarioActual usuarioActual;
    private final PasswordResetService passwordResetService;

    public UsuarioController(UsuarioService usuarioService, UsuarioActual usuarioActual,
                             PasswordResetService passwordResetService) {
        this.usuarioService = usuarioService;
        this.usuarioActual = usuarioActual;
        this.passwordResetService = passwordResetService;
    }

    /** Público (auditoría 2026-09-19): siempre 204, exista o no el DNI —
     * ver PasswordResetService#solicitarReset. */
    @PostMapping("/recuperar-password")
    public ResponseEntity<Void> recuperarPassword(@Valid @RequestBody SolicitarResetPasswordRequest request) {
        passwordResetService.solicitarReset(request.dni());
        return ResponseEntity.noContent().build();
    }

    /** Público: el token del link reemplaza a la autenticación acá. */
    @PostMapping("/resetear-password")
    public ResponseEntity<Void> resetearPassword(@Valid @RequestBody ResetearPasswordRequest request) {
        passwordResetService.resetearPassword(request.token(), request.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroAdultoRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario usuario = usuarioService.registrarAdulto(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(usuario));
    }

    /** Verificación previa del DNI en el wizard (paso 2): OCR + edad + unicidad
     * sin crear la cuenta; el email/password se piden recién después de acá. */
    @PostMapping(value = "/verificar-dni", consumes = "multipart/form-data")
    public ResponseEntity<Void> verificarDni(
            @Valid @RequestPart("datos") VerificarDniRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        usuarioService.verificarDocumentoParaRegistro(request.dniDeclarado(),
                request.nombreDeclarado(), request.apellidoDeclarado(),
                request.fechaNacimientoDeclarada(), fotoDni.getBytes());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/menores", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registrarMenor(
            @Valid @RequestPart("datos") RegistroMenorRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni,
            Authentication authentication
    ) throws IOException {
        // El menor no se registra solo: lo hace su Adulto Responsable
        // autenticado (FR-ID-020, Artículo II). El principal es User(dni).
        Usuario adulto = usuarioActual.obtener(authentication);

        Usuario menor = usuarioService.registrarMenor(request, fotoDni.getBytes(), adulto);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(menor));
    }

    /** Menores a cargo del Adulto Responsable autenticado (auditoría 2026-09-18:
     *  antes solo había alta y baja por id, sin forma de volver a listarlos). */
    @GetMapping("/menores")
    public ResponseEntity<List<UsuarioResponse>> listarMenores(Authentication authentication) {
        Usuario adultoResponsable = usuarioActual.obtener(authentication);
        List<UsuarioResponse> menores = usuarioService.listarMenores(adultoResponsable).stream()
                .map(UsuarioResponse::from)
                .toList();
        return ResponseEntity.ok(menores);
    }

    /** "Editar cuenta" (auditoría 2026-09-19): perfil propio, incluido el
     * email — el JWT no lo lleva en el payload, así que la pantalla de
     * cuenta necesita un endpoint aparte para mostrarlo. */
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponse> obtenerPropio(Authentication authentication) {
        return ResponseEntity.ok(UsuarioResponse.from(usuarioActual.obtener(authentication)));
    }

    @PatchMapping("/me/email")
    public ResponseEntity<UsuarioResponse> actualizarEmail(
            @Valid @RequestBody ActualizarEmailRequest request,
            Authentication authentication
    ) {
        Usuario usuario = usuarioService.actualizarEmail(usuarioActual.obtener(authentication), request.email());
        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> cambiarPassword(
            @Valid @RequestBody CambiarPasswordRequest request,
            Authentication authentication
    ) {
        usuarioService.cambiarPassword(usuarioActual.obtener(authentication),
                request.passwordActual(), request.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/capacidades")
    public ResponseEntity<UsuarioResponse> actualizarCapacidades(
            @Valid @RequestBody ActualizarCapacidadesRequest request,
            Authentication authentication
    ) {
        Usuario usuario = usuarioService.actualizarCapacidades(usuarioActual.obtener(authentication), request);
        return ResponseEntity.ok(UsuarioResponse.from(usuario));
    }

    @DeleteMapping("/menores/{id}")
    public ResponseEntity<Void> darDeBajaMenor(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean confirmar,
            Authentication authentication
    ) {
        // FR-ID-014: solo el Adulto Responsable del menor puede darlo de baja;
        // si tiene reservas futuras se exige confirmación explícita.
        usuarioService.darDeBajaMenor(usuarioActual.obtener(authentication), id, confirmar);
        return ResponseEntity.noContent().build();
    }
}
