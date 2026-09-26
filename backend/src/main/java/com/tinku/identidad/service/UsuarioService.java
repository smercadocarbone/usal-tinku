package com.tinku.identidad.service;

import com.tinku.identidad.validacion.PoliticaPassword;

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
import com.tinku.identidad.port.CancelacionReservasFuturas;
import com.tinku.identidad.port.VerificadorReservasFuturas;
import com.tinku.identidad.repository.AutorizacionTutorRepository;
import com.tinku.identidad.repository.ConsentimientoMenorRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.Base64;
import java.util.UUID;
import java.security.SecureRandom;

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

    // FASE2-06 / AUD-017 (ADR-M1-05): fecha fija de la anonimización. La tabla
    // no tiene CHECK de edad sobre fecha_nacimiento (solo NOT NULL en V2).
    private static final LocalDate FECHA_ANONIMIZADA = LocalDate.of(1900, 1, 1);

    private final UsuarioRepository usuarioRepository;
    private final OcrService ocrService;
    private final PasswordEncoder passwordEncoder;
    private final OcrBackoffService ocrBackoffService;
    private final ConsentimientoMenorRepository consentimientoRepo;
    private final AutorizacionTutorRepository autorizacionRepo;
    private final VerificadorReservasFuturas verificadorReservas;
    private final CancelacionReservasFuturas cancelacionReservas;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository,
                          OcrService ocrService,
                          PasswordEncoder passwordEncoder,
                          OcrBackoffService ocrBackoffService,
                          ConsentimientoMenorRepository consentimientoRepo,
                          AutorizacionTutorRepository autorizacionRepo,
                          VerificadorReservasFuturas verificadorReservas,
                          CancelacionReservasFuturas cancelacionReservas) {
        this.usuarioRepository = usuarioRepository;
        this.ocrService = ocrService;
        this.passwordEncoder = passwordEncoder;
        this.ocrBackoffService = ocrBackoffService;
        this.consentimientoRepo = consentimientoRepo;
        this.autorizacionRepo = autorizacionRepo;
        this.verificadorReservas = verificadorReservas;
        this.cancelacionReservas = cancelacionReservas;
    }

    /** Constructor de test de chunks M1-C/D (sin autorizaciones ni reservas). */
    public UsuarioService(UsuarioRepository usuarioRepository,
                          OcrService ocrService,
                          PasswordEncoder passwordEncoder,
                          OcrBackoffService ocrBackoffService,
                          ConsentimientoMenorRepository consentimientoRepo) {
        this(usuarioRepository, ocrService, passwordEncoder, ocrBackoffService,
                consentimientoRepo, null, null, null);
    }

    @Transactional
    public Usuario registrarAdulto(RegistroAdultoRequest request, byte[] fotoDni) {
        // FASE2-02: antes del OCR, así una contraseña inválida no consume intentos.
        PoliticaPassword.exigirDistintaDelDni(request.password(), request.dniDeclarado());
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
        // FR-ID-020 / Art. II (revisión por rol, punto 1): solo quien tiene la capacidad de
        // Adulto Responsable da de alta un menor, y nunca otro menor. Antes del OCR, para no
        // consumir intentos. Un Tutor puede serlo (decisión 2026-09-25) si la activó.
        if (adultoResponsable.getTipo() == TipoUsuario.MENOR || !adultoResponsable.isCapacidadAdultoResponsable()) {
            throw new AltaMenorNoPermitidaException();
        }
        // FASE2-02: antes del OCR, así una contraseña inválida no consume intentos.
        PoliticaPassword.exigirDistintaDelDni(request.password(), request.dniDeclarado());
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
        // FASE2-02: antes del OCR, así una contraseña inválida no consume intentos.
        PoliticaPassword.exigirDistintaDelDni(request.password(), request.dniDeclarado());
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

        // FR-ID-001: un Adulto nunca queda sin capacidades. Para un Tutor son opcionales
        // (revisión por rol, decisión tutor-padre): puede tomar clases o tener menores a cargo.
        if (!estudiante && !adultoResp && usuario.getTipo() != TipoUsuario.TUTOR) {
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
     * "Editar cuenta" (auditoría 2026-09-19): cambio de email del usuario
     * autenticado. Nunca toca nombre/apellido/dni/fechaNacimiento — esos son
     * datos verificados por OCR contra la foto del DNI en el alta, y dejarlos
     * editables por el propio usuario rompería el modelo de verificación de
     * identidad que sostiene la seguridad de menores (Artículo II).
     */
    @Transactional
    public Usuario actualizarEmail(Usuario usuario, String email) {
        if (usuarioRepository.existsByEmailAndIdNot(email, usuario.getId())) {
            throw new EmailYaRegistradoException();
        }
        usuario.setEmail(email);
        return usuarioRepository.save(usuario);
    }

    /** "Editar cuenta": cambio de contraseña. Exige la actual — reautenticar
     * la intención, no solo la sesión (FR de buena práctica, sin ticket propio). */
    @Transactional
    public void cambiarPassword(Usuario usuario, String passwordActual, String passwordNueva) {
        if (!passwordEncoder.matches(passwordActual, usuario.getPasswordHash())) {
            throw new PasswordActualIncorrectaException();
        }
        PoliticaPassword.exigirDistintaDelDni(passwordNueva, usuario.getDni());
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuario.invalidarCredenciales(); // AUD-027: las sesiones abiertas dejan de valer
        usuarioRepository.save(usuario);
    }

    /** Menores a cargo del Adulto Responsable autenticado (auditoría 2026-09-18,
     *  ver darDeBajaMenor arriba). Nunca de OTRO Adulto Responsable — el filtro
     *  de pertenencia es el propio parámetro de la consulta, no un chequeo aparte.
     *  FASE2-06 / AUD-017: excluye menores en BAJA (anonimizados). */
    public java.util.List<Usuario> listarMenores(Usuario adultoResponsable) {
        return usuarioRepository.findByAdultoResponsableIdAndTipoAndEstadoCuentaNotOrderByNombre(
                adultoResponsable.getId(), TipoUsuario.MENOR, EstadoCuenta.BAJA);
    }

    /**
     * Baja definitiva de un perfil de MENOR, solo por su Adulto Responsable
     * (FR-ID-014, T-M1-12). Si el menor tiene reservas futuras, exige
     * confirmación explícita (si la tiene, se procede igualmente).
     *
     * FASE2-06 / AUD-017 (ADR-M1-05): ya NO se borra la fila — se anonimiza.
     * Las FKs de reservas/seguridad/reputacion siguen apuntando al `id`, así
     * que el DELETE era un 500 en cuanto el menor tuvo actividad real. La Ley
     * 25.326 exige supresión de los datos identificatorios, no la destrucción
     * de los registros contables que los referencian.
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

        // Reservas futuras: se cancelan por la vía normal, en nombre del AR (el
        // pagador) — M5 reembolsa o libera según FR-RES-008 y M3 desagenda la
        // Sesión. Antes que la anonimización, así los listeners ven al menor real.
        cancelacionReservas.cancelarFuturasDeMenor(menorId, adultoResponsable.getId());

        // Vínculos de confianza operativos del menor: se siguen borrando (FK limpia).
        autorizacionRepo.deleteByMenorId(menorId);
        consentimientoRepo.deleteByMenorId(menorId);

        // Anonimización determinística (ADR-M1-05) — el DNI original queda irrecuperable.
        menor.setDni(dniAnonimo(menor.getId()));
        menor.setNombre("Perfil");
        menor.setApellido("dado de baja");
        menor.setEmail(null);
        menor.setFechaNacimiento(FECHA_ANONIMIZADA);
        byte[] secreto = new byte[32];
        new SecureRandom().nextBytes(secreto);
        menor.setPasswordHash(passwordEncoder.encode(Base64.getEncoder().encodeToString(secreto)));
        menor.setEstadoCuenta(EstadoCuenta.BAJA);
        menor.invalidarCredenciales(); // AUD-027: además de BAJA, ningún token suyo vuelve a valer
        menor.setActivoParaMatching(false);
        usuarioRepository.save(menor);
    }

    /** "BAJA-" + primeros 14 chars del UUID sin guiones: determinístico y único
     *  por menor, respeta VARCHAR(20) UNIQUE de V2 (ADR-M1-05). */
    private String dniAnonimo(UUID id) {
        return "BAJA-" + id.toString().replace("-", "").substring(0, 14);
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
     * Pasos compartidos de los flujos de registro (FR-ID-019): lectura OCR
     * del documento, distinción ilegible/no-coincide/servicio-caído y
     * devolución del resultado.
     *
     * <p>Orden de fallos, intencional: si el OCR no está disponible, el
     * usuario no puede hacer nada — se rechaza ANTES de consumir la foto; si
     * la foto no se lee, es ilegible (consume backoff FR-ID-011); si se leyó
     * pero los datos declarados no coinciden con los del documento (nombre,
     * apellido, fecha de nacimiento o número de DNI), es "no coincide".
     * Recién después se evalúa la edad, siempre sobre lo EXTRAÍDO (nunca lo
     * declarado) — por eso un DNI con fecha que no coincide jamás cae en
     * "menor de edad": primero se rechaza el dato que no coincide.
     */
    private ResultadoOcr validarDocumento(String dniDeclarado, String nombreDeclarado,
                                          String apellidoDeclarado, LocalDate fechaNacimientoDeclarada,
                                          byte[] fotoDni) {
        ResultadoOcr ocr = ocrService.procesarDocumento(fotoDni,
                new DatosDniDeclarados(dniDeclarado, nombreDeclarado,
                        apellidoDeclarado, fechaNacimientoDeclarada));
        // OcrNoDisponibleException propaga tal cual: no es falta del usuario,
        // no consume reintentos y se traduce a 503 en el handler.
        if (!ocr.documentoLegible()) {
            ocrBackoffService.registrarIntentoFallido(dniDeclarado);
            throw new DocumentoIlegibleException();
        }
        if (!mismaIdentidad(dniDeclarado, nombreDeclarado, apellidoDeclarado,
                fechaNacimientoDeclarada, ocr)) {
            throw new DocumentoNoCoincideException();
        }
        return ocr;
    }

    /**
     * Todo lo que el usuario declaró en el formulario debe coincidir con lo
     * que el OCR leyó del documento: número de DNI, nombre, apellido y fecha
     * de nacimiento. Nombre/apellido comparan sin acentos y sin distinguir
     * mayúsculas/minúsculas (el documento suele venir en MAYÚSCULAS); el DNI
     * ignora separadores de miles; la fecha se compara exacta.
     */
    private boolean mismaIdentidad(String dniDeclarado, String nombreDeclarado,
                                   String apellidoDeclarado, LocalDate fechaNacimientoDeclarada,
                                   ResultadoOcr ocr) {
        return soloDigitos(dniDeclarado).equals(soloDigitos(ocr.dniExtraido()))
                && coincideAproximado(nombreDeclarado, ocr.nombreExtraido())
                && coincideAproximado(apellidoDeclarado, ocr.apellidoExtraido())
                && fechaNacimientoDeclarada != null
                && fechaNacimientoDeclarada.equals(ocr.fechaNacimientoExtraida());
    }

    private String soloDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    /**
     * Nombre o apellido declarado contra el leído. El número de DNI y la fecha de
     * nacimiento se exigen exactos; acá se toleran dos cosas del mundo real (2026-09-25,
     * nadie con un DNI real lograba registrarse): el OCR que confunde un carácter cada
     * tanto (distancia de edición ≤ 1 cada 6 letras) y el usuario que escribe solo su
     * primer nombre cuando el documento trae dos ("Juan" contra "JUAN CARLOS").
     */
    private boolean coincideAproximado(String declarado, String extraido) {
        if (declarado == null || extraido == null) return false;
        String d = normalizar(declarado).replaceAll("\\s+", " ");
        String e = normalizar(extraido).replaceAll("\\s+", " ");
        if (d.isEmpty()) return false;
        if (parecidos(d, e)) return true;
        // Solo el primer nombre/apellido: tiene que coincidir con la primera palabra del documento.
        String primera = e.split(" ")[0];
        return !d.contains(" ") && parecidos(d, primera);
    }

    private static boolean parecidos(String a, String b) {
        int tolerancia = Math.max(1, Math.max(a.length(), b.length()) / 6);
        return distancia(a, b) <= tolerancia;
    }

    /** Distancia de Levenshtein (nombres cortos: la matriz entera no pesa). */
    private static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previa[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo);
            }
            int[] t = previa; previa = actual; actual = t;
        }
        return previa[b.length()];
    }

    private String normalizar(String s) {
        String sinAcentos = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.toUpperCase();
    }
}
