package com.tinku.identidad.service;

import com.tinku.identidad.model.EstadoFirmaCap;

/** ADR-M1-10: no se aprueba un CAP sin la firma digital verificada por el validador oficial. */
public class CapFirmaNoVerificadaException extends RuntimeException {
    public CapFirmaNoVerificadaException(EstadoFirmaCap estado) {
        super(switch (estado) {
            case PENDIENTE, EN_PROCESO -> "Todavía no se puede aprobar: la firma digital del certificado se está verificando.";
            case INVALIDA -> "No se puede aprobar: la firma digital del certificado no es válida.";
            case FIRMANTE_NO_RECONOCIDO -> "No se puede aprobar: el certificado no está firmado por el Registro Nacional de Reincidencia.";
            case ERROR -> "No se puede aprobar: no se pudo verificar la firma digital. Volvé a verificarla.";
            default -> "No se puede aprobar: la firma digital del certificado no está verificada.";
        });
    }
}
