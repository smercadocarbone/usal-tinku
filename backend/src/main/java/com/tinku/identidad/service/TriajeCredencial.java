package com.tinku.identidad.service;

import com.tinku.identidad.ocr.TextoDocumento;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Triaje automático de una credencial antes de la cola del Admin (ADR-M1-06, decisión 3). Reglas
 * puras, sin estado:
 * <ul>
 *   <li>rechazo inmediato de lo que claramente no sirve: enlace fuera de los dominios oficiales,
 *       imagen sin texto legible, documento que no es un título ni un certificado (p. ej. el DNI);</li>
 *   <li>comparación del nombre leído con el del DNI verificado: solo <b>marca</b> para el Admin;</li>
 *   <li>prellenado de institución, título y año, que el Tutor confirma.</li>
 * </ul>
 * La máquina decide qué <b>no</b> es un título; qué sí es real lo decide el Admin contra el registro.
 */
public final class TriajeCredencial {

    private TriajeCredencial() {
    }

    static final String MOTIVO_ENLACE = "El enlace de verificación tiene que empezar con https:// y ser de un "
            + "sitio oficial: registrograduados.siu.edu.ar, argentina.gob.ar (Mi Argentina) o el de una "
            + "universidad (.edu.ar).";
    static final String MOTIVO_SIN_TEXTO = "No pudimos leer texto en la imagen. Subí una foto más nítida y "
            + "derecha, o el PDF original.";
    static final String MOTIVO_DNI = "El archivo parece un DNI, no un título. Subí el título o el certificado.";
    static final String MOTIVO_NO_ES_TITULO = "El archivo no parece un título ni un certificado. Subí el "
            + "documento que respalda tu formación.";

    /** Palabras que aparecen en un título, certificado o matrícula (sin acentos, en mayúsculas). */
    private static final List<String> PALABRAS_TITULO = List.of(
            "TITULO", "DIPLOMA", "CERTIFIC", "CONSTANCIA", "EGRESAD", "GRADUAD", "MATRICULA",
            "ALUMNO REGULAR", "ANALITICO", "PROFESOR", "LICENCIAD", "INGENIER", "BACHILLER", "TECNIC",
            "DOCTOR", "ABOGAD", "CONTADOR", "MEDIC", "ARQUITECT", "TRADUCTOR", "MAESTR", "UNIVERSIDAD",
            "INSTITUTO", "FACULTAD", "ESCUELA", "COLEGIO", "CONSEJO PROFESIONAL", "GUARANI");

    private static final List<String> PALABRAS_DNI = List.of(
            "DOCUMENTO NACIONAL DE IDENTIDAD", "REGISTRO NACIONAL DE LAS PERSONAS");
    private static final Pattern MRZ_DNI = Pattern.compile("IDARG\\d");

    private static final List<String> PALABRAS_INSTITUCION = List.of(
            "UNIVERSIDAD", "INSTITUTO", "COLEGIO", "ESCUELA", "CONSEJO", "I.S.F.D", "ISFD");
    private static final List<String> PALABRAS_GRADO = List.of(
            "PROFESOR", "LICENCIAD", "INGENIER", "BACHILLER", "TECNIC", "DOCTOR", "ABOGAD", "CONTADOR",
            "MEDIC", "ARQUITECT", "TRADUCTOR", "MAESTR", "ANALISTA", "PERITO");
    private static final Pattern TITULO_DE =
            Pattern.compile("t[ií]tulo\\s+de\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ANIO = Pattern.compile("(?<!\\d)(19[5-9]\\d|20\\d\\d)(?!\\d)");

    // ------------------------------------------------------------------ enlace

    /**
     * SPIKE §4.2: el enlace tiene que ser https y de un dominio permitido. El sistema no lo abre
     * (no se scrapea nada); se lo muestra al Admin. Vacío = válido.
     */
    public static Optional<String> motivoEnlaceInvalido(String enlace) {
        URI uri;
        try {
            uri = new URI(enlace.strip());
        } catch (URISyntaxException | NullPointerException e) {
            return Optional.of(MOTIVO_ENLACE);
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                || uri.getRawUserInfo() != null || uri.getPort() != -1) {
            return Optional.of(MOTIVO_ENLACE);
        }
        host = host.toLowerCase(Locale.ROOT);
        boolean permitido = host.equals("argentina.gob.ar") || host.endsWith(".argentina.gob.ar")
                || (host.endsWith(".edu.ar") && host.length() > ".edu.ar".length());
        return permitido ? Optional.empty() : Optional.of(MOTIVO_ENLACE);
    }

    // ------------------------------------------------------------------ texto del documento

    /** Rechazo inmediato por el contenido. Un documento que no se pudo procesar no se rechaza. */
    public static Optional<String> motivoRechazoPorTexto(TextoDocumento texto) {
        return switch (texto.lectura()) {
            case SIN_TEXTO -> Optional.of(MOTIVO_SIN_TEXTO);
            case NO_PROCESABLE -> Optional.empty();
            case LEIDO -> {
                String t = normalizar(texto.texto());
                if (PALABRAS_TITULO.stream().anyMatch(t::contains)) {
                    yield Optional.empty();
                }
                boolean dni = PALABRAS_DNI.stream().anyMatch(t::contains) || MRZ_DNI.matcher(t).find();
                yield Optional.of(dni ? MOTIVO_DNI : MOTIVO_NO_ES_TITULO);
            }
        };
    }

    /**
     * ¿El nombre del DNI verificado aparece en el documento? Cada palabra del apellido y el primer
     * nombre tienen que estar, con la misma tolerancia que el registro (un error cada 6 letras, ver
     * {@code UsuarioService#coincideAproximado}). {@code null} = no se pudo leer: sin comparar.
     */
    public static Boolean nombreCoincide(TextoDocumento texto, String nombre, String apellido) {
        if (texto.lectura() != TextoDocumento.Lectura.LEIDO || nombre == null || apellido == null) {
            return null;
        }
        List<String> palabras = Arrays.asList(normalizar(texto.texto()).split("[^A-Z0-9]+"));
        List<String> buscadas = new ArrayList<>();
        String[] nombres = normalizar(nombre).split("[^A-Z]+");
        if (nombres.length > 0 && !nombres[0].isEmpty()) {
            buscadas.add(nombres[0]);
        }
        List<String> apellidos = Arrays.stream(normalizar(apellido).split("[^A-Z]+"))
                .filter(p -> !p.isEmpty()).toList();
        List<String> largos = apellidos.stream().filter(p -> p.length() >= 3).toList();
        buscadas.addAll(largos.isEmpty() ? apellidos : largos);
        if (buscadas.isEmpty()) {
            return null;
        }
        return buscadas.stream().allMatch(b -> palabras.stream().anyMatch(p -> parecidos(b, p)));
    }

    /** Lo que el OCR propone; el Tutor lo confirma o corrige. Campos null = no se encontró. */
    public record Prellenado(String institucion, String denominacion, Integer anioEgreso) {
    }

    public static Prellenado prellenar(TextoDocumento texto) {
        if (texto.lectura() != TextoDocumento.Lectura.LEIDO) {
            return new Prellenado(null, null, null);
        }
        List<String> lineas = texto.texto().lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
        return new Prellenado(institucion(lineas), denominacion(lineas), anio(lineas));
    }

    private static String institucion(List<String> lineas) {
        return lineas.stream()
                .filter(l -> PALABRAS_INSTITUCION.stream().anyMatch(normalizar(l)::contains))
                .findFirst().map(l -> recortar(l, 200)).orElse(null);
    }

    private static String denominacion(List<String> lineas) {
        for (int i = 0; i < lineas.size(); i++) {
            Matcher m = TITULO_DE.matcher(lineas.get(i));
            if (m.find()) {
                String resto = m.group(1).strip();
                if (!resto.isEmpty()) {
                    return recortar(resto, 200);
                }
                if (i + 1 < lineas.size()) {
                    return recortar(lineas.get(i + 1), 200);
                }
            }
        }
        return lineas.stream()
                .filter(l -> PALABRAS_GRADO.stream().anyMatch(normalizar(l)::contains))
                .findFirst().map(l -> recortar(l, 200)).orElse(null);
    }

    private static Integer anio(List<String> lineas) {
        int actual = Year.now().getValue();
        for (String l : lineas) {
            Matcher m = ANIO.matcher(l);
            while (m.find()) {
                int a = Integer.parseInt(m.group(1));
                if (a <= actual) {
                    return a;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ utilidades

    static String normalizar(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
    }

    private static boolean parecidos(String a, String b) {
        int tolerancia = Math.max(1, Math.max(a.length(), b.length()) / 6);
        return Math.abs(a.length() - b.length()) <= tolerancia && distancia(a, b) <= tolerancia;
    }

    /** Levenshtein (palabras cortas). */
    private static int distancia(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previa[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, previa[j] + 1), previa[j - 1] + costo);
            }
            int[] t = previa; previa = actual; actual = t;
        }
        return previa[b.length()];
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
