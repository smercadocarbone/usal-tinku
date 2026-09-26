package com.tinku.identidad.ocr;

import com.tinku.identidad.service.OcrNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR REAL del DNI con Tesseract (ADR-M1-01), invocando el programa {@code tesseract} del
 * sistema (ADR-M1-08). Activo fuera de {@code dev}/{@code test}, donde sigue el stub.
 *
 * <p>Antes se usaba Tess4J (JNA): exigía Leptonica ≥ 1.83 y la imagen de producción (Ubuntu
 * 24.04) trae la 1.82, así que en producción NUNCA funcionó: toda foto terminaba en "lector no
 * disponible". El programa del paquete {@code tesseract-ocr} no tiene ese problema.</p>
 *
 * <p>Pipeline, pensado para fotos de celular:</p>
 * <ol>
 *   <li>{@link PreprocesadorImagen#cargar}: aplica la rotación EXIF, achica (o agranda) a un
 *       tamaño de trabajo y pasa a grises con más contraste.</li>
 *   <li>Detección de orientación de Tesseract ({@code --psm 0}): endereza una foto de costado o
 *       al revés.</li>
 *   <li>Lectura en varias variantes (enderezada; binarizada; otras rotaciones) hasta que
 *       {@link DniParser} obtiene los cuatro datos. Nunca campos parciales.</li>
 * </ol>
 *
 * <p>Si el programa no está o no responde, es falta del servicio, no del usuario:
 * {@link OcrNoDisponibleException} (503) y no consume intentos.</p>
 */
@Service
@Profile("!dev & !test")
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);
    private static final Pattern ROTACION_OSD = Pattern.compile("Rotate:\\s*(\\d+)");
    private static final long TIMEOUT_SEGUNDOS = 30;
    private static final String CARACTERES_MRZ = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<";
    /** Viene siempre con el paquete {@code tesseract-ocr} (dependencia de {@code tesseract-ocr-spa}). */
    private static final String IDIOMA_MRZ = "eng";

    private final DniParser dniParser;
    private final PreprocesadorImagen preprocesador;
    private final String comando;
    private final String tessdata;
    private final String idioma;

    public TesseractOcrService(DniParser dniParser,
                               PreprocesadorImagen preprocesador,
                               @Value("${tinku.ocr.comando:tesseract}") String comando,
                               @Value("${tinku.ocr.tessdata:}") String tessdata,
                               @Value("${tinku.ocr.idioma:spa}") String idioma) {
        this.dniParser = dniParser;
        this.preprocesador = preprocesador;
        this.comando = comando;
        this.tessdata = tessdata;
        this.idioma = idioma;
    }

    @Override
    public ResultadoOcr procesarDocumento(byte[] imagenDocumento, DatosDniDeclarados datosDeclarados) {
        // El proveedor real extrae todo de la imagen; `datosDeclarados` solo alimenta al stub.
        BufferedImage base = preprocesador.cargar(imagenDocumento);
        if (base == null) {
            log.info("OCR DNI: la imagen no se pudo leer (formato no soportado o archivo dañado)");
            return ResultadoOcr.ilegible();
        }
        int rotacion = rotacionDetectada(base);
        List<String> intentos = new ArrayList<>();
        for (Map.Entry<String, BufferedImage> variante : variantes(base, rotacion).entrySet()) {
            List<LineaTexto> lineas = variante.getKey().endsWith("-bloque")
                    ? leerLineas(variante.getValue(), "6", null, idioma)
                    : leerLineas(variante.getValue());
            // Dorso: si se ve la zona de lectura mecánica, se relee primero solo con sus caracteres
            // (A-Z, 0-9, <). La tipografía OCR-B confunde al modelo general (9 → 0, I → T), y una
            // lectura general "legible" pero con una letra cambiada no debe ganarle a la MRZ.
            if (tieneMrz(lineas)) {
                ResultadoOcr mrz = leerMrz(variante.getValue());
                if (mrz.documentoLegible()) {
                    log.info("OCR DNI: leído por la MRZ con la variante '{}'", variante.getKey());
                    return mrz;
                }
            }
            ResultadoOcr r = dniParser.parse(lineas);
            if (r.documentoLegible()) {
                log.info("OCR DNI: leído con la variante '{}' (orientación detectada {}°)", variante.getKey(), rotacion);
                return r;
            }
            intentos.add(variante.getKey() + ":" + dniParser.diagnostico(lineas));
        }
        // Sin datos personales en el log: solo qué faltó en cada variante.
        log.info("OCR DNI ilegible (orientación detectada {}°): {}", rotacion, intentos);
        return ResultadoOcr.ilegible();
    }

    /**
     * Relectura de la MRZ solo con sus caracteres. El modelo inglés confunde menos las letras
     * (I → T) y el configurado, a veces, menos los dígitos; los números traen dígito de control y
     * el renglón de nombres no. Se prueba: la lectura en inglés; la del idioma configurado con el
     * renglón de nombres tomado de la inglesa; y la del idioma configurado sola.
     */
    private ResultadoOcr leerMrz(BufferedImage img) {
        List<LineaTexto> ingles = leerLineas(img, "6", CARACTERES_MRZ, IDIOMA_MRZ);
        ResultadoOcr r = dniParser.parse(ingles);
        if (r.documentoLegible() || IDIOMA_MRZ.equals(idioma)) {
            return r;
        }
        List<LineaTexto> propia = leerLineas(img, "6", CARACTERES_MRZ, idioma);
        ResultadoOcr hibrida = dniParser.parse(conNombresDe(propia, ingles));
        return hibrida.documentoLegible() ? hibrida : dniParser.parse(propia);
    }

    /** Reemplaza el renglón de nombres de la MRZ ({@code APELLIDO<<NOMBRES<<<}) por el de otra lectura. */
    static List<LineaTexto> conNombresDe(List<LineaTexto> base, List<LineaTexto> otra) {
        LineaTexto nombres = otra.stream().filter(l -> esRenglonDeNombres(l.texto())).findFirst().orElse(null);
        if (nombres == null) {
            return base;
        }
        return base.stream().map(l -> esRenglonDeNombres(l.texto()) ? new LineaTexto(nombres.texto(), l.y()) : l).toList();
    }

    private static boolean esRenglonDeNombres(String texto) {
        String t = texto.replace(" ", "");
        return t.matches("[A-Z]+(<[A-Z]+)*<<[A-Z<]*") && !t.startsWith("ID");
    }

    /** Variantes a probar, en orden: la enderezada por OSD, binarizada, y las otras rotaciones. */
    private Map<String, BufferedImage> variantes(BufferedImage base, int rotacion) {
        Map<String, BufferedImage> v = new LinkedHashMap<>();
        BufferedImage derecha = preprocesador.rotar90(base, rotacion);
        v.put("derecha", derecha);
        v.put("derecha-bloque", derecha); // misma imagen, leída como un bloque de renglones (psm 6)
        v.put("binarizada", preprocesador.binarizar(derecha));
        for (int grados : new int[]{0, 90, 180, 270}) {
            if (grados != rotacion) {
                v.put("rotada" + grados, preprocesador.rotar90(base, grados));
            }
        }
        return v;
    }

    /** OSD de Tesseract: cuántos grados hay que rotar (0/90/180/270). Si no puede decidir, 0. */
    int rotacionDetectada(BufferedImage img) {
        try {
            String salida = ejecutar(img, "0", null, null, "osd");
            Matcher m = ROTACION_OSD.matcher(salida);
            return m.find() ? Integer.parseInt(m.group(1)) % 360 : 0;
        } catch (OcrNoDisponibleException e) {
            throw e;
        } catch (RuntimeException e) {
            return 0; // OSD falla con poco texto: se prueban igual todas las rotaciones
        }
    }

    /** Lee la imagen y agrupa las palabras por línea (bloque/párrafo/línea de Tesseract). */
    List<LineaTexto> leerLineas(BufferedImage img) {
        return leerLineas(img, "3", null, idioma);
    }

    private List<LineaTexto> leerLineas(BufferedImage img, String psm, String listaBlanca, String lengua) {
        String tsv;
        try {
            tsv = ejecutar(img, psm, "tsv", listaBlanca, lengua);
        } catch (OcrNoDisponibleException e) {
            throw e;
        } catch (RuntimeException e) {
            return List.of();
        }
        return lineasDeTsv(tsv);
    }

    static List<LineaTexto> lineasDeTsv(String tsv) {
        Map<String, StringBuilder> textos = new LinkedHashMap<>();
        Map<String, Double> tops = new LinkedHashMap<>();
        for (String fila : tsv.split("\n")) {
            String[] c = fila.split("\t", -1);
            // level page block par line word left top width height conf text
            if (c.length < 12 || !"5".equals(c[0])) {
                continue;
            }
            String texto = c[11].trim();
            if (texto.isEmpty()) {
                continue;
            }
            String clave = c[2] + "-" + c[3] + "-" + c[4];
            StringBuilder sb = textos.computeIfAbsent(clave, k -> new StringBuilder());
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(texto);
            double top = Double.parseDouble(c[7]);
            tops.merge(clave, top, Math::min);
        }
        List<LineaTexto> lineas = new ArrayList<>();
        textos.forEach((clave, sb) -> lineas.add(new LineaTexto(sb.toString(), tops.get(clave))));
        lineas.sort(java.util.Comparator.comparingDouble(LineaTexto::y));
        return lineas;
    }

    private static boolean tieneMrz(List<LineaTexto> lineas) {
        return lineas.stream().anyMatch(l -> l.texto().chars().filter(c -> c == '<').count() >= 5);
    }

    private String ejecutar(BufferedImage img, String psm, String formato, String listaBlanca, String lengua) {
        Path entrada = null;
        try {
            entrada = Files.createTempFile("tinku-dni-", ".png");
            byte[] png = preprocesador.aPng(img);
            if (png == null) {
                throw new IllegalStateException("No se pudo preparar la imagen");
            }
            Files.write(entrada, png);
            List<String> cmd = new ArrayList<>(List.of(comando, entrada.toString(), "stdout",
                    "-l", lengua, "--psm", psm, "-c", "user_defined_dpi=300"));
            if (tessdata != null && !tessdata.isBlank()) {
                cmd.add(1, "--tessdata-dir");
                cmd.add(2, tessdata);
            }
            if (listaBlanca != null) {
                cmd.addAll(List.of("-c", "tessedit_char_whitelist=" + listaBlanca));
            }
            if (formato != null) {
                cmd.add(formato);
            }
            Process p = new ProcessBuilder(cmd).redirectErrorStream("0".equals(psm)).start();
            byte[] salida = p.getInputStream().readAllBytes();
            if (!p.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("Tesseract no respondió a tiempo");
            }
            return new String(salida, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // El programa no está instalado o no se puede ejecutar: falta del servicio.
            log.warn("El lector de documentos no está disponible (¿tesseract instalado?): {}", e.getMessage());
            throw new OcrNoDisponibleException();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OcrNoDisponibleException();
        } finally {
            if (entrada != null) {
                try {
                    Files.deleteIfExists(entrada);
                } catch (IOException ignorada) {
                    // archivo temporal: lo limpia el sistema
                }
            }
        }
    }
}
