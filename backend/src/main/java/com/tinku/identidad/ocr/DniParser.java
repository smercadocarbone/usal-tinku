package com.tinku.identidad.ocr;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte el texto crudo que devuelve el OCR de un DNI en los campos de
 * identidad que necesita M1 (nombre, apellido, dni, fecha de nacimiento).
 *
 * ADR-M1-01: Cloud Vision devuelve texto crudo, NO campos estructurados —
 * el parsing es responsabilidad propia y es la parte de mayor riesgo de
 * exactitud. Por eso es un componente AISLADO (no depende de Google) con sus
 * propios tests unitarios contra muestras de AMBOS formatos de DNI argentino
 * vigentes:
 *   - Libreta (pre-2009): campos rotulados ("APELLIDO:", "NOMBRES:", DNI,
 *     "NACIMIENTO:") en líneas.
 *   - Tarjeta plástica (2009+): sin rótulos; apellido en una línea, nombres
 *     en la siguiente, número de DNI en gran formato ("Nº ..."), luego
 *     "Fecha de nacimiento".
 *
 * Invariante: nunca retornar un {@link ResultadoOcr} con campos parciales
 * inconsistentes. Si no se puede extraer el juego completo de forma confiable,
 * se devuelve {@link ResultadoOcr#ilegible()} para que el flujo de registro
 * lo trate como fallo de lectura (reintentos, no rechazo de identidad).
 */
@Component
public class DniParser {

    // DNI: 6 a 8 dígitos. Puede venir con separadores de miles ("12.345.678").
    private static final Pattern DNI_CRUDO = Pattern.compile("\\d[\\d. ]*\\d");
    private static final Pattern DNI_SOLO_DIGITOS = Pattern.compile("(?<![\\d.])(\\d{6,8})(?![\\d.])");

    // Fecha: dd/mm/aaaa o dd-mm-aaaa.
    private static final Pattern FECHA = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})");

    private static final Pattern MARCA_DNI =
            Pattern.compile("(?:N\\s*[º°]|N\\s*\\.?\\s*\\d|N[uú]?mero|N\\s+DE\\b|\\bDNI\\b|D\\.N\\.I\\.|\\bDoc(?:umento)?\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARCA_NACIMIENTO =
            Pattern.compile("nac(imiento|id[oa])|fecha\\s+de\\s+nac|nac\\.", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROTULO_VALOR =
            Pattern.compile("^([A-ZÁÉÍÓÚÑ ]+?)\\s*[:\\-]\\s*(.*)$");

    public ResultadoOcr parse(List<LineaTexto> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            return ResultadoOcr.ilegible();
        }

        // Orden de lectura de arriba hacia abajo (y luego por x dentro de la línea).
        List<String> orden = lineas.stream()
                .sorted(Comparator.comparingDouble(LineaTexto::y))
                .map(LineaTexto::texto)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        String textoCompleto = String.join("\n", orden);

        String dni = extraerDni(orden, textoCompleto);
        LocalDate fechaNacimiento = extraerFechaNacimiento(orden, textoCompleto);
        String[] nombreYApellido = extraerNombreYApellido(orden, textoCompleto);

        if (dni == null || fechaNacimiento == null
                || nombreYApellido[0] == null || nombreYApellido[1] == null) {
            // Juego incompleto → ilegible, nunca campos parciales.
            return ResultadoOcr.ilegible();
        }

        // OJO: ResultadoOcr es (dni, nombre, apellido, fecha). nombreYApellido
        // internamente es [apellido, nombre].
        return new ResultadoOcr(true, dni, nombreYApellido[1], nombreYApellido[0], fechaNacimiento);
    }

    // ------------------------------------------------------------------
    // DNI
    // ------------------------------------------------------------------

    private String extraerDni(List<String> orden, String texto) {
        // 1) Preferir el número asociado a una marca tipo "Nº", "DNI", "Número".
        int indiceMarca = -1;
        for (int i = 0; i < orden.size(); i++) {
            if (MARCA_DNI.matcher(orden.get(i)).find()) {
                indiceMarca = i;
                break;
            }
        }
        if (indiceMarca >= 0) {
            String candidato = colorearDesdeMarca(orden, indiceMarca);
            String normalizado = normalizarDni(candidato);
            if (esDniValido(normalizado)) {
                return normalizado;
            }
        }

        // 2) Línea que ES SOLO un número de 6-8 dígitos (nº grande de la tarjeta).
        for (String linea : orden) {
            if (esSoloNumeroDni(linea)) {
                return normalizarDni(linea);
            }
        }

        // 3) Primera secuencia de 6-8 dígitos en todo el texto.
        Matcher m = DNI_SOLO_DIGITOS.matcher(texto);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /** Devuelve el nº de 6-8 dígitos que aparece junto a una marca "Nº" (misma línea o la siguiente). */
    private String colorearDesdeMarca(List<String> orden, int indiceMarca) {
        for (int k = indiceMarca; k < Math.min(indiceMarca + 2, orden.size()); k++) {
            Matcher m = DNI_SOLO_DIGITOS.matcher(normalizarDni(orden.get(k)));
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private String normalizarDni(String s) {
        if (s == null) return null;
        // Queda solo la secuencia de dígitos (quita separadores de miles, "Nº", ":", etc.).
        return s.replaceAll("\\D", "");
    }

    private boolean esDniValido(String s) {
        return s != null && s.matches("\\d{6,8}");
    }

    // ------------------------------------------------------------------
    // Fecha de nacimiento
    // ------------------------------------------------------------------

    private LocalDate extraerFechaNacimiento(List<String> orden, String texto) {
        // Preferir una fecha en una línea que mencione nacimiento.
        for (String linea : orden) {
            if (MARCA_NACIMIENTO.matcher(linea).find()) {
                LocalDate fecha = fechaDeLinea(linea);
                if (fecha != null) return fecha;
            }
        }
        // Else: alguna fecha con año plausible de nacimiento (preferir la última en el texto).
        LocalDate candidata = null;
        Matcher m = FECHA.matcher(texto);
        while (m.find()) {
            LocalDate fecha = aFecha(m.group(1), m.group(2), m.group(3));
            if (fecha != null && esAnioNacimientoPlausible(fecha.getYear())) {
                candidata = fecha;
            }
        }
        return candidata;
    }

    private LocalDate fechaDeLinea(String linea) {
        Matcher m = FECHA.matcher(linea);
        if (m.find()) {
            return aFecha(m.group(1), m.group(2), m.group(3));
        }
        return null;
    }

    private LocalDate aFecha(String d, String m, String y) {
        try {
            return LocalDate.parse(String.format("%s-%s-%s", d, m, y),
                    DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean esAnioNacimientoPlausible(int anio) {
        int actual = Year.now().getValue();
        return anio >= 1920 && anio <= actual - 6; // edad mínima de un menor = 6 (FR-ID-017)
    }

    // ------------------------------------------------------------------
    // Nombre y apellido
    // ------------------------------------------------------------------

    /** @return [apellido, nombre] o [null, null] si no se pudo extraer de forma confiable. */
    private String[] extraerNombreYApellido(List<String> orden, String texto) {
        // Formato libreta: rótulos "APELLIDO" y "NOMBRES" (+ valor en la misma línea o la siguiente).
        String[] rotulado = extraerPorRotulos(orden);
        if (rotulado[0] != null && rotulado[1] != null) {
            return rotulado;
        }

        // Formato tarjeta plástica: sin rótulos — apellido y nombres son las dos
        // líneas "nombre-sosas" inmediatamente arriba de la línea del Nº/DNI.
        return extraerPosicional(orden);
    }

    private String[] extraerPorRotulos(List<String> orden) {
        String apellido = null;
        String nombre = null;
        for (int i = 0; i < orden.size(); i++) {
            String linea = orden.get(i);
            if (apellido == null && esRotulo(linea, "APELLIDO")) {
                apellido = valorDeRotulo(orden, i, "APELLIDO");
            } else if (nombre == null && esRotulo(linea, "NOMBRES")) {
                nombre = valorDeRotulo(orden, i, "NOMBRES");
            }
        }
        return new String[]{apellido, nombre};
    }

    private boolean esRotulo(String linea, String rotulo) {
        // El rótulo al inicio, seguido de separador (':', ' - '). Evita confundir
        // "NOMBRES" con una línea de texto corrido que empiece igual.
        String may = linea.toUpperCase(Locale.ROOT);
        return may.startsWith(rotulo + ":")
                || may.startsWith(rotulo + " :")
                || may.startsWith(rotulo + " -")
                || may.startsWith(rotulo + " : ");
    }

    private String valorDeRotulo(List<String> orden, int i, String rotulo) {
        String linea = orden.get(i);
        int idx = linea.toUpperCase(Locale.ROOT).indexOf(':');
        if (idx >= 0 && idx + 1 < linea.length()) {
            String resto = linea.substring(idx + 1).replaceAll("^[\\s:\\-]+", "").trim();
            if (!resto.isEmpty()) return limpiarValor(resto);
        }
        // Valor en la línea siguiente.
        if (i + 1 < orden.size()) {
            String siguiente = limpiarValor(orden.get(i + 1));
            if (siguiente != null && !siguiente.isEmpty()
                    && !siguiente.chars().anyMatch(Character::isDigit)) {
                return siguiente;
            }
        }
        return null;
    }

    private String[] extraerPosicional(List<String> orden) {
        int indiceDni = -1;
        for (int i = 0; i < orden.size(); i++) {
            if (MARCA_DNI.matcher(orden.get(i)).find() || esSoloNumeroDni(orden.get(i))) {
                indiceDni = i;
                break;
            }
        }
        if (indiceDni < 0) return new String[]{null, null};

        // Tomar las dos líneas inmediatamente anteriores que parezcan nombres
        // (letras mayúsculas/acentos, sin números), en orden de lectura.
        List<String> sosas = new ArrayList<>();
        for (int i = indiceDni - 1; i >= 0 && sosas.size() < 2; i--) {
            String t = limpiarValor(orden.get(i));
            if (esSosa(t)) sosas.add(t);
        }
        if (sosas.size() < 2) return new String[]{null, null};
        // sosas quedó en orden inverso (de abajo hacia arriba); dar vuelta:
        // apellido (arriba), nombre (abajo).
        String apellido = sosas.get(1);
        String nombre = sosas.get(0);
        return new String[]{apellido, nombre};
    }

    private boolean esSoloNumeroDni(String linea) {
        // Solo dígitos/espacios/puntos (sin letras, sin barras de fecha),
        // y que el número resultante tenga 6-8 dígitos.
        return linea != null
                && linea.trim().matches("[\\d.\\s]+")
                && esDniValido(normalizarDni(linea));
    }

    private boolean esSosa(String s) {
        if (s == null || s.isEmpty()) return false;
        // "apellido, nombre" — letras, espacios, apóstrofes, guiones; sin dígitos.
        return s.matches("[A-ZÁÉÍÓÚÑa-záéíóúñÀ-ÿ'\\-\\s]+")
                && s.chars().anyMatch(Character::isLetter);
    }

    private String limpiarValor(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\s:.\\-]+$", "").trim();
    }
}
