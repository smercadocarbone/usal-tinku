package com.tinku.identidad.web;

import com.tinku.identidad.dto.CargarCredencialRequest;
import com.tinku.identidad.dto.CredencialResponse;
import com.tinku.identidad.dto.MateriasNivel;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.dto.ReputacionTutor;
import com.tinku.identidad.dto.TutorPerfilResponse;
import com.tinku.identidad.dto.UsuarioResponse;
import com.tinku.identidad.dto.VerificarDniRequest;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.TipoArchivoCredencial;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.port.PerfilMatchingProvider;
import com.tinku.identidad.port.ReputacionPerfilProvider;
import com.tinku.identidad.service.ArchivoCredencialDemasiadoGrandeException;
import com.tinku.identidad.service.ArchivoCredencialInvalidoException;
import com.tinku.identidad.service.CredencialService;
import com.tinku.identidad.service.UsuarioService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Alta de Tutor (FR-ID-007, T-M1-09, endpoint público de autorregistro) y
 * carga de Credencial Académica (US-4, T-M1-10, autenticado: solo el Tutor).
 */
@RestController
@RequestMapping("/api/tutores")
public class TutorController {

    private final UsuarioService usuarioService;
    private final CredencialService credencialService;
    private final UsuarioActual usuarioActual;
    private final Almacenamiento almacenamiento;
    private final PerfilMatchingProvider perfilMatchingProvider;
    private final ReputacionPerfilProvider reputacionPerfilProvider;
    /** Mismo valor que corta el contenedor; se re-chequea acá (ver cargarCredencial). */
    private final DataSize maxArchivoCredencial;

    public TutorController(UsuarioService usuarioService,
                           CredencialService credencialService,
                           UsuarioActual usuarioActual,
                           Almacenamiento almacenamiento,
                           PerfilMatchingProvider perfilMatchingProvider,
                           ReputacionPerfilProvider reputacionPerfilProvider,
                           @Value("${spring.servlet.multipart.max-file-size}") DataSize maxArchivoCredencial) {
        this.maxArchivoCredencial = maxArchivoCredencial;
        this.usuarioService = usuarioService;
        this.credencialService = credencialService;
        this.usuarioActual = usuarioActual;
        this.almacenamiento = almacenamiento;
        this.perfilMatchingProvider = perfilMatchingProvider;
        this.reputacionPerfilProvider = reputacionPerfilProvider;
    }

    @PostMapping(value = "/registro", consumes = "multipart/form-data")
    public ResponseEntity<UsuarioResponse> registro(
            @Valid @RequestPart("datos") RegistroTutorRequest request,
            @RequestPart("fotoDni") MultipartFile fotoDni
    ) throws IOException {
        Usuario tutor = usuarioService.registrarTutor(request, fotoDni.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(tutor));
    }

/** Perfil público del Tutor (autenticado). No expone DNI, passwordHash ni
     * capacidad de pago. Materias/nivel (M2) y calificaciones (M7) vienen de
     * puertos con stubs hasta que esos módulos existan. */
    @GetMapping("/{id}")
    public ResponseEntity<TutorPerfilResponse> obtener(@PathVariable UUID id) {
        Usuario tutor = usuarioService.obtenerTutor(id);
        Optional<MateriasNivel> materiasNivel = perfilMatchingProvider.materiasYNivel(id);
        ReputacionTutor reputacion = reputacionPerfilProvider.reputacion(id);
        return ResponseEntity.ok(TutorPerfilResponse.of(
                tutor, materiasNivel.orElse(null), reputacion));
    }

    /** Verificación previa del DNI del Tutor en el wizard (FR-ID-007), sin
     * crear la cuenta — misma compuerta que /api/usuarios/verificar-dni. */
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

    /** Estado real de la credencial del Tutor autenticado (auditoría
     * 2026-09-19). 204 si todavía no cargó ninguna. */
    @GetMapping("/me/credencial")
    public ResponseEntity<CredencialResponse> miCredencial(Authentication authentication) {
        Usuario tutor = usuarioActual.obtener(authentication);
        return credencialService.obtenerUltima(tutor.getId())
                .map(c -> ResponseEntity.ok(toResponse(c)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping(value = "/credenciales", consumes = "multipart/form-data")
    public ResponseEntity<CredencialResponse> cargarCredencial(
            @Valid @RequestPart("datos") CargarCredencialRequest request,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication authentication
    ) throws IOException {
        Usuario tutor = usuarioActual.obtener(authentication);
        byte[] contenido = archivo.getBytes();
        // AUD-007: tamaño y allowlist por contenido real, antes de guardar nada. El
        // límite de spring.servlet.multipart lo aplica el contenedor; se repite acá
        // para no depender de cómo se despliegue (y para poder testearlo).
        if (archivo.getSize() > maxArchivoCredencial.toBytes()) {
            throw new ArchivoCredencialDemasiadoGrandeException(maxArchivoCredencial);
        }
        if (TipoArchivoCredencial.detectar(contenido).isEmpty()) {
            throw new ArchivoCredencialInvalidoException();
        }
        String archivoUrl = almacenamiento.guardar(contenido, archivo.getOriginalFilename());
        CredencialAcademica credencial =
                credencialService.cargarCredencial(tutor, request.tipoDocumento(), archivoUrl);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(credencial));
    }

    private CredencialResponse toResponse(CredencialAcademica c) {
        return new CredencialResponse(c.getId(), c.getTipoDocumento(), c.getEstado(),
                c.getNumeroIntento(), c.getCreatedAt());
    }
}
