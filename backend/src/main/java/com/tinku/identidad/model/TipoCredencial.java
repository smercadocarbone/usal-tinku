package com.tinku.identidad.model;

import java.util.Optional;

/**
 * Tipos de documento admitidos como Credencial Académica de un Tutor.
 *
 * <p>Los tres primeros son la lista cerrada original (BR-ID-01, V2): con el flag
 * {@code tinku.especialidades.habilitado} apagado son los únicos que se pueden cargar. El resto
 * son los tipos de ADR-M1-06 (SPIKE §4.2, V56), los únicos que se pueden cargar con el flag
 * encendido. La base admite todos: las credenciales ya cargadas no se tocan.</p>
 */
public enum TipoCredencial {
    TITULO,
    CERTIFICADO_ANALITICO,
    MATRICULA,

    SECUNDARIO,
    TERCIARIO_NO_DOCENTE,
    PROFESORADO,
    UNIVERSITARIO,
    ALUMNO_UNIVERSITARIO,
    MATRICULA_PROFESIONAL,
    OTRO;

    /** Tipos de ADR-M1-06 (los que se cargan con el flag encendido). */
    public boolean esDeEspecialidades() {
        return ordinal() > MATRICULA.ordinal();
    }

    /**
     * Hasta qué nivel puede respaldar una especialidad (SPIKE §4.3, tabla inicial). La materia
     * afín la decide el Admin al aprobar. Los tipos que la tabla no menciona (terciario no
     * docente, otro y los viejos) no respaldan ninguna: ante la duda, lo más restrictivo.
     */
    public Optional<NivelEspecialidad> nivelMaximoQueRespalda() {
        return switch (this) {
            case SECUNDARIO -> Optional.of(NivelEspecialidad.PRIMARIO);
            case ALUMNO_UNIVERSITARIO, PROFESORADO -> Optional.of(NivelEspecialidad.SECUNDARIO);
            case UNIVERSITARIO, MATRICULA_PROFESIONAL -> Optional.of(NivelEspecialidad.UNIVERSITARIO);
            default -> Optional.empty();
        };
    }

    /** SPIKE §4.2: el enlace o código de verificación es obligatorio si el título es digital o si
     *  es un certificado de alumno regular. */
    public boolean exigeEnlace(boolean digital) {
        return digital || this == ALUMNO_UNIVERSITARIO;
    }

    /** Paso que la tarjeta del Admin le indica según el tipo (SPIKE §4.2). */
    public String pasoASeguir(boolean digital) {
        if (digital) {
            return "Abrí el QR o el enlace (Mi Argentina / ReFE) y compará nombre y DNI.";
        }
        return switch (this) {
            case UNIVERSITARIO -> "Consultá el Registro Público de Graduados Universitarios con este DNI "
                    + "(títulos desde 2012). Si es anterior, pedí la legalización del Ministerio.";
            case ALUMNO_UNIVERSITARIO -> "Validá el código del certificado en el SIU-Guaraní de la universidad.";
            case MATRICULA_PROFESIONAL -> "Buscá la matrícula en el buscador del colegio o consejo profesional "
                    + "(salud: REFEPS/SISA).";
            case PROFESORADO -> "Si es digital, abrí el QR en Mi Argentina; si no, revisá la validez nacional "
                    + "del plan (ReNaV / INFoD).";
            case SECUNDARIO -> "Un secundario en papel no tiene registro consultable: solo se puede aprobar "
                    + "como \"solo documento\".";
            default -> "No hay un registro oficial para este tipo: revisá el documento y aprobá como "
                    + "\"solo documento\" si corresponde.";
        };
    }
}
