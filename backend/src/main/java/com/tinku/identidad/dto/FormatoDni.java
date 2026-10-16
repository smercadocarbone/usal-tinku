package com.tinku.identidad.dto;

/**
 * Formato del DNI declarado al registrarse: 6 a 8 dígitos, con o sin los puntos de miles
 * ("12345678" o "12.345.678"), los mismos que lee el OCR del documento (DniParser). Sin esto
 * "3a1b2c3d4e5f6g7" pasaba (se comparaban solo los dígitos) y uno de 100 dígitos rompía la base.
 */
public final class FormatoDni {

    public static final String REGEX = "^(\\d{6,8}|\\d{1,2}\\.\\d{3}\\.\\d{3})$";
    public static final String MENSAJE = "tiene que ser el número de DNI, por ejemplo 12345678";

    private FormatoDni() {
    }

    /** Solo los dígitos: "12.345.678" y "12345678" son el mismo DNI. */
    public static String soloDigitos(String dni) {
        return dni == null ? "" : dni.replaceAll("\\D", "");
    }
}
