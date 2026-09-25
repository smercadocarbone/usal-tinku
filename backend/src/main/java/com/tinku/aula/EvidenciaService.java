package com.tinku.aula;

import com.tinku.seguridad.model.AlertaSeguridad;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.Almacenamiento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Evidencia del kill-switch (T-M3-08, AUD-021): el clip del buffer de 30 s se SUBE
 * como archivo y lo guarda Tinku por el puerto {@link Almacenamiento}, igual que las
 * credenciales. Antes el cliente declaraba una URL cualquiera y el Admin la abría para
 * decidir una sanción: un enlace del atacante era phishing dirigido al Admin.
 *
 * El archivo se borra al vencer la retención (BR-KS-02, job de M9 agendado al resolver).
 * Solo se acepta con la Alerta pendiente: resuelta, la retención ya corre.
 */
@Service
public class EvidenciaService {

    /** Buffer rotativo de evidencia: 30 s (Tabla de Tiempos). */
    static final int MAX_SEGUNDOS = 30;

    private final SesionService sesionService;
    private final AlertaSeguridadRepository alertaRepo;
    private final Almacenamiento almacenamiento;

    public EvidenciaService(SesionService sesionService, AlertaSeguridadRepository alertaRepo,
                            Almacenamiento almacenamiento) {
        this.sesionService = sesionService;
        this.alertaRepo = alertaRepo;
        this.almacenamiento = almacenamiento;
    }

    @Transactional
    public AlertaSeguridad subir(Usuario usuario, UUID sesionId, byte[] clip, Integer duracionSegundos) {
        if (!sesionService.participantes(sesionId).contains(usuario.getId())) {
            throw new SoloParticipanteException();
        }
        if (duracionSegundos != null && (duracionSegundos <= 0 || duracionSegundos > MAX_SEGUNDOS)) {
            throw new EvidenciaInvalidaException(
                    "el clip del buffer tiene un máximo de 30 segundos (BR-KS-01).");
        }
        if (clip == null || clip.length == 0 || !esVideo(clip)) {
            throw new EvidenciaInvalidaException("el clip tiene que ser un video WebM o MP4.");
        }
        AlertaSeguridad alerta = alertaRepo.findBySesionId(sesionId)
                .orElseThrow(AlertaNoEncontradaException::new);
        if (!AlertaSeguridad.ESTADO_PENDIENTE_REVISION.equals(alerta.getEstado())) {
            throw new EvidenciaInvalidaException("la Alerta ya se resolvió.");
        }
        String anterior = alerta.getClipUrl();
        alerta.setClipUrl(almacenamiento.guardar(clip, "evidencia-" + sesionId));
        AlertaSeguridad guardada = alertaRepo.save(alerta);
        if (anterior != null) {
            almacenamiento.borrar(anterior); // un solo clip por Alerta (minimización)
        }
        return guardada;
    }

    /** WebM/Matroska (EBML 1A 45 DF A3) o MP4/MOV (caja {@code ftyp} en el byte 4). */
    static boolean esVideo(byte[] b) {
        boolean ebml = b.length >= 4 && (b[0] & 0xFF) == 0x1A && (b[1] & 0xFF) == 0x45
                && (b[2] & 0xFF) == 0xDF && (b[3] & 0xFF) == 0xA3;
        boolean ftyp = b.length >= 8 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
        return ebml || ftyp;
    }
}
