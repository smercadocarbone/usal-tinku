package com.tinku.identidad.web;

import com.tinku.identidad.service.CertificadoService;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.dto.CargarCapRequest;
import com.tinku.identidad.dto.CapResponse;
import com.tinku.identidad.dto.ActualizarPerfilPublicoRequest;
import com.tinku.identidad.dto.CargarCredencialRequest;
import com.tinku.identidad.dto.CredencialResponse;
import com.tinku.identidad.dto.EstadoPerfilTutorResponse;
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
import com.tinku.identidad.port.TarifaPerfilProvider;
import com.tinku.identidad.service.ArchivoCredencialDemasiadoGrandeException;
import com.tinku.identidad.service.ArchivoCredencialInvalidoException;
import com.tinku.identidad.service.CredencialService;
import com.tinku.identidad.service.PerfilPublicoTutorService;
import com.tinku.identidad.service.UsuarioService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final TarifaPerfilProvider tarifaPerfilProvider;
    private final PerfilPublicoTutorService perfilPublicoService;
    private final CertificadoService certificadoService;
    /** Mismo valor que corta el contenedor; se re-chequea acá (ver cargarCredencial). */
    private final DataSize maxArchivoCredencial;

    public TutorController(UsuarioService usuarioService,
                           CredencialService credencialService,
                           UsuarioActual usuarioActual,
                           Almacenamiento almacenamiento,
                           PerfilMatchingProvider perfilMatchingProvider,
                           ReputacionPerfilProvider reputacionPerfilProvider,
                           TarifaPerfilProvider tarifaPerfilProvider,
                           PerfilPublicoTutorService perfilPublicoService,
                           CertificadoService certificadoService,
                           @Value("${spring.servlet.multipart.max-file-size}") DataSize maxArchivoCredencial) {
        this.maxArchivoCredencial = maxArchivoCredencial;
        this.usuarioService = usuarioService;
        this.credencialService = credencialService;
        this.usuarioActual = usuarioActual;
        this.almacenamiento = almacenamiento;
        this.perfilMatchingProvider = perfilMatchingProvider;
        this.reputacionPerfilProvider = reputacionPerfilProvider;
        this.tarifaPerfilProvider = tarifaPerfilProvider;
        this.perfilPublicoService = perfilPublicoService;
        this.certificadoService = certificadoService;
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
     * email. Materias/nivel (M2), calificaciones (M7) y precio (M5) vienen de
     * puertos; bio y foto (U1) son del propio Tutor. */
    @GetMapping("/{id}")
    public ResponseEntity<TutorPerfilResponse> obtener(@PathVariable UUID id) {
        Usuario tutor = usuarioService.obtenerTutor(id);
        return ResponseEntity.ok(perfil(tutor));
    }

    private TutorPerfilResponse perfil(Usuario tutor) {
        UUID id = tutor.getId();
        Optional<MateriasNivel> materiasNivel = perfilMatchingProvider.materiasYNivel(id);
        ReputacionTutor reputacion = reputacionPerfilProvider.reputacion(id);
        return TutorPerfilResponse.of(tutor, materiasNivel.orElse(null), reputacion,
                credencialService.existeAprobada(id),
                tarifaPerfilProvider.tarifaConfigurada(id).orElse(null),
                certificadoService.habilitadoParaMenores(id));
    }

    /** UX-06 §1: checklist de "qué me falta para recibir alumnos". Solo Tutores. */
    @GetMapping("/me/estado-perfil")
    public ResponseEntity<EstadoPerfilTutorResponse> estadoPerfil(Authentication authentication) {
        Usuario tutor = usuarioActual.obtener(authentication);
        if (tutor.getTipo() != com.tinku.identidad.model.TipoUsuario.TUTOR) {
            throw new com.tinku.identidad.service.SoloTutorException();
        }
        UUID id = tutor.getId();
        return ResponseEntity.ok(new EstadoPerfilTutorResponse(
                tutor.isActivoParaMatching(),
                credencialService.obtenerUltima(id).map(CredencialAcademica::getEstado).orElse(null),
                credencialService.existeAprobada(id),
                perfilMatchingProvider.materiasYNivel(id).map(m -> !m.materias().isEmpty()).orElse(false),
                tarifaPerfilProvider.tarifaConfigurada(id).isPresent(),
                tutor.getBio() != null,
                tutor.getFotoRef() != null,
                certificadoService.ultimo(id).map(CapResponse::from).orElse(null),
                certificadoService.habilitadoParaMenores(id)));
    }

    /**
     * FR-ID-021 (T02): el Tutor que quiere dar clases a menores sube su CAP (PDF de
     * argentina.gob.ar / Mi Argentina). Mismas validaciones que la credencial (AUD-007):
     * tamaño y tipo real por magic bytes, antes de guardar nada.
     */
    @PostMapping(value = "/antecedentes-penales", consumes = "multipart/form-data")
    public ResponseEntity<CapResponse> cargarCap(
            @Valid @RequestPart("datos") CargarCapRequest request,
            @RequestPart("archivo") MultipartFile archivo,
            Authentication authentication
    ) throws IOException {
        Usuario tutor = usuarioActual.obtener(authentication);
        byte[] contenido = archivo.getBytes();
        if (archivo.getSize() > maxArchivoCredencial.toBytes()) {
            throw new ArchivoCredencialDemasiadoGrandeException(maxArchivoCredencial);
        }
        if (TipoArchivoCredencial.detectar(contenido).isEmpty()) {
            throw new ArchivoCredencialInvalidoException();
        }
        String archivoUrl = almacenamiento.guardar(contenido, "cap-" + archivo.getOriginalFilename());
        CertificadoAntecedentesPenales cap = certificadoService.cargarCap(tutor, archivoUrl, request.fechaEmision());
        return ResponseEntity.status(HttpStatus.CREATED).body(CapResponse.from(cap));
    }

    /** U1: el Tutor autenticado actualiza la bio de su perfil público (≤ 500 caracteres). */
    @PutMapping("/me/perfil")
    public ResponseEntity<TutorPerfilResponse> actualizarPerfilPublico(
            @RequestBody ActualizarPerfilPublicoRequest request, Authentication authentication) {
        Usuario tutor = perfilPublicoService.actualizarBio(usuarioActual.obtener(authentication), request.bio());
        return ResponseEntity.ok(perfil(tutor));
    }

    /** U1: foto del perfil público. PNG o JPEG por magic bytes; mismo tope de tamaño que las credenciales. */
    @PutMapping(value = "/me/foto", consumes = "multipart/form-data")
    public ResponseEntity<TutorPerfilResponse> actualizarFoto(
            @RequestPart("archivo") MultipartFile archivo, Authentication authentication) throws IOException {
        if (archivo.getSize() > maxArchivoCredencial.toBytes()) {
            throw new ArchivoCredencialDemasiadoGrandeException(maxArchivoCredencial);
        }
        Usuario tutor = perfilPublicoService.actualizarFoto(usuarioActual.obtener(authentication), archivo.getBytes());
        return ResponseEntity.ok(perfil(tutor));
    }

    @DeleteMapping("/me/foto")
    public ResponseEntity<TutorPerfilResponse> borrarFoto(Authentication authentication) {
        Usuario tutor = perfilPublicoService.borrarFoto(usuarioActual.obtener(authentication));
        return ResponseEntity.ok(perfil(tutor));
    }

    /**
     * U1: bytes de la foto del Tutor (autenticado, como el resto del perfil). El
     * Content-Type sale de los magic bytes; {@code nosniff} + {@code CSP: sandbox}
     * como en el visor de credenciales (AUD-007). 404 si no tiene foto.
     */
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable UUID id) {
        return perfilPublicoService.foto(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(TipoArchivoCredencial.detectar(bytes)
                                .map(t -> MediaType.parseMediaType(t.getMediaType()))
                                .orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Content-Security-Policy", "sandbox")
                        .cacheControl(CacheControl.noCache().cachePrivate())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
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
                .map(c -> ResponseEntity.ok(CredencialResponse.from(
                        c, credencialService.existeAprobada(tutor.getId()))))
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
        return ResponseEntity.status(HttpStatus.CREATED).body(CredencialResponse.from(
                credencial, credencialService.existeAprobada(tutor.getId())));
    }
}
