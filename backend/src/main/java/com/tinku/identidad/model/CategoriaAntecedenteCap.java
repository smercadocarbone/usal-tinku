package com.tinku.identidad.model;

/**
 * Qué informa el CAP, según lo carga el Admin de Moderación al revisarlo.
 * BR-CAP-01 (rechazo automático, sin excepción ni apelación en producto): los tres primeros.
 * BR-CAP-02: cualquier otro antecedente o proceso en trámite → {@code EN_REVISION_LEGAL},
 * que no habilita (fail-closed, PT1) hasta que la asesoría legal defina el criterio.
 */
public enum CategoriaAntecedenteCap {
    /** Abuso sexual, corrupción de menores, grooming (Ley 26.904), pornografía infantil, trata sexual. */
    INTEGRIDAD_SEXUAL,
    /** Cualquier delito específicamente vinculado a menores. */
    VINCULADO_A_MENORES,
    /** Homicidio o tentativa. */
    HOMICIDIO,
    /** Propiedad, estupefacientes por tenencia, económicos, proceso sin sentencia firme… */
    OTRO;

    public boolean esRechazoAutomatico() {
        return this != OTRO;
    }
}
