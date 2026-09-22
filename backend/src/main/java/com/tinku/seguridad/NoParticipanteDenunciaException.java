package com.tinku.seguridad;

/**
 * AUD-011: una denuncia CON sesión exige que denunciante y denunciado sean participantes
 * de esa Sesión (tutor, beneficiario o pagador). Sin esto, cualquiera podía congelar el
 * escrow de una sesión ajena. Se traduce a 403.
 */
public class NoParticipanteDenunciaException extends RuntimeException {
    public NoParticipanteDenunciaException() {
        super("Solo los participantes de la sesión pueden denunciar o ser denunciados por ella.");
    }
}
