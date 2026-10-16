package com.tinku.identidad.service;

import com.tinku.identidad.dto.AccionRevisionCap;
import com.tinku.identidad.model.CategoriaAntecedenteCap;
import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.CancelacionReservasConMenores;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Certificado de Antecedentes Penales (CAP) del Tutor que quiere dar clases a menores
 * (DT6, ADR-M1-04, FR-ID-021 a 026). Restaurado de {@code ebf6cc0} con otra semántica:
 * el CAP define una capacidad APARTE — la habilitación para menores —, nunca toca
 * {@code activoParaMatching} (TR2): la credencial académica sola habilita a enseñar a
 * adultos, y un CAP vencido no hace desaparecer al Tutor para los adultos.
 */
@Service
public class CertificadoService {

    private static final Logger LOG = LoggerFactory.getLogger(CertificadoService.class);
    private static final int MAX_INTENTOS_CICLO = CicloIntentos.MAX;
    /** FR-ID-025, Tabla de Tiempos: "Vigencia del CAP, 12 meses desde la fecha de emisión". */
    private static final int MESES_VIGENCIA = 12;
    /** Mismo huso que las reservas: "vigente hoy" es hoy en Argentina. */
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final CertificadoAntecedentesPenalesRepository capRepo;
    private final CredencialBackoffService backoffService;
    private final CancelacionReservasConMenores cancelacion;
    private final com.tinku.identidad.repository.UsuarioRepository usuarioRepo;
    private final com.tinku.shared.notificacion.Notificador notificador;
    private final VerificacionFirmaCapService verificacionFirma;

    public CertificadoService(CertificadoAntecedentesPenalesRepository capRepo,
                              CredencialBackoffService backoffService,
                              CancelacionReservasConMenores cancelacion,
                              com.tinku.identidad.repository.UsuarioRepository usuarioRepo,
                              com.tinku.shared.notificacion.Notificador notificador,
                              VerificacionFirmaCapService verificacionFirma) {
        this.verificacionFirma = verificacionFirma;
        this.usuarioRepo = usuarioRepo;
        this.notificador = notificador;
        this.capRepo = capRepo;
        this.backoffService = backoffService;
        this.cancelacion = cancelacion;
    }

    /** DT6/FR-ID-026: único lugar que decide si un Tutor puede dar clases a menores.
     *  Se calcula siempre; nunca se persiste un booleano que pueda desincronizarse. */
    @Transactional(readOnly = true)
    public boolean habilitadoParaMenores(UUID tutorId) {
        return HabilitacionMenoresCap.habilitado(capRepo, usuarioRepo, tutorId);
    }

    /**
     * V41: preferencia del Tutor de dar clases a menores. Apagarla cancela sus clases futuras
     * con menores (reembolso total y aviso al AR, como un CAP vencido). Devuelve cuántas.
     */
    @Transactional
    public int actualizarAceptaMenores(Usuario tutor, boolean acepta) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException();
        }
        Usuario u = usuarioRepo.findById(tutor.getId()).orElseThrow(SoloTutorException::new);
        u.setAceptaMenores(acepta);
        usuarioRepo.save(u);
        return acepta ? 0 : cancelacion.cancelarFuturasConMenores(u.getId());
    }

    /** ADR-M1-10: con el validador encendido, el CAP tiene que ser el PDF original firmado. */
    public boolean exigePdfFirmado() {
        return verificacionFirma.habilitado();
    }

    /** El último CAP del Tutor (su panel muestra el estado), si cargó alguno. */
    @Transactional(readOnly = true)
    public Optional<CertificadoAntecedentesPenales> ultimo(UUID tutorId) {
        return capRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutorId);
    }

    /** FR-ID-021: carga un CAP PENDIENTE. Solo Tutor, con el backoff de las credenciales. */
    @Transactional
    public CertificadoAntecedentesPenales cargarCap(Usuario tutor, String archivoUrl, LocalDate fechaEmision) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new IllegalArgumentException("Solo un perfil de Tutor puede cargar un certificado de antecedentes.");
        }
        LocalDate hoy = LocalDate.now(ZONA);
        if (fechaEmision.isAfter(hoy)) {
            throw new IllegalArgumentException("La fecha de emisión del certificado no puede ser futura.");
        }
        // FR-ID-025: vigente hasta vence_at inclusive (como HabilitacionMenoresCap); si ya pasó, nace vencido.
        if (fechaEmision.plusMonths(MESES_VIGENCIA).isBefore(hoy)) {
            throw new IllegalArgumentException(
                    "El certificado ya venció: tiene que estar emitido en los últimos 12 meses.");
        }
        backoffService.chequearPuedeIntentar(tutor.getId()); // FR-ID-021 reutiliza FR-ID-012

        capRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCap.PENDIENTE)
                .ifPresent(c -> { throw new YaExisteCredencialPendienteException(); });

        CertificadoAntecedentesPenales cap = new CertificadoAntecedentesPenales();
        cap.setTutor(tutor);
        cap.setArchivoUrl(archivoUrl);
        cap.setFechaEmision(fechaEmision);
        cap.setVenceAt(fechaEmision.plusMonths(MESES_VIGENCIA)); // FR-ID-025
        cap.setEstado(EstadoCap.PENDIENTE);
        cap.setNumeroIntento(numeroDeIntentoParaCiclo(tutor.getId()));
        verificacionFirma.encolar(cap); // ADR-M1-10: la firma digital se verifica en la cola
        return capRepo.save(cap);
    }

    private int numeroDeIntentoParaCiclo(UUID tutorId) {
        return CicloIntentos.siguiente(
                capRepo.findFirstByTutorIdOrderByCreatedAtDesc(tutorId),
                c -> c.getEstado() == EstadoCap.RECHAZADO,
                CertificadoAntecedentesPenales::getNumeroIntento);
    }

    /**
     * Revisión manual del Admin de Moderación (FR-ID-022 a 024). La categoría informada
     * manda sobre la acción pedida: un antecedente de BR-CAP-01 rechaza siempre; cualquier
     * otro deja el CAP en revisión legal y no habilita (BR-CAP-02, PT1). Aprobar solo
     * prospera sin antecedentes. Solo se revisa un CAP pendiente o en revisión legal.
     */
    /** Tutor dueño del CAP (el Admin no revisa el suyo: conflicto de interés). */
    @Transactional(readOnly = true)
    public UUID tutorDelCap(UUID capId) {
        return capRepo.findById(capId).orElseThrow(CapNoEncontradoException::new).getTutor().getId();
    }

    @Transactional
    public CertificadoAntecedentesPenales revisar(UUID capId, UUID adminRevisorId,
                                                  AccionRevisionCap accion, CategoriaAntecedenteCap categoria) {
        CertificadoAntecedentesPenales cap = capRepo.findById(capId)
                .orElseThrow(CapNoEncontradoException::new);
        if (cap.getEstado() != EstadoCap.PENDIENTE && cap.getEstado() != EstadoCap.EN_REVISION_LEGAL) {
            throw new CapNoRevisableException();
        }
        cap.setAdminRevisorId(adminRevisorId);
        cap.setRevisadoAt(Instant.now());
        // R-F F-7 (T4-H11): re-rechazar o volver a mandar a revisión legal SIN categoría no borra el
        // antecedente ya informado. Solo se limpia al aprobar sin categoría.
        boolean conservar = categoria == null && cap.getCategoriaAntecedente() != null
                && accion != AccionRevisionCap.APROBAR;
        if (!conservar) {
            cap.setTieneAntecedentes(categoria != null);
            cap.setCategoriaAntecedente(categoria == null ? null : categoria.name());
        }

        EstadoCap resultado;
        if (categoria != null && categoria.esRechazoAutomatico()) {
            resultado = EstadoCap.RECHAZADO;                                      // BR-CAP-01
        } else if (categoria != null || accion == AccionRevisionCap.EN_REVISION_LEGAL) {
            resultado = EstadoCap.EN_REVISION_LEGAL;                              // BR-CAP-02
        } else if (accion == AccionRevisionCap.RECHAZAR) {
            resultado = EstadoCap.RECHAZADO;                                      // documento inválido
        } else {
            verificacionFirma.exigirFirmaValida(cap);                             // ADR-M1-10
            resultado = EstadoCap.APROBADO;                                       // FR-ID-022
        }
        cap.setEstado(resultado);
        if (resultado == EstadoCap.RECHAZADO && cap.getNumeroIntento() >= MAX_INTENTOS_CICLO) {
            backoffService.registrarCicloAgotado(cap.getTutor().getId()); // FR-ID-021/012
        }
        // Al Tutor, solo el resultado: nunca la categoría del antecedente (T03 §2.3).
        notificador.notificar(cap.getTutor().getId(), com.tinku.shared.notificacion.TipoNotificacion.CAP_REVISADO,
                java.util.Map.of("resultado", switch (resultado) {
                    case APROBADO -> "aprobado";
                    case RECHAZADO -> "rechazado";
                    default -> "en_revision";
                }));
        return capRepo.save(cap);
    }

    /** Cola de moderación: pendientes + en revisión legal. */
    @Transactional(readOnly = true)
    public List<CertificadoAntecedentesPenales> colaModeracion() {
        return capRepo.colaConTutor(List.of(EstadoCap.PENDIENTE, EstadoCap.EN_REVISION_LEGAL));
    }

    /** ADR-M1-10: Moderación pide verificar la firma de nuevo. */
    @Transactional
    public void volverAVerificarFirma(UUID capId) {
        verificacionFirma.volverAVerificar(capId);
    }

    /** El CAP con el Tutor cargado, para responder la fila de la cola fuera de la transacción. */
    @Transactional(readOnly = true)
    public CertificadoAntecedentesPenales obtenerConTutor(UUID capId) {
        CertificadoAntecedentesPenales cap = capRepo.findById(capId).orElseThrow(CapNoEncontradoException::new);
        org.hibernate.Hibernate.initialize(cap.getTutor());
        return cap;
    }

    @Transactional(readOnly = true)
    public CertificadoAntecedentesPenales obtener(UUID capId) {
        return capRepo.findById(capId).orElseThrow(CapNoEncontradoException::new);
    }

    /**
     * FR-ID-025, disparado por {@code CapVencimientoJob}: marca {@code VENCIDO} los CAP con
     * {@code vence_at} pasado y, por cada Tutor que se quedó sin habilitación, cancela sus
     * clases futuras con menores (PT10). No toca {@code activoParaMatching} (TR2). Idempotente.
     * @return cantidad de CAP vencidos.
     */
    @Transactional
    public int marcarVencidos() {
        List<CertificadoAntecedentesPenales> vencidos = capRepo.findByEstadoInAndVenceAtBefore(
                List.of(EstadoCap.PENDIENTE, EstadoCap.APROBADO, EstadoCap.EN_REVISION_LEGAL),
                LocalDate.now(ZONA));
        Set<UUID> perdieron = new LinkedHashSet<>();
        for (CertificadoAntecedentesPenales cap : vencidos) {
            if (cap.getEstado() == EstadoCap.APROBADO) {
                perdieron.add(cap.getTutor().getId());
            }
            cap.setEstado(EstadoCap.VENCIDO);
            capRepo.save(cap);
        }
        perdieron.forEach(this::perdioHabilitacion);
        return vencidos.size();
    }

    private void perdioHabilitacion(UUID tutorId) {
        if (habilitadoParaMenores(tutorId)) {
            return; // tiene otro CAP aprobado y vigente
        }
        int canceladas = cancelacion.cancelarFuturasConMenores(tutorId);
        LOG.info("Tutor {} sin habilitación para menores: {} reservas con menores canceladas (PT10).",
                tutorId, canceladas);
    }
}
