package com.tinku.identidad.ocr;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Implementación REAL de {@link OcrService} usando Tesseract vía Tess4J,
 * in-process (ADR-M1-01). Activa en el perfil de producción — NUNCA en
 * `dev`/`test`, donde sigue activo {@link StubOcrService}.
 *
 * Pipeline: imagen cruda → {@link PreprocesadorImagen} (grises + contraste +
 * deskew) → Tesseract (idioma español) → agrupación en líneas (con coordenada
 * Y) → {@link DniParser} (texto → nombre/apellido/DNI/fecha).
 *
 * Requiere el binario nativo de Tesseract instalado en el entorno (ver
 * backend/README.md) y el data de idioma español (`spa.traineddata`) accesible
 * vía `TESSDATA_PREFIX` o `tinku.ocr.tessdata`.
 */
@Service
@Profile("!dev & !test")
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    private final DniParser dniParser;
    private final PreprocesadorImagen preprocesador;
    private final String tessdata;
    private final String idioma;

    public TesseractOcrService(DniParser dniParser,
                               PreprocesadorImagen preprocesador,
                               @Value("${tinku.ocr.tessdata:}") String tessdata,
                               @Value("${tinku.ocr.idioma:spa}") String idioma) {
        this.dniParser = dniParser;
        this.preprocesador = preprocesador;
        this.tessdata = tessdata;
        this.idioma = idioma;
    }

    @Override
    public ResultadoOcr procesarDocumento(byte[] imagenDocumento) {
        // Preprocesamiento ANTES de Tesseract (ADR-M1-01: mitigación de exactitud).
        byte[] lista = preprocesador.preprocesar(imagenDocumento);
        if (lista == null) {
            return ResultadoOcr.ilegible();
        }

        BufferedImage img = leer(lista);
        if (img == null) {
            return ResultadoOcr.ilegible();
        }

        try {
            ITesseract tesseract = new Tesseract();
            if (tessdata != null && !tessdata.isBlank()) {
                tesseract.setDatapath(tessdata);
            }
            tesseract.setLanguage(idioma);

            List<Word> palabras = tesseract.getWords(img, ITessAPI.TessPageSegMode.PSM_AUTO);
            List<LineaTexto> lineas = agruparEnLineas(palabras);
            if (lineas.isEmpty()) {
                return ResultadoOcr.ilegible();
            }
            return dniParser.parse(lineas);
        } catch (Exception e) {
            // Falla de lectura de OCR → se trata como documento ilegible
            // (reintentos + backoff FR-ID-011), no como rechazo de identidad.
            log.warn("Falló el OCR de documento: {}", e.getMessage());
            return ResultadoOcr.ilegible();
        }
    }

    private BufferedImage leer(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Agrupa las palabras detectadas por Tesseract en líneas de texto, usando
     * la superposición de su coordenada vertical. Devuelve las líneas en orden
     * de lectura (y, luego x).
     */
    List<LineaTexto> agruparEnLineas(List<Word> palabras) {
        List<Word> ordenadas = new ArrayList<>(palabras);
        ordenadas.sort(Comparator.comparingDouble((Word w) -> w.getBoundingBox().getY())
                .thenComparingDouble(w -> w.getBoundingBox().getX()));

        List<FilaParcial> filas = new ArrayList<>();
        for (Word w : ordenadas) {
            double yTop = w.getBoundingBox().getY();
            double yCenter = yTop + w.getBoundingBox().getHeight() / 2.0;
            FilaParcial fila = null;
            for (FilaParcial fp : filas) {
                // Misma línea si el centro vertical cae dentro del rango de la fila.
                if (yCenter >= fp.yMin - fp.tolerancia && yCenter <= fp.yMax + fp.tolerancia) {
                    fila = fp;
                    break;
                }
            }
            if (fila == null) {
                fila = new FilaParcial();
                filas.add(fila);
            }
            fila.yMin = Math.min(fila.yMin, yTop);
            fila.yMax = Math.max(fila.yMax, yTop + w.getBoundingBox().getHeight());
            fila.palabras.add(w);
        }

        List<LineaTexto> resultado = new ArrayList<>();
        for (FilaParcial f : filas) {
            f.palabras.sort(Comparator.comparingDouble(w -> w.getBoundingBox().getX()));
            StringBuilder sb = new StringBuilder();
            for (Word w : f.palabras) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w.getText());
            }
            resultado.add(new LineaTexto(sb.toString().trim(), f.yMin));
        }
        resultado.sort(Comparator.comparingDouble(LineaTexto::y));
        return resultado;
    }

    /** Fila parcial mientras se agrupan palabras; no es un bean ni un DTO persistente. */
    private static class FilaParcial {
        double yMin = Double.MAX_VALUE;
        double yMax = -1;
        double tolerancia = 10;
        List<Word> palabras = new ArrayList<>();
    }
}
