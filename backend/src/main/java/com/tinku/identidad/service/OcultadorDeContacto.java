package com.tinku.identidad.service;

import java.util.regex.Pattern;

/**
 * R-B B-9 (T2-H11, decisión D-7): saca datos de contacto de un texto público (la presentación
 * del Tutor). Las clases se coordinan dentro de Tinku; un contacto directo saltea la protección
 * del menor y el pago. Reemplaza emails, teléfonos (8 dígitos o más, con o sin separadores) y
 * usuarios de redes ({@code @usuario}) por {@value #REEMPLAZO}. La moderación posterior sigue igual.
 */
public final class OcultadorDeContacto {

    public static final String REEMPLAZO = "[contacto oculto]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    /** 8 o más dígitos, admitiendo espacios, guiones, puntos, paréntesis y un + inicial entre ellos. */
    private static final Pattern TELEFONO = Pattern.compile("\\+?\\(?\\d(?:[\\s.()-]*\\d){7,}");
    private static final Pattern USUARIO = Pattern.compile("(?<![\\w@])@[A-Za-z0-9_.]{3,}");

    private OcultadorDeContacto() {
    }

    public static String ocultar(String texto) {
        if (texto == null) {
            return null;
        }
        String r = EMAIL.matcher(texto).replaceAll(REEMPLAZO);
        r = TELEFONO.matcher(r).replaceAll(REEMPLAZO);
        return USUARIO.matcher(r).replaceAll(REEMPLAZO);
    }
}
