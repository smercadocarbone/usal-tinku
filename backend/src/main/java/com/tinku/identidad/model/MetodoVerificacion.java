package com.tinku.identidad.model;

/**
 * Cómo verificó el Admin una credencial al aprobarla (ADR-M1-06, decisión 3).
 *  - {@code REGISTRO_OFICIAL}: la consultó en el registro oficial → las especialidades que cubre
 *    quedan <b>verificadas</b>.
 *  - {@code SOLO_DOCUMENTO}: papel sin registro consultable → quedan <b>declaradas con respaldo
 *    documental</b>, sin la insignia fuerte. Es también el valor de las credenciales aprobadas
 *    antes de V56.
 */
public enum MetodoVerificacion {
    REGISTRO_OFICIAL,
    SOLO_DOCUMENTO
}
