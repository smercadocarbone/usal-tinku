package com.tinku.identidad.model;

/**
 * Motivo de rechazo de una credencial (ADR-M1-06, SPIKE §4.2: lista cerrada + texto). El texto es
 * el que ve el Tutor, en castellano llano.
 */
public enum MotivoRechazoCredencial {
    ILEGIBLE("No se puede leer el documento."),
    NO_ES_TITULO("El archivo no es un título ni un certificado."),
    NOMBRE_NO_COINCIDE("El nombre del documento no coincide con el de tu DNI."),
    NO_FIGURA_EN_REGISTRO("No encontramos el título en el registro oficial."),
    DATOS_NO_COINCIDEN("Los datos que cargaste no coinciden con el documento."),
    ENLACE_INVALIDO("El enlace o código de verificación no funciona."),
    OTRO("Otro motivo.");

    private final String texto;

    MotivoRechazoCredencial(String texto) {
        this.texto = texto;
    }

    public String getTexto() {
        return texto;
    }
}
