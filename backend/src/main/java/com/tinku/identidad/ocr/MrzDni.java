package com.tinku.identidad.ocr;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Locale;

/**
 * Zona de lectura mecánica (MRZ, formato TD1 de ICAO 9303) del dorso del DNI tarjeta:
 * <pre>
 *   IDARG12345678&lt;5&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;
 *   9005151M3005156ARG&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;6
 *   PEREZ&lt;&lt;JUAN&lt;CARLOS&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;&lt;
 * </pre>
 * Tiene dígitos de control: si el número de documento o la fecha de nacimiento no los
 * cumplen (OCR que leyó mal un carácter), devuelve {@code null} y manda el frente. Nunca
 * devuelve un resultado parcial.
 */
final class MrzDni {

    private MrzDni() {
    }

    static ResultadoOcr parse(List<String> lineas) {
        List<String> mrz = lineas.stream()
                .map(l -> l.replace(" ", "").replace('«', '<').toUpperCase(Locale.ROOT))
                .filter(l -> l.chars().filter(c -> c == '<').count() >= 2 && l.length() >= 25)
                .toList();
        for (int i = 0; i + 2 < mrz.size(); i++) {
            ResultadoOcr r = parseTres(mrz.get(i), mrz.get(i + 1), mrz.get(i + 2));
            if (r != null) {
                return r;
            }
        }
        return null;
    }

    private static ResultadoOcr parseTres(String l1, String l2, String l3) {
        if (!l1.startsWith("ID") || l1.length() < 15 || l2.length() < 15) {
            return null;
        }
        String campoDoc = aDigitos(l1.substring(5, 14));
        char controlDoc = aDigito(l1.charAt(14));
        String nacimiento = aDigitos(l2.substring(0, 6));
        char controlNac = aDigito(l2.charAt(6));
        if (!control(campoDoc, controlDoc) || !control(nacimiento, controlNac)) {
            return null;
        }
        String dni = campoDoc.replace("<", "");
        if (!dni.matches("\\d{6,8}")) {
            return null;
        }
        LocalDate fecha = fecha(nacimiento);
        String[] partes = l3.split("<<", 2);
        if (fecha == null || partes.length < 2) {
            return null;
        }
        String apellido = nombre(partes[0]);
        // Los nombres terminan en el primer "<<": lo que sigue es relleno (el OCR a veces lo lee
        // como letras y se pegaba al nombre).
        String nombre = nombre(partes[1].split("<<", 2)[0]);
        if (apellido.isEmpty() || nombre.isEmpty()) {
            return null;
        }
        return new ResultadoOcr(true, dni, nombre, apellido, fecha);
    }

    /** En campos numéricos el OCR confunde letras con dígitos: O→0, I/L→1, S→5, B→8, Z→2. */
    private static String aDigitos(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(aDigito(c));
        }
        return sb.toString();
    }

    private static char aDigito(char c) {
        return switch (c) {
            case 'O', 'Q', 'D' -> '0';
            case 'I', 'L' -> '1';
            case 'Z' -> '2';
            case 'S' -> '5';
            case 'G' -> '6';
            case 'B' -> '8';
            default -> c;
        };
    }

    /** Dígito de control ICAO 9303: pesos 7-3-1, '<' vale 0, letras A=10…Z=35. */
    static boolean control(String campo, char digito) {
        if (!Character.isDigit(digito)) {
            return false;
        }
        int[] pesos = {7, 3, 1};
        int suma = 0;
        for (int i = 0; i < campo.length(); i++) {
            char c = campo.charAt(i);
            int v;
            if (Character.isDigit(c)) {
                v = c - '0';
            } else if (c == '<') {
                v = 0;
            } else if (c >= 'A' && c <= 'Z') {
                v = c - 'A' + 10;
            } else {
                return false;
            }
            suma += v * pesos[i % 3];
        }
        return suma % 10 == digito - '0';
    }

    /** YYMMDD: el siglo se decide por la edad mínima (6 años, FR-ID-017). */
    private static LocalDate fecha(String yymmdd) {
        try {
            int yy = Integer.parseInt(yymmdd.substring(0, 2));
            int mm = Integer.parseInt(yymmdd.substring(2, 4));
            int dd = Integer.parseInt(yymmdd.substring(4, 6));
            int actual = Year.now().getValue();
            int anio = 2000 + yy;
            if (anio > actual - 6) {
                anio -= 100;
            }
            return LocalDate.of(anio, mm, dd);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String nombre(String s) {
        return s.replaceAll("<+", " ").replaceAll("[^A-ZÑ ]", "").trim().replaceAll("\\s+", " ");
    }
}
