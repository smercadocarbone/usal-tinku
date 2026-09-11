package com.tinku.identidad.service;

import com.tinku.identidad.dto.ActualizarCapacidadesRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.ConsentimientoMenor;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.DatosDniDeclarados;
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
                consentimientoRepo, null, null);
    }

    @Transactional
    public Usuario registrarAdulto(RegistroAdultoRequest request, byte[] fotoDni) {
        ResultadoOcr ocr = compuertaRegistroAdulto(request.dniDeclarado(),
                request.nombreDeclarado(), request.apellidoDeclarado(),
                request.fechaNacimientoDeclarada(), fotoDni);

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
        usuario.setEmail(request.email());
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

        ResultadoOcr ocr = validarDocumento(request.dniDeclarado(), request.nombreDeclarado(),
                request.apellidoDeclarado(), request.fechaNacimientoDeclarada(), fotoDni);

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
        ResultadoOcr ocr = compuertaRegistroAdulto(request.dniDeclarado(),
                request.nombreDeclarado(), request.apellidoDeclarado(),
                request.fechaNacimientoDeclarada(), fotoDni);

        Usuario tutor = new Usuario();
        tutor.setDni(ocr.dniExtraido());
        tutor.setNombre(ocr.nombreExtraido());
        tutor.setApellido(ocr.apellidoExtraido());
        tutor.setFechaNacimiento(ocr.fechaNacimientoExtraida());
        tutor.setTipo(TipoUsuario.TUTOR);
        tutor.setCapacidadEstudiante(false);
        tutor.setCapacidadAdultoResponsable(false);
        tutor.setPasswordHash(passwordEncoder.encode(request.password()));
        tutor.setEmail(request.email());
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
        // confirmación explícita (puente real a M4, VerificadorReservasFuturasReal).
        long reservasFuturas = verificadorReservas.contarReservasFuturas(menorId);
        if (reservasFuturas > 0 && !confirmarBaja) {
            throw new ReservasFuturasPendientesException(reservasFuturas);
        }

        autorizacionRepo.deleteByMenorId(menorId);
        consentimientoRepo.deleteByMenorId(menorId);
        usuarioRepository.delete(menor);
    }

    /**
* Perfil del Tutor por id (GET /api/tutores/{id}). Solo perfiles TUTOR;
     * cualquier otro tipo (o inexistente) responde 404.
     */
    @Transactional
    public Usuario obtenerTutor(UUID id) {
        return usuarioRepository.findById(id)
                .filter(u -> u.getTipo() == TipoUsuario.TUTOR)
                .orElseThrow(TutorNoEncontradoException::new);
    }

    /**
     * Verificación PREVIA del documento en el wizard de registro
     * (POST /api/usuarios/verificar-dni y /api/tutores/verificar-dni): corre
     * los mismos pasos del alta (FR-ID-011 backoff, FR-ID-019 OCR +
     * coincidencia, edad ≥ 18 y unicidad de DNI) pero NO crea la cuenta —
     * email y contraseña todavía no se pidieron. El alta final re-valida en
     * su transacción, así que este paso es una compuerta de UX, no una
     * garantía de estado persistido.
     */
    public void verificarDocumentoParaRegistro(String dniDeclarado, String nombreDeclarado,
                                               String apellidoDeclarado,
                                               LocalDate fechaNacimientoDeclarada,
                                               byte[] fotoDni) {
        compuertaRegistroAdulto(dniDeclarado, nombreDeclarado, apellidoDeclarado,
                fechaNacimientoDeclarada, fotoDni);
    }

    /**
     * Compuerta común de registro adulto/Tutor y de verificación previa del
     * wizard: backoff (FR-ID-011), OCR + coincidencia nombre/apellido/DNI
     * (FR-ID-019), edad >= 18 — calculada sobre la fecha EXTRAÍDA del
     * documento, nunca sobre la declarada — y unicidad de DNI (FR-ID-001/018).
     * Los tres flujos corren exactamente las mismas validaciones en el mismo
     * orden; el menor (registrarMenor) difiere en edad y checks previos.
     */
    private ResultadoOcr compuertaRegistroAdulto(String dniDeclarado, String nombreDeclarado,
                                                 String apellidoDeclarado,
                                                 LocalDate fechaNacimientoDeclarada,
                                                 byte[] fotoDni) {
        ocrBackoffService.chequearPuedeIntentar(dniDeclarado); // FR-ID-011

        ResultadoOcr ocr = validarDocumento(dniDeclarado, nombreDeclarado,
                apellidoDeclarado, fechaNacimientoDeclarada, fotoDni);

        int edad = Period.between(ocr.fechaNacimientoExtraida(), LocalDate.now()).getYears();
        if (edad < EDAD_MINIMA_ADULTO) {
            throw new EdadInsuficienteException("Tenés que ser mayor de 18 años para registrarte.");
        }

        if (usuarioRepository.existsByDni(ocr.dniExtraido())) {
            throw new DniYaRegistradoException();
        }

        return ocr;
    }

    /**
     * Pasos compartidos de los flujos de registro (FR-ID-019): lectura OCR (chunk register-flow-redesign: wizard de registro en 4 pasos (rol -> datos -> verificacion DNI -> credenciales con email) + email como credencial en backend + endpoints verificar-dni sin creacion de cuenta)
     * del documento, distinción ilegible/no-coincide y devolución del resultado.
     * El backoff (FR-ID-011), el check de edad y la unicidad del DNI se
     * orquestan en la compuerta común adulto/Tutor (compuertaRegistroAdulto);
     * el flujo de menor los aplica en un orden propio (ver javadoc de la clase).
     */
    private ResultadoOcr validarDocumento(String dniDeclarado, String nombreDeclarado,
                                          String apellidoDeclarado, LocalDate fechaNacimientoDeclarada,
                                          byte[] fotoDni) {
        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni,
                new DatosDniDeclarados(dniDeclarado, nombreDeclarado,
                        apellidoDeclarado, fechaNacimientoDeclarada));
        if (!ocr.documentoLegible()) {
            ocrBackoffService.registrarIntentoFallido(dniDeclarado);
            throw new DocumentoIlegibleException();
        }
        if (!coincideAproximado(nombreDeclarado, ocr.nombreExtraido())
                || !coincideAproximado(apellidoDeclarado, ocr.apellidoExtraido())) {
            throw new DocumentoNoCoincideException();
        }
        return ocr;
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
