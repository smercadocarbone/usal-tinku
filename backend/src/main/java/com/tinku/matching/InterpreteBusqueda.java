package com.tinku.matching;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lo que se entiende de una búsqueda escrita sin depender del modelo de embeddings
 * (FR-MATCH-011): el nivel que la persona dijo ("divisiones en primario") y las raíces de las
 * palabras para reconocer el tema en el catálogo ("divisiones" → "divisi" → "División").
 */
final class InterpreteBusqueda {

    private static final Pattern PRIMARIO = Pattern.compile("\\bprimari[oa]s?\\b");
    private static final Pattern SECUNDARIO = Pattern.compile("\\bsecundari[oa]s?\\b|\\bsecu\\b|\\bcolegio\\b");
    private static final Pattern UNIVERSITARIO = Pattern.compile("\\buniversi\\w*|\\bfacultad\\b|\\bfacu\\b|\\bcbc\\b");
    private static final Pattern PALABRA = Pattern.compile("[a-zñ]{4,}");

    /** Palabras que no dicen de qué tema se trata. */
    private static final Set<String> VACIAS = Set.of(
            "para", "como", "necesito", "quiero", "busco", "clases", "clase", "ayuda", "aprender",
            "entender", "estudiar", "tema", "temas", "ejercicios", "ejercicio", "problemas", "problema",
            "examen", "prueba", "parcial", "final", "tarea", "tareas", "profe", "profesor", "profesora",
            "tutor", "tutora", "sobre", "hacer", "resolver", "nivel", "grado", "anio", "curso",
            "primario", "primaria", "secundario", "secundaria", "universidad", "universitario",
            "facultad", "colegio", "escuela", "materia", "algo", "cosas", "mucho", "poco", "bien");

    private InterpreteBusqueda() {
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    /** "primario" | "secundario" | "universitario" si el texto lo dice explícitamente. */
    static Optional<String> nivel(String texto) {
        String t = normalizar(texto);
        if (PRIMARIO.matcher(t).find()) return Optional.of("primario");
        if (SECUNDARIO.matcher(t).find()) return Optional.of("secundario");
        if (UNIVERSITARIO.matcher(t).find()) return Optional.of("universitario");
        return Optional.empty();
    }

    /**
     * Raíces de las palabras con contenido: las de 6+ letras se cortan a 6 ("divisiones" y
     * "división" → "divisi"; "multiplicar" y "multiplicación" → "multip"), las de 4-5 van enteras.
     */
    static List<String> raices(String texto) {
        List<String> raices = new ArrayList<>();
        Matcher m = PALABRA.matcher(normalizar(texto));
        while (m.find()) {
            String palabra = m.group();
            if (VACIAS.contains(palabra)) {
                continue;
            }
            String raiz = palabra.length() >= 6 ? palabra.substring(0, 6) : palabra;
            if (!raices.contains(raiz)) {
                raices.add(raiz);
            }
        }
        return raices;
    }
}
