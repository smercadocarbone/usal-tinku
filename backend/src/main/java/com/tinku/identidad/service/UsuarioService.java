package com.tinku.identidad.service;

import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;

/**
 * Servicio de alta de Usuario adulto. Implementa el flujo de
 * Plan_M1_Identidad_Perfiles.md, sección 2.1 — leer esa sección antes de
 * modificar este archivo, el orden de las validaciones es intencional
 * (documento ilegible se distingue de documento que no coincide, que se
 * distingue de edad insuficiente, que se distingue de DNI duplicado —
 * cada una tiene una consecuencia de negocio distinta, ver FR-ID-001/007/011/018).
 */
@Service
public class UsuarioService {

    private static final int EDAD_MINIMA_ADULTO = 18;

    private final UsuarioRepository usuarioRepository;
    private final OcrService ocrService;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, OcrService ocrService, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.ocrService = ocrService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Usuario registrarAdulto(RegistroAdultoRequest request, byte[] fotoDni) {
        // Paso 2: OCR sobre la foto.
        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni);

        // Fallo de LECTURA (no de validación) — consume el contador de reintentos.
        // La política de backoff (3 intentos + 24hs) se implementa en el
        // controlador/scheduler, no acá: este método solo señaliza el caso.
        if (!ocr.documentoLegible()) {
            throw new DocumentoIlegibleException();
        }

        // Paso 4a: nombre/apellido extraído == declarado (tolerante a
        // mayúsculas/acentos, no exacto carácter por carácter).
        if (!coincideAproximado(request.nombreDeclarado(), ocr.nombreExtraido())
                || !coincideAproximado(request.apellidoDeclarado(), ocr.apellidoExtraido())) {
            throw new DocumentoNoCoincideException();
        }

        // Paso 4b: edad >= 18, calculada sobre la fecha EXTRAÍDA del
        // documento, nunca sobre la declarada (evita que alguien declare
        // una fecha distinta a la de su propio DNI).
        int edad = Period.between(ocr.fechaNacimientoExtraida(), LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA_ADULTO) {
            throw new EdadInsuficienteException("Tenés que ser mayor de 18 años para registrarte.");
        }

        // Paso 4c: unicidad del DNI en todo el sistema (FR-ID-001/018),
        // usando el DNI EXTRAÍDO del documento, no el declarado.
        if (usuarioRepository.existsByDni(ocr.dniExtraido())) {
            throw new DniYaRegistradoException();
        }

        // FR-ID-001 (constraint de aplicación además de la de BD): al
        // menos una capacidad debe estar activa.
        if (!request.capacidadEstudiante() && !request.capacidadAdultoResponsable()) {
            throw new IllegalArgumentException("Debe activar al menos una capacidad (Estudiante o Adulto Responsable).");
        }

        Usuario usuario = new Usuario();
        usuario.setDni(ocr.dniExtraido());
        usuario.setNombre(ocr.nombreExtraido());
        usuario.setApellido(ocr.apellidoExtraido());
        usuario.setFechaNacimiento(ocr.fechaNacimientoExtraida());
        usuario.setTipo(TipoUsuario.ADULTO);
        usuario.setCapacidadEstudiante(request.capacidadEstudiante());
        usuario.setCapacidadAdultoResponsable(request.capacidadAdultoResponsable());
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setEstadoCuenta(EstadoCuenta.ACTIVA);

        return usuarioRepository.save(usuario);
    }

    /**
     * Comparación tolerante: mayúsculas/minúsculas y acentos no cuentan
     * como "no coincide" — el OCR y el usuario pueden tipear "José" vs
     * "JOSE" y siguen siendo la misma persona.
     */
    private boolean coincideAproximado(String declarado, String extraido) {
        if (declarado == null || extraido == null) return false;
        return normalizar(declarado).equals(normalizar(extraido));
    }

    private String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase();
    }
}
