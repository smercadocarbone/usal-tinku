package com.tinku.resumen.anonimizacion;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonimizacion del transcript ANTES de que cualquier cosa salga hacia el
 * proveedor de LLM (FR-SUM-005, Plan M6 §2 paso 4 — paso no-opcional y anterior
 * a toda llamada de red saliente). Reemplaza datos personales por marcadores
 * genericos ({@code [nombre]}, {@code [contacto]}-family).
 *
 * <p>Estrategia (Artículo VII, 1 dev, USD 0-100/mes): regex primero. El NER del
 * Plan (ADR-M6-01 pendiente) queda como un PUENTE minimo que no trae ningun
 * modelo: (a) patrones contextuales de presentacion ("me llamo X", "soy X") y
 * (b) un diccionario reducido de nombres propios hispanos frecuentes. Cuando el
 * ADR-M6-01 se cierre, se reemplaza {@link #NOMBRES} y su patron por el modelo
 * elegido sin tocar el contrato de {@link #anonimizar(String)} ni el pipeline.
 *
 * <p>Compromiso deliberado (fail-safe): ante la duda se enmascara de mas. Un
 * falso positivo enmascara una palabra del transcript; un falso negativo filtra
 * un dato personal de un menor — la regla del Artículo II manda.
 */
@Component
public class AnonimizadorTranscript {

    /** NER ligero sin modelo (puente, ADR-M6-01): nombres propios hispanos frecuentes. */
    private static final Pattern NOMBRE_DIC = Pattern.compile(
            "\\b(?:Juan|María|Maria|Ana|Carlos|Pablo|Martín|Martin|Lucía|Lucia|Julieta|Rocío|Rocio"
            + "|Agustín|Agustin|Sofía|Sofia|Valentina|Santiago|Mateo|Bautista|Joaquín|Joaquin|Pedro"
            + "|Miguel|José|Jose|Luis|Diego|Nicolás|Nicolas|Facundo|Gonzalo|Federico|Matías|Matias"
            + "|Sebastián|Sebastian|Franco|Iván|Ivan|Ramiro|Manuel|Javier|Esteban|Damián|Damian"
            + "|Leonardo|Cristian|Jorge|Alejandro|Andrés|Andres|Marcos|Marcelo|Alberto|Oscar"
            + "|Ricardo|Eduardo|Gabriel|Daniel|Roberto|Fernando|Mario|Gustavo|Raúl|Raul|Gerardo"
            + "|Rubén|Ruben|Norberto|Alejandra|Cecilia|Gabriela|Graciela|Verónica|Veronica|Silvia"
            + "|Patricia|Carina|Carolina|Marcela|Romina|Karina|Viviana|Adriana|Valeria|Mercedes"
            + "|Florencia|Camila|Milagros|Constanza|Agustina|Jazmín|Jazmin|Micaela|Candela|Brisa"
            + "|Selene|Morena|Delfina|Josefina|Magdalena|Paloma|Martina|Catalina|Olivia|Emma|Tobías"
            + "|Tobias|Thiago|Benjamín|Benjamín|Felipe|Tomas|Tomás|Bruno|Iker|Simón|Simon)\\b",
            Pattern.CASE_INSENSITIVE);

    /** NER ligero contextual: presentaciones ("me llamo X", "soy X", ...). El flag
     *  inline {@code (?i:...)} escribe el introductor indiferente a mayusculas
     *  ("Me llamo", "SOY") sin relajar la captura, que exige inicial mayuscula. */
    private static final Pattern NOMBRE_PRESENTACION = Pattern.compile(
            "(?:(?i:me llamo|mi nombre es|soy el profe|soy la profe|soy|habla|conmigo|es el profe"
                    + "|es la profe)\\s+)"
                    + "([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){0,3})");

    private static final Pattern EMAIL = Pattern.compile(
            "(?<!@)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    private static final Pattern URL = Pattern.compile(
            "\\b(?:https?://|www\\.)[^\\s<>]+", Pattern.CASE_INSENSITIVE);

    /** Datos de pago numericos: CBU (22 digitos), CUIT/CUIL, tarjeta (16). */
    private static final Pattern[] PAGO_NUMERICO = {
            Pattern.compile("\\b\\d{22}\\b"),
            Pattern.compile("\\b(?:20|23|24|27|30|33|34)\\s?-\\s?\\d{8}\\s?-\\s?\\d\\b"),
            Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b")
    };

    /** Alias de MercadoPago (3 segmentos en minusculas). Corre DESPUES del email
     *  para no tragarse "nombre@gmail.com" — el email ya fue a {@code [email]}. */
    private static final Pattern ALIAS_PAGO = Pattern.compile(
            "\\b[a-z][a-z0-9]{3,}\\.[a-z0-9]+\\.[a-z0-9]+\\b");

    /** Telefonos argentinos: moviles (con/sin 54, 0 o 9), fijos con area y
     *  locales de 8 digitos. Cada patron traga el grupo final opcional para no
     *  dejar colgados "5555-1234". Corren DESPUES de los pagos (PAGO) para que
     *  CBU/tarjeta no terminen enmascarados como telefono. */
    private static final Pattern[] NO_PAGO_TELEFONO = {
            Pattern.compile("(?:\\+54[\\s.-]?)?(?:0[\\s.-]?)?(?:9[\\s.-]?)?"
                    + "(?:11|15)[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?:[\\s.-]?\\d{4})?"),
            Pattern.compile("\\b0\\d{1,3}[\\s.-]?\\d{3,4}[\\s.-]?\\d{4}(?:[\\s.-]?\\d{4})?"),
            Pattern.compile("\\b\\d{3,4}[\\s.-]\\d{4}\\b"),
            Pattern.compile("\\b\\d{10}\\b")
    };

    // FIXME AUD-036/ADR-M6-02 (auditoría 2026-09-21): el "porqué" de abajo es falso —
    // ninguno de los 4 patrones de NO_PAGO_TELEFONO matchea el formato xx.xxx.xxx que este
    // patrón documenta. El orden DNI→teléfono es inofensivo pero indiferente; el motivo real
    // es la especificidad del ancla obligatoria `dni|documento`. Ver ADR-M6-02, punto 5.
    /** DNI/documento seguido del numero con formato xx.xxx.xxx (tolera palabras
     *  intermedias, p.ej. "DNI es 30.123.456"). Corre ANTES de los telefonos para
     *  que el numero no termine enmascarado como telefono. */
    private static final Pattern DNI = Pattern.compile(
            "\\b(?:dni|documento)\\b[^\\d]{0,15}?\\d{1,3}\\.\\d{3}\\.\\d{3}\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String NOMBRE = "[nombre]";
    private static final String TELEFONO = "[telefono]";
    private static final String EMAIL_MARK = "[email]";
    private static final String URL_MARK = "[url]";
    private static final String PAGO_MARK = "[pago]";
    private static final String DNI_MARK = "[dni]";

    /** Devuelve el texto con todo dato personal reemplazado por marcadores.
     *  Nunca devuelve el transcript crudo; si el input es null, {@code null}. */
    public String anonimizar(String texto) {
        if (texto == null) {
            return null;
        }
        String resultado = texto;
        for (Pattern p : PAGO_NUMERICO) {
            resultado = reemplazar(resultado, p, PAGO_MARK);
        }
        resultado = reemplazar(resultado, EMAIL, EMAIL_MARK);
        resultado = reemplazar(resultado, URL, URL_MARK);
        resultado = reemplazar(resultado, ALIAS_PAGO, PAGO_MARK);
        resultado = reemplazar(resultado, DNI, DNI_MARK);
        for (Pattern p : NO_PAGO_TELEFONO) {
            resultado = reemplazar(resultado, p, TELEFONO);
        }
        resultado = reemplazarObjetivo(resultado, NOMBRE_PRESENTACION, NOMBRE);
        resultado = reemplazar(resultado, NOMBRE_DIC, NOMBRE);
        return resultado;
    }

    private static String reemplazar(String texto, Pattern patron, String marcador) {
        Matcher m = patron.matcher(texto);
        return m.replaceAll(Matcher.quoteReplacement(marcador));
    }

    /** Reemplaza solo el grupo capturado (la frase contextual se conserva). */
    private static String reemplazarObjetivo(String texto, Pattern patron, String marcador) {
        Matcher m = patron.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    texto.substring(m.start(), m.start(1)) + marcador
                            + texto.substring(m.end(1), m.end())));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}