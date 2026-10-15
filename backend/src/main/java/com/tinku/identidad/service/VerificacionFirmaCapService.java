package com.tinku.identidad.service;

import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import com.tinku.identidad.model.EstadoFirmaCap;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.port.ArchivoNoDisponibleException;
import com.tinku.identidad.port.ValidadorFirmasPdf;
import com.tinku.identidad.port.ValidadorFirmasPdf.EstadoValidacion;
import com.tinku.identidad.port.ValidadorFirmasPdf.Firma;
import com.tinku.identidad.port.ValidadorFirmasPdf.Resultado;
import com.tinku.identidad.port.ValidadorNoDisponibleException;
import com.tinku.identidad.repository.CertificadoAntecedentesPenalesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Cola de verificación de la firma digital del CAP con el validador oficial (ADR-M1-10).
 *
 * <p>Cada paso ({@link #procesarSiguiente()}, lo dispara {@code CapFirmaColaJob} cada
 * {@code tinku.cap.validador.intervalo-segundos}) hace <b>una sola</b> cosa: o consulta el
 * pedido en curso, o manda el próximo de la cola. Así Tinku nunca tiene más de un pedido
 * abierto en el validador y no lo satura. Antes de mandar, mira cuántos pedidos tiene en espera
 * el validador y no manda si llega a {@code cola-maxima} (la misma cota que usa su página).</p>
 *
 * <p>Fail-closed (Art. II): la aprobación exige {@link EstadoFirmaCap#VALIDA}. Si el validador no
 * responde, se reintenta con esperas crecientes y, agotados los intentos, el CAP queda en
 * {@code ERROR} hasta que Moderación pida verificarlo de nuevo. Nunca se aprueba sola: la firma
 * válida solo habilita la revisión manual del contenido.</p>
 */
@Service
public class VerificacionFirmaCapService {

    private static final Logger LOG = LoggerFactory.getLogger(VerificacionFirmaCapService.class);
    /** Esperas entre reintentos (Tabla de Tiempos, ADR-M1-10). */
    private static final List<Duration> ESPERAS = List.of(
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(3));

    private final CertificadoAntecedentesPenalesRepository capRepo;
    private final Almacenamiento almacenamiento;
    private final ValidadorFirmasPdf validador;
    private final boolean habilitado;
    private final int colaMaxima;
    private final int maxIntentos;
    private final Duration esperaResultado;
    private final String firmanteEsperado;

    public VerificacionFirmaCapService(CertificadoAntecedentesPenalesRepository capRepo,
                                       Almacenamiento almacenamiento,
                                       ValidadorFirmasPdf validador,
                                       @Value("${tinku.cap.validador.habilitado:false}") boolean habilitado,
                                       @Value("${tinku.cap.validador.cola-maxima:300}") int colaMaxima,
                                       @Value("${tinku.cap.validador.max-intentos:6}") int maxIntentos,
                                       @Value("${tinku.cap.validador.espera-resultado-minutos:10}") int esperaResultadoMinutos,
                                       @Value("${tinku.cap.validador.firmante-esperado:REINCIDENCIA}") String firmanteEsperado) {
        this.capRepo = capRepo;
        this.almacenamiento = almacenamiento;
        this.validador = validador;
        this.habilitado = habilitado;
        this.colaMaxima = colaMaxima;
        this.maxIntentos = maxIntentos;
        this.esperaResultado = Duration.ofMinutes(esperaResultadoMinutos);
        this.firmanteEsperado = normalizar(firmanteEsperado);
    }

    public boolean habilitado() {
        return habilitado;
    }

    /** Al cargar un CAP: entra a la cola si el validador está encendido. */
    public void encolar(CertificadoAntecedentesPenales cap) {
        reiniciar(cap, habilitado ? EstadoFirmaCap.PENDIENTE : EstadoFirmaCap.NO_VERIFICADA);
    }

    /** Moderación pide verificar de nuevo (por ejemplo, después de un {@code ERROR}). */
    public CertificadoAntecedentesPenales volverAVerificar(UUID capId) {
        CertificadoAntecedentesPenales cap = capRepo.findById(capId).orElseThrow(CapNoEncontradoException::new);
        if (!enRevision(cap)) {
            throw new CapNoRevisableException();
        }
        reiniciar(cap, habilitado ? EstadoFirmaCap.PENDIENTE : EstadoFirmaCap.NO_VERIFICADA);
        return capRepo.save(cap);
    }

    /** Regla de aprobación (ADR-M1-10): con el validador encendido, solo con firma válida. */
    public void exigirFirmaValida(CertificadoAntecedentesPenales cap) {
        if (habilitado && cap.getFirmaEstado() != EstadoFirmaCap.VALIDA) {
            throw new CapFirmaNoVerificadaException(cap.getFirmaEstado());
        }
    }

    /**
     * Un paso de la cola: consulta el pedido en curso o manda el próximo. Nunca las dos cosas,
     * nunca más de un pedido. No es {@code @Transactional}: las llamadas HTTP quedan fuera de
     * cualquier transacción y cada {@code save} confirma por su cuenta.
     */
    public void procesarSiguiente() {
        if (!habilitado) {
            return;
        }
        Optional<CertificadoAntecedentesPenales> enCurso =
                capRepo.findFirstByFirmaEstadoOrderByFirmaEnviadaAtAsc(EstadoFirmaCap.EN_PROCESO);
        if (enCurso.isPresent()) {
            consultar(enCurso.get());
            return;
        }
        capRepo.findFirstByFirmaEstadoAndFirmaProximoIntentoAtLessThanEqualOrderByFirmaProximoIntentoAtAsc(
                EstadoFirmaCap.PENDIENTE, Instant.now()).ifPresent(this::enviar);
    }

    private void enviar(CertificadoAntecedentesPenales cap) {
        if (!enRevision(cap)) { // lo rechazaron o venció mientras esperaba
            cap.setFirmaEstado(EstadoFirmaCap.NO_VERIFICADA);
            capRepo.save(cap);
            return;
        }
        byte[] archivo;
        try {
            archivo = almacenamiento.leer(cap.getArchivoUrl());
        } catch (ArchivoNoDisponibleException e) {
            terminar(cap, EstadoFirmaCap.ERROR, "No se encontró el archivo del certificado.", null);
            return;
        }
        try {
            if (validador.pedidosEnCola() >= colaMaxima) {
                LOG.info("Validador de firmas con la cola llena: el CAP {} espera al próximo paso.", cap.getId());
                return;
            }
            String id = validador.enviar(archivo);
            cap.setFirmaSha256(sha256(archivo));
            cap.setFirmaSolicitudId(id);
            cap.setFirmaEnviadaAt(Instant.now());
            cap.setFirmaEstado(EstadoFirmaCap.EN_PROCESO);
            capRepo.save(cap);
        } catch (ValidadorNoDisponibleException e) {
            fallo(cap, e.getMessage());
        }
    }

    private void consultar(CertificadoAntecedentesPenales cap) {
        Resultado r;
        try {
            r = validador.consultar(cap.getFirmaSolicitudId());
        } catch (ValidadorNoDisponibleException e) {
            fallo(cap, e.getMessage());
            return;
        }
        switch (r.estado()) {
            case EN_COLA -> {
                if (cap.getFirmaEnviadaAt() != null
                        && cap.getFirmaEnviadaAt().plus(esperaResultado).isBefore(Instant.now())) {
                    fallo(cap, "El validador no terminó en " + esperaResultado.toMinutes() + " minutos.");
                }
            }
            case PROTEGIDO -> terminar(cap, EstadoFirmaCap.INVALIDA,
                    "El PDF está protegido con contraseña y no se puede verificar.", null);
            case DESCONOCIDO -> fallo(cap, "El validador respondió un estado desconocido.");
            case TERMINADO -> aplicar(cap, r);
        }
    }

    private void aplicar(CertificadoAntecedentesPenales cap, Resultado r) {
        boolean todasValidas = !r.firmas().isEmpty() && r.firmas().stream().allMatch(Firma::valida);
        if (!r.valido() || !todasValidas) {
            String detalle = r.mensaje() == null || r.mensaje().isBlank()
                    ? "La firma digital no es válida." : r.mensaje().strip();
            terminar(cap, EstadoFirmaCap.INVALIDA, detalle, null);
            return;
        }
        // Un resultado positivo solo vale si es sobre el mismo archivo que se envió.
        if (r.sha256() == null || cap.getFirmaSha256() == null || !r.sha256().equalsIgnoreCase(cap.getFirmaSha256())) {
            terminar(cap, EstadoFirmaCap.INVALIDA, "El validador verificó un archivo distinto del enviado.", null);
            return;
        }
        Optional<Firma> esperada = r.firmas().stream().filter(this::esFirmanteEsperado).findFirst();
        if (esperada.isPresent()) {
            terminar(cap, EstadoFirmaCap.VALIDA, "Firma digital válida.", describir(esperada.get()));
        } else {
            terminar(cap, EstadoFirmaCap.FIRMANTE_NO_RECONOCIDO,
                    "La firma es válida, pero no es del Registro Nacional de Reincidencia.",
                    describir(r.firmas().get(0)));
        }
    }

    private void fallo(CertificadoAntecedentesPenales cap, String motivo) {
        int intentos = cap.getFirmaIntentos() + 1;
        cap.setFirmaIntentos(intentos);
        cap.setFirmaSolicitudId(null);
        cap.setFirmaEnviadaAt(null);
        if (intentos >= maxIntentos) {
            LOG.warn("Verificación de firma del CAP {} agotó {} intentos: {}", cap.getId(), intentos, motivo);
            terminar(cap, EstadoFirmaCap.ERROR, recortar("No se pudo verificar la firma: " + motivo), null);
            return;
        }
        Duration espera = ESPERAS.get(Math.min(intentos - 1, ESPERAS.size() - 1));
        cap.setFirmaEstado(EstadoFirmaCap.PENDIENTE);
        cap.setFirmaProximoIntentoAt(Instant.now().plus(espera));
        cap.setFirmaDetalle(recortar("Reintento " + intentos + ": " + motivo));
        capRepo.save(cap);
    }

    private void terminar(CertificadoAntecedentesPenales cap, EstadoFirmaCap estado, String detalle, String firmante) {
        cap.setFirmaEstado(estado);
        cap.setFirmaDetalle(recortar(detalle));
        cap.setFirmaFirmante(firmante == null ? null : firmante.length() > 300 ? firmante.substring(0, 300) : firmante);
        cap.setFirmaVerificadaAt(Instant.now());
        cap.setFirmaProximoIntentoAt(null);
        capRepo.save(cap);
    }

    private static void reiniciar(CertificadoAntecedentesPenales cap, EstadoFirmaCap estado) {
        cap.setFirmaEstado(estado);
        cap.setFirmaIntentos(0);
        cap.setFirmaProximoIntentoAt(estado == EstadoFirmaCap.PENDIENTE ? Instant.now() : null);
        cap.setFirmaSolicitudId(null);
        cap.setFirmaEnviadaAt(null);
        cap.setFirmaVerificadaAt(null);
        cap.setFirmaDetalle(null);
        cap.setFirmaFirmante(null);
        cap.setFirmaSha256(null);
    }

    private static boolean enRevision(CertificadoAntecedentesPenales cap) {
        return cap.getEstado() == EstadoCap.PENDIENTE || cap.getEstado() == EstadoCap.EN_REVISION_LEGAL;
    }

    private boolean esFirmanteEsperado(Firma f) {
        return normalizar(f.nombre() + " " + f.organizacion() + " " + f.unidad()).contains(firmanteEsperado);
    }

    private static String describir(Firma f) {
        StringBuilder sb = new StringBuilder(f.nombre() == null ? "" : f.nombre().strip());
        for (String parte : new String[]{f.organizacion(), f.unidad()}) {
            if (parte != null && !parte.isBlank()) {
                sb.append(sb.isEmpty() ? "" : " · ").append(parte.strip());
            }
        }
        return sb.toString();
    }

    private static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    private static String recortar(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 500);
    }

    static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
