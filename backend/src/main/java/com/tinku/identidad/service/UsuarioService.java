package com.tinku.identidad.service;

import com.tinku.identidad.dto.ActualizarCapacidadesRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.ConsentimientoMenor;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.port.VerificadorReservasFuturas;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.ConsentimientoMenorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

import com.tinku.identidad.port.StubVerificadorReservasFuturas;

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
    private static final int EDAD_MINIMA_MENOR = 6;
    private static final int MAX_MENORES_POR_ADULTO = 5; // FR-ID-013
    private static final String VERSION_CONSENTIMIENTO_DEFAULT = "v1";

    private final UsuarioRepository usuarioRepository;
    private final OcrService ocrService;
    private final PasswordEncoder passwordEncoder;
    private final OcrBackoffService ocrBackoffService;
    private final ConsentimientoMenorRepository consentimientoRepo;
    private final AutorizacionTutorRepository autorizacionRepo;
    private final VerificadorReservasFuturas verificadorReservas;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository,
                          OcrService ocrService,
                          PasswordEncoder passwordEncoder,
                          OcrBackoffService ocrBackoffService,
                          ConsentimientoMenorRepository consentimientoRepo,
                          AutorizacionTutorRepository autorizacionRepo,
                          VerificadorReservasFuturas verificadorReservas) {
        this.usuarioRepository = usuarioRepository;
        this.ocrService = ocrService;
        this.passwordEncoder = passwordEncoder;
        this.ocrBackoffService = ocrBackoffService;
        this.consentimientoRepo = consentimientoRepo;
        this.autorizacionRepo = autorizacionRepo;
        this.verificadorReservas = verificadorReservas;
    }

    /** Constructor de test de chunks M1-C/D (sin autorizaciones ni reservas). */
    public UsuarioService(UsuarioRepository usuarioRepository,
                          OcrService ocrService,
                          PasswordEncoder passwordEncoder,
                          OcrBackoffService ocrBackoffService,
                          ConsentimientoMenorRepository consentimientoRepo) {
        this(usuarioRepository, ocrService, passwordEncoder, ocrBackoffService,
                consentimientoRepo, null, new StubVerificadorReservasFuturas());
    }

    @Transactional
    public Usuario registrarAdulto(RegistroAdultoRequest request, byte[] fotoDni) {
        // Paso 0: respetar el backoff de OCR (FR-ID-011) del DNI declarado.
        ocrBackoffService.chequearPuedeIntentar(request.dniDeclarado());

        // Paso 2: OCR sobre la foto.
        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni);

        // Fallo de LECTURA (no de validación) — consume el contador de reintentos
        // (FR-ID-011) y señaliza el caso. La política de 3 intentos + 24hs la
        // maneja OcrBackoffService, no acá.
        if (!ocr.documentoLegible()) {
            ocrBackoffService.registrarIntentoFallido(request.dniDeclarado());
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
     * Alta de un perfil de MENOR a cargo, por el Adulto Responsable
     * autenticado. El menor NUNCA se autorregistra (FR-ID-020) — esta
     * operación exige sesión del adulto (Artículo II). Mismo OCR que un
     * adulto (FR-ID-019), edad ≥ 6 (FR-ID-017), consentimiento explícito
     * separado del T&C (BR-CONSENT-01) y límite de 5 menores (FR-ID-013) —
     * todos en la misma transacción.
     */
    @Transactional
    public Usuario registrarMenor(RegistroMenorRequest request, byte[] fotoDni, Usuario adultoResponsable) {
        ocrBackoffService.chequearPuedeIntentar(request.dniDeclarado());

        // BR-CONSENT-01: consentimiento explícito y separado, obligatorio.
        if (!Boolean.TRUE.equals(request.consentimientoExplicito())) {
            throw new ConsentimientoNoOtorgadoException();
        }

        // FR-ID-013: máximo 5 perfiles de menor a cargo.
        long menoresACargo = usuarioRepository
                .countByAdultoResponsableIdAndTipo(adultoResponsable.getId(), TipoUsuario.MENOR);
        if (menoresACargo >= MAX_MENORES_POR_ADULTO) {
            throw new LimiteMenoresAlcanzadoException();
        }

        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni);
        if (!ocr.documentoLegible()) {
            ocrBackoffService.registrarIntentoFallido(request.dniDeclarado());
            throw new DocumentoIlegibleException();
        }

        // Mismas validaciones que un adulto (FR-ID-019): coincidencia
        // nombre/apellido/DNI y unicidad del DNI en el sistema.
        if (!coincideAproximado(request.nombreDeclarado(), ocr.nombreExtraido())
                || !coincideAproximado(request.apellidoDeclarado(), ocr.apellidoExtraido())) {
            throw new DocumentoNoCoincideException();
        }

        if (usuarioRepository.existsByDni(ocr.dniExtraido())) {
            throw new DniYaRegistradoException();
        }

        // FR-ID-017: edad del menor calculada sobre la fecha EXTRAÍDA del
        // documento (nunca la declarada). Rango 6..17.
        int edad = Period.between(ocr.fechaNacimientoExtraida(), LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA_MENOR || edad >= EDAD_MINIMA_ADULTO) {
            throw new EdadInsuficienteException(
                    "El perfil de menor debe tener entre 6 y 17 años.");
        }

        Usuario menor = new Usuario();
        menor.setDni(ocr.dniExtraido());
        menor.setNombre(ocr.nombreExtraido());
        menor.setApellido(ocr.apellidoExtraido());
        menor.setFechaNacimiento(ocr.fechaNacimientoExtraida());
        menor.setTipo(TipoUsuario.MENOR);
        menor.setCapacidadEstudiante(true);
        menor.setCapacidadAdultoResponsable(false);
        menor.setAdultoResponsable(adultoResponsable); // FR-ID-020
        menor.setPasswordHash(passwordEncoder.encode(request.password()));
        menor.setEstadoCuenta(EstadoCuenta.ACTIVA);
        menor = usuarioRepository.save(menor);

        // BR-CONSENT-01: persiste el consentimiento en la misma transacción.
        ConsentimientoMenor consentimiento = new ConsentimientoMenor();
        consentimiento.setMenor(menor);
        consentimiento.setAdultoResponsable(adultoResponsable);
        consentimiento.setVersionTexto(
                request.versionTextoConsentimiento() == null
                        ? VERSION_CONSENTIMIENTO_DEFAULT
                        : request.versionTextoConsentimiento());
        consentimientoRepo.save(consentimiento);

        return menor;
    }

    /**
     * Alta de Tutor (FR-ID-007): el MISMO flujo de OCR que un adulto de
     * US-1 — edad ≥ 18, coincidencia nombre/apellido/DNI y unicidad de DNI —
     * sin excepciones para menores. La capacidad se administra por separado
     * (credenciales + CAP en chunks M1-E/M1-F); acá solo se crea el perfil.
     */
    @Transactional
    public Usuario registrarTutor(RegistroTutorRequest request, byte[] fotoDni) {
        ocrBackoffService.chequearPuedeIntentar(request.dniDeclarado()); // FR-ID-011

        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni);
        if (!ocr.documentoLegible()) {
            ocrBackoffService.registrarIntentoFallido(request.dniDeclarado()); // FR-ID-011
            throw new DocumentoIlegibleException();
        }

        if (!coincideAproximado(request.nombreDeclarado(), ocr.nombreExtraido())
                || !coincideAproximado(request.apellidoDeclarado(), ocr.apellidoExtraido())) {
            throw new DocumentoNoCoincideException();
        }

        int edad = Period.between(ocr.fechaNacimientoExtraida(), LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA_ADULTO) {
            // FR-ID-007: bloqueo de registro de Tutor menor, sin excepciones.
            throw new EdadInsuficienteException("Tenés que ser mayor de 18 años para registrarte como Tutor.");
        }

        if (usuarioRepository.existsByDni(ocr.dniExtraido())) {
            throw new DniYaRegistradoException();
        }

        Usuario tutor = new Usuario();
        tutor.setDni(ocr.dniExtraido());
        tutor.setNombre(ocr.nombreExtraido());
        tutor.setApellido(ocr.apellidoExtraido());
        tutor.setFechaNacimiento(ocr.fechaNacimientoExtraida());
        tutor.setTipo(TipoUsuario.TUTOR);
        tutor.setCapacidadEstudiante(false);
        tutor.setCapacidadAdultoResponsable(false);
        tutor.setPasswordHash(passwordEncoder.encode(request.password()));
        tutor.setEstadoCuenta(EstadoCuenta.ACTIVA);

        return usuarioRepository.save(tutor);
    }

    /**
     * Activar/desactivar capacidades del usuario autenticado (FR-ID-015/016).
     *  - Activación de una capacidad complementaria: inmediata, SIN nueva
     *    verificación OCR (FR-ID-015).
     *  - No se puede desactivar "Adulto Responsable" con menores a cargo
     *    (FR-ID-016).
     *  - Al menos una capacidad debe seguir activa (FR-ID-001).
     */
    @Transactional
    public Usuario actualizarCapacidades(Usuario usuario, ActualizarCapacidadesRequest request) {
        boolean estudiante = request.capacidadEstudiante();
        boolean adultoResp = request.capacidadAdultoResponsable();

        // FR-ID-001: nunca quedar sin capacidades.
        if (!estudiante && !adultoResp) {
            throw new IllegalArgumentException("Debe mantener al menos una capacidad activa.");
        }

        // Artículo II: un menor nunca puede ser Adulto Responsable (no paga,
        // no autoriza Tutores) — solo tiene capacidad estudiante.
        if (usuario.getTipo() == TipoUsuario.MENOR && adultoResp) {
            throw new IllegalArgumentException("Un perfil de menor no puede ser Adulto Responsable.");
        }

        // FR-ID-016: no desactivar Adulto Responsable con menores a cargo.
        if (usuario.isCapacidadAdultoResponsable() && !adultoResp) {
            long menoresACargo = usuarioRepository
                    .countByAdultoResponsableIdAndTipo(usuario.getId(), TipoUsuario.MENOR);
            if (menoresACargo > 0) {
                throw new NoPuedeDesactivarAdultoResponsableException();
            }
        }

        usuario.setCapacidadEstudiante(estudiante);
        usuario.setCapacidadAdultoResponsable(adultoResp);
        return usuarioRepository.save(usuario);
    }

    /**
     * Baja definitiva de un perfil de MENOR, solo por su Adulto Responsable
     * (FR-ID-014, T-M1-12). Si el menor tiene reservas futuras, exige
     * confirmación explícita (si la tiene, se procede igualmente). Se elimina
     * el menor y sus datos dependientes (autorizaciones y consentimientos).
     */
    @Transactional
    public void darDeBajaMenor(Usuario adultoResponsable, UUID menorId, boolean confirmarBaja) {
        Usuario menor = usuarioRepository.findById(menorId)
                .orElseThrow(MenorNoPerteneceException::new);

        // FR-ID-020: solo opera sobre menores a su cargo.
        if (menor.getTipo() != TipoUsuario.MENOR
                || menor.getAdultoResponsable() == null
                || !menor.getAdultoResponsable().getId().equals(adultoResponsable.getId())) {
            throw new MenorNoPerteneceException();
        }

        // FR-ID-014: no se puede dar de baja un menor con reservas futuras sin
        // confirmación explícita (el puente a M4; stub por ahora devuelve 0).
        long reservasFuturas = verificadorReservas.contarReservasFuturas(menorId);
        if (reservasFuturas > 0 && !confirmarBaja) {
            throw new ReservasFuturasPendientesException(reservasFuturas);
        }

        autorizacionRepo.deleteByMenorId(menorId);
        consentimientoRepo.deleteByMenorId(menorId);
        usuarioRepository.delete(menor);
    }
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
