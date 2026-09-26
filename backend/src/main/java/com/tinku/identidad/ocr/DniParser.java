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
 *   - Tarjeta actual (2012+, la que tiene casi todo el mundo): rótulos bilingües
 *     sin dos puntos ("Apellido / Surname", valor en la línea siguiente) y fechas
 *     con el mes en letras ("15 MAY/ MAY 1990"). Hasta 2026-09-25 el parser no la
 *     entendía: todo DNI real salía "ilegible".
 *   - Dorso de la tarjeta: zona de lectura mecánica (MRZ, 3 líneas con {@code <})
 *     con dígitos de control. Si está y valida, manda sobre el frente.
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
    // El número del DNI con puntos de miles ("34.567.890", "7.123.456"): ninguna fecha de la tarjeta
    // tiene ese formato, así que es la señal más confiable aunque el rótulo se lea mal.
    // El OCR a veces lee un punto como coma o le agrega un espacio ("17, 654.321", "41.987. 003").
    private static final Pattern DNI_CON_PUNTOS = Pattern.compile("(?<![\\d.,])(\\d{1,2}[.,]\\s?\\d{3}[.,]\\s?\\d{3})(?![\\d.,])");
    // Un número de DNI dentro de una línea, con o sin puntos (nunca dígitos sueltos de una fecha).
    private static final Pattern DNI_EN_LINEA = Pattern.compile("(?<![\\d.])(\\d{1,2}\\.\\d{3}\\.\\d{3}|\\d{6,8})(?![\\d.])");

    // Fecha: dd/mm/aaaa o dd-mm-aaaa.
    private static final Pattern FECHA = Pattern.compile("(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})");

    private static final Pattern MARCA_DNI =
            Pattern.compile("(?:N\\s*[º°]|N\\s*\\.?\\s*\\d|N[uú]?mero|N\\s+DE\\b|\\bDNI\\b|D\\.N\\.I\\.|\\bDoc(?:umento)?\\b)", Pattern.CASE_INSENSITIVE);
    // Fecha con el mes en letras: "15 MAY/ MAY 1990", "03 ENE/JAN 1985", "3 SET 1990".
    private static final Pattern FECHA_MES_LETRAS = Pattern.compile(
            "(\\d{1,2})\\s*([A-Za-zÁÉÍÓÚ]{3})\\.?(?:\\s*/\\s*[A-Za-z]{3}\\.?)?\\s*(\\d{4})");
    private static final java.util.Map<String, Integer> MESES = java.util.Map.ofEntries(
            java.util.Map.entry("ENE", 1), java.util.Map.entry("JAN", 1), java.util.Map.entry("FEB", 2),
            java.util.Map.entry("MAR", 3), java.util.Map.entry("ABR", 4), java.util.Map.entry("APR", 4),
            java.util.Map.entry("MAY", 5), java.util.Map.entry("JUN", 6), java.util.Map.entry("JUL", 7),
            java.util.Map.entry("AGO", 8), java.util.Map.entry("AUG", 8), java.util.Map.entry("SEP", 9),
            java.util.Map.entry("SET", 9), java.util.Map.entry("OCT", 10), java.util.Map.entry("NOV", 11),
            java.util.Map.entry("DIC", 12), java.util.Map.entry("DEC", 12));
    private static final Pattern MARCA_DOCUMENTO = Pattern.compile("\\bDOCUMENT", Pattern.CASE_INSENSITIVE);
    private static final Pattern MARCA_NACIMIENTO =
            Pattern.compile("nac(imiento|id[oa])|fecha\\s+de\\s+nac|nac\\.|date\\s+of\\s+birth|of\\s+birth", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROTULO_VALOR =
            Pattern.compile("^([A-ZÁÉÍÓÚÑ ]+?)\\s*[:\\-]\\s*(.*)$");

    /**
     * Qué se pudo extraer y qué no, SIN datos personales (para el log cuando una foto no se lee).
     */
    public String diagnostico(List<LineaTexto> lineas) {
        if (lineas == null || lineas.isEmpty()) {
            return "sin texto";
        }
        List<String> orden = lineas.stream().sorted(Comparator.comparingDouble(LineaTexto::y))
                .map(LineaTexto::texto).map(String::trim).filter(x -> !x.isEmpty()).toList();
        String texto = String.join("\n", orden);
        String[] nombreYApellido = extraerNombreYApellido(orden, texto);
        return "lineas=" + orden.size()
                + " dni=" + (extraerDni(orden, texto) != null ? "si" : "no")
                + " fecha=" + (extraerFechaNacimiento(orden, texto) != null ? "si" : "no")
                + " apellido=" + (nombreYApellido[0] != null ? "si" : "no")
                + " nombre=" + (nombreYApellido[1] != null ? "si" : "no")
                + " mrz=" + orden.stream().filter(l -> l.chars().filter(c -> c == '<').count() >= 5).count();
    }

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

        ResultadoOcr mrz = MrzDni.parse(orden);
        if (mrz != null) {
            return mrz;
        }

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
        return new ResultadoOcr(true, dni, nombreYApellido[1].toUpperCase(Locale.ROOT),
                nombreYApellido[0].toUpperCase(Locale.ROOT), fechaNacimiento);
    }

    // ------------------------------------------------------------------
    // DNI
    // ------------------------------------------------------------------

    private String extraerDni(List<String> orden, String texto) {
        // 00) Número con puntos de miles: el formato propio del DNI (tarjeta y libreta).
        Matcher conPuntos = DNI_CON_PUNTOS.matcher(texto);
        if (conPuntos.find()) {
            return normalizarDni(conPuntos.group(1));
        }
        // 0) Tarjeta actual: "Documento / Document" y el número debajo. Va primero porque
        //    el "Trámite Nº" de más arriba también matchea la marca genérica "Nº".
        for (int i = 0; i < orden.size(); i++) {
            if (MARCA_DOCUMENTO.matcher(orden.get(i)).find()) {
                String normalizado = normalizarDni(colorearDesdeMarca(orden, i));
                if (esDniValido(normalizado)) {
                    return normalizado;
                }
            }
        }
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
            // Sobre la línea tal cual: normalizarla entera juntaba los dígitos de una fecha
            // ("10 ENE/ JAN 2018" → "102018") y los tomaba como DNI.
            Matcher m = DNI_EN_LINEA.matcher(orden.get(k));
            if (m.find()) {
                return normalizarDni(m.group(1));
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
        // Preferir una fecha en la línea que menciona nacimiento, o en la siguiente
        // (tarjeta actual: rótulo arriba, valor abajo). Sin esto, la tarjeta tiene
        // también emisión y vencimiento y se podía tomar la fecha equivocada.
        for (int i = 0; i < orden.size(); i++) {
            if (MARCA_NACIMIENTO.matcher(orden.get(i)).find()) {
                LocalDate fecha = fechaDeLinea(orden.get(i));
                if (fecha == null && i + 1 < orden.size()) {
                    fecha = fechaDeLinea(orden.get(i + 1));
                }
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
        Matcher l = FECHA_MES_LETRAS.matcher(linea);
        if (l.find()) {
            String mes = java.text.Normalizer.normalize(l.group(2), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
            Integer numero = MESES.get(mes);
            if (numero != null) {
                return aFecha(l.group(1), numero.toString(), l.group(3));
            }
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
                || may.startsWith(rotulo + " : ")
                || esRotuloBilingue(may, rotulo);
    }

    /**
     * Tarjeta actual: "APELLIDO / SURNAME", "NOMBRE / NAME" solos en su línea (valor debajo).
     * Tolera lo que el OCR suele leer mal en el rótulo en inglés ("Surmame", "Narne") y una
     * letra de más o de menos en el español: la barra y la línea corta son lo que lo distingue
     * de un valor.
     */
    private boolean esRotuloBilingue(String may, String rotulo) {
        String t = may.trim();
        String base = rotulo.equals("NOMBRES") ? "NOMBRES?" : "APELLIDOS?";
        String ingles = rotulo.equals("NOMBRES") ? "NAMES?" : "SURNAMES?";
        if (t.matches(base + "\\s*(/\\s*" + ingles + ")?\\s*:?")) {
            return true;
        }
        // El OCR lee mal la barra ("f", "7", "U", "l") y el rótulo en inglés ("Surmame", "Sumarna",
        // "Narne"): alcanza con el rótulo en español, un separador corto y una palabra en inglés
        // que empiece como corresponde (S… para Surname, N… para Name).
        String espanol = rotulo.equals("NOMBRES") ? "N[O0]M?B?R[E3]S?" : "AP[E3]L+[I1L]D[O0]S?";
        String ingles2 = rotulo.equals("NOMBRES") ? "N[A-Z]{2,5}" : "S[A-Z]{3,8}";
        return t.length() <= 30
                && (t.matches(espanol + "\\s*/.{0,14}") || t.matches(espanol + "\\s*\\S{0,3}\\s*" + ingles2));
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
