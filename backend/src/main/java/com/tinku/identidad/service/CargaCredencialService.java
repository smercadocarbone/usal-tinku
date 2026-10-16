package com.tinku.identidad.service;

import com.tinku.identidad.dto.CargarCredencialRequest;
import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.LectorTextoDocumento;
import com.tinku.identidad.ocr.TextoDocumento;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.repository.CredencialAcademicaRepository;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Carga estructurada de una credencial con triaje automático (ADR-M1-06, chunk E2). Solo con
 * {@code tinku.especialidades.habilitado} encendido; apagado, la carga es la de siempre
 * ({@link CredencialService#cargarCredencial(Usuario, TipoCredencial, String)}).
 *
 * <p>Orden: primero lo barato (datos, consentimiento, enlace, espera y pendiente), después el OCR.
 * Un rechazo del triaje no guarda nada ni consume intento: se le dice al Tutor qué corregir. El
 * OCR no corre dentro de una transacción (puede tardar).</p>
 */
@Service
public class CargaCredencialService {

    private static final int MAX_TEXTO = 200;
    /** Un código de verificación suelto (sin enlace), como el de un certificado del SIU-Guaraní. */
    private static final java.util.regex.Pattern CODIGO = java.util.regex.Pattern.compile("[A-Za-z0-9-]{4,100}");

    private final CredencialService credencialService;
    private final CredencialBackoffService backoffService;
    private final CredencialAcademicaRepository credencialRepo;
    private final EspecialidadService especialidades;
    private final ConsentimientoService consentimiento;
    private final LectorTextoDocumento lector;
    private final Almacenamiento almacenamiento;

    public CargaCredencialService(CredencialService credencialService,
                                  CredencialBackoffService backoffService,
                                  CredencialAcademicaRepository credencialRepo,
                                  EspecialidadService especialidades,
                                  ConsentimientoService consentimiento,
                                  LectorTextoDocumento lector,
                                  Almacenamiento almacenamiento) {
        this.credencialService = credencialService;
        this.backoffService = backoffService;
        this.credencialRepo = credencialRepo;
        this.especialidades = especialidades;
        this.consentimiento = consentimiento;
        this.lector = lector;
        this.almacenamiento = almacenamiento;
    }

    public CredencialAcademica cargar(Usuario tutor, CargarCredencialRequest r, byte[] contenido,
                                      String nombreArchivo) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new IllegalArgumentException("Solo un perfil de Tutor puede cargar credenciales.");
        }
        TipoCredencial tipo = r.tipoDocumento();
        if (!tipo.esDeEspecialidades()) {
            throw new TipoCredencialNoAdmitidaException();
        }
        if (!Boolean.TRUE.equals(r.aceptaConsultaRegistros())) {
            throw new DatosCredencialInvalidosException("Para cargar la credencial tenés que aceptar que el "
                    + "equipo la consulte en los registros oficiales con tu DNI.");
        }
        String institucion = texto(r.institucion(), "Indicá la institución que emitió el documento.");
        String denominacion = texto(r.denominacion(), "Indicá el nombre del título o certificado.");
        Integer anio = r.anioEgreso();
        if (anio == null || anio < 1900 || anio > Year.now().getValue()) {
            throw new DatosCredencialInvalidosException("Indicá un año de egreso (o de cursada) válido.");
        }
        boolean digital = Boolean.TRUE.equals(r.esDigital());
        String enlace = r.enlaceVerificacion() == null || r.enlaceVerificacion().isBlank()
                ? null : r.enlaceVerificacion().strip();
        if (enlace == null && tipo.exigeEnlace(digital)) {
            throw new DatosCredencialInvalidosException("Para este documento tenés que indicar el enlace o el "
                    + "código de verificación.");
        }
        if (enlace != null && !CODIGO.matcher(enlace).matches()) {
            TriajeCredencial.motivoEnlaceInvalido(enlace).ifPresent(m -> {
                throw new DatosCredencialInvalidosException(m);
            });
        }
        if (!especialidades.tieneEspecialidades(tutor.getId())) {
            throw new DatosCredencialInvalidosException("Primero elegí al menos una especialidad: la "
                    + "credencial es la que la respalda.");
        }
        // Antes del OCR: si igual no podría cargarla, no se procesa el archivo.
        backoffService.chequearPuedeIntentar(tutor.getId());
        credencialRepo.findByTutorIdAndEstado(tutor.getId(), EstadoCredencial.PENDIENTE)
                .ifPresent(c -> { throw new YaExisteCredencialPendienteException(); });

        TextoDocumento leido = lector.leer(contenido);
        TriajeCredencial.motivoRechazoPorTexto(leido).ifPresent(m -> {
            throw new DatosCredencialInvalidosException(m);
        });
        Boolean nombreCoincide = TriajeCredencial.nombreCoincide(leido, tutor.getNombre(), tutor.getApellido());

        consentimiento.aceptar(tutor.getId(), ConsentimientoService.CONSULTA_REGISTROS);
        String archivoUrl = almacenamiento.guardar(contenido, nombreArchivo);
        return credencialService.cargarCredencial(tutor, tipo, archivoUrl, c -> {
            c.setInstitucion(institucion);
            c.setDenominacion(denominacion);
            c.setAnioEgreso(anio);
            c.setEsDigital(digital);
            c.setEnlaceVerificacion(enlace);
            c.setNombreCoincide(nombreCoincide);
        });
    }

    /** Prellenado por OCR (institución, título y año) que el Tutor confirma. */
    public TriajeCredencial.Prellenado prellenar(Usuario tutor, byte[] contenido) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new SoloTutorException();
        }
        return TriajeCredencial.prellenar(lector.leer(contenido));
    }

    private static String texto(String valor, String mensaje) {
        if (valor == null || valor.isBlank()) {
            throw new DatosCredencialInvalidosException(mensaje);
        }
        String v = valor.strip();
        if (v.length() > MAX_TEXTO) {
            throw new DatosCredencialInvalidosException("Cada dato puede tener hasta " + MAX_TEXTO + " caracteres.");
        }
        return v;
    }
}
