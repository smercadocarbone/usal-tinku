package com.tinku.identidad.validacion;

/** FASE2-02: la parte de la política que necesita el DNI (la contraseña no puede contenerlo). */
public final class PoliticaPassword {

    private PoliticaPassword() {
    }

    /** Lanza {@link IllegalArgumentException} (→ 422) si la contraseña contiene el DNI. */
    public static void exigirDistintaDelDni(String password, String dni) {
        if (password == null || dni == null) {
            return;
        }
        String soloDigitos = dni.replaceAll("\\D", "");
        if (!soloDigitos.isEmpty() && password.contains(soloDigitos)) {
            throw new IllegalArgumentException("La contraseña no puede contener tu DNI.");
        }
    }
}
