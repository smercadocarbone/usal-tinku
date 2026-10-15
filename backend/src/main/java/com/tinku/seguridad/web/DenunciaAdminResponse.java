package com.tinku.seguridad.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.seguridad.model.Denuncia;

/**
 * R-F F-3 (T4-H7, FR-SEC-007): lo que el Admin de Moderación necesita para resolver — caso,
 * evidencia, a quién se denunció y todos los descargos. Es una vista aparte de
 * {@link DenunciaResponse} (la del denunciado), que no cambia. La identidad del denunciante no
 * viaja nunca (FR-SEC-006). {@code conflicto}: quien consulta es parte del caso (F-1).
 */
public record DenunciaAdminResponse(
        @JsonUnwrapped DenunciaResponse denuncia,
        String evidenciaUrl,
        String denunciadoNombre,
        String denunciadoApellido,
        TipoUsuario denunciadoTipo,
        String descargoHistorial,
        boolean conflicto) {

    public static DenunciaAdminResponse from(Denuncia d, Usuario denunciado, boolean conflicto) {
        return new DenunciaAdminResponse(DenunciaResponse.from(d), d.getEvidenciaUrl(),
                denunciado == null ? null : denunciado.getNombre(),
                denunciado == null ? null : denunciado.getApellido(),
                denunciado == null ? null : denunciado.getTipo(),
                d.getDescargoHistorial(), conflicto);
    }
}
