package com.tinku.identidad.service;

import com.tinku.identidad.model.AceptacionClausula;
import com.tinku.identidad.repository.AceptacionClausulaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * PT5 (T08): consentimiento expreso y versionado para grabar el audio de la clase (ADR-M3-04).
 * La versión vigente sale de configuración: al cambiar el texto, se sube la versión y todos
 * tienen que volver a aceptar. El alumno la acepta al contratar el adicional; el Tutor, en su perfil.
 */
@Service
public class ConsentimientoService {

    public static final String GRABACION_AUDIO_RESUMEN = "GRABACION_AUDIO_RESUMEN";
    /** ADR-M3-05: Términos y Condiciones, aceptados en el registro (incluyen la grabación). */
    public static final String TERMINOS = "TERMINOS_Y_CONDICIONES";

    private final AceptacionClausulaRepository repo;
    private final String versionGrabacion;
    private final String versionTerminos;

    public ConsentimientoService(AceptacionClausulaRepository repo,
                                 @Value("${tinku.clausulas.grabacion-audio.version}") String versionGrabacion,
                                 @Value("${tinku.clausulas.terminos.version:2026-09-26}") String versionTerminos) {
        this.repo = repo;
        this.versionGrabacion = versionGrabacion;
        this.versionTerminos = versionTerminos;
    }

    public String versionVigente(String clausula) {
        exigirConocida(clausula);
        return TERMINOS.equals(clausula) ? versionTerminos : versionGrabacion;
    }

    /**
     * ADR-M3-05: el consentimiento de la grabación de solo audio se da UNA vez, junto con los
     * Términos, al crear la cuenta (adulto o Tutor). También lo usa quien tiene que aceptar una
     * versión nueva de los Términos. Nunca se registra para un Menor.
     */
    @Transactional
    public void aceptarTerminos(UUID usuarioId) {
        aceptar(usuarioId, TERMINOS);
        aceptar(usuarioId, GRABACION_AUDIO_RESUMEN);
    }

    /** Idempotente: aceptar dos veces la misma versión no duplica la fila. */
    @Transactional
    public void aceptar(UUID usuarioId, String clausula) {
        String version = versionVigente(clausula);
        if (repo.existsByUsuarioIdAndClausulaAndVersion(usuarioId, clausula, version)) {
            return;
        }
        AceptacionClausula a = new AceptacionClausula();
        a.setUsuarioId(usuarioId);
        a.setClausula(clausula);
        a.setVersion(version);
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public boolean haAceptado(UUID usuarioId, String clausula) {
        return repo.existsByUsuarioIdAndClausulaAndVersion(usuarioId, clausula, versionVigente(clausula));
    }

    private static void exigirConocida(String clausula) {
        if (!GRABACION_AUDIO_RESUMEN.equals(clausula) && !TERMINOS.equals(clausula)) {
            throw new ClausulaDesconocidaException();
        }
    }
}
