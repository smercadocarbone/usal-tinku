package com.tinku.identidad.ocr;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Preprocesa la imagen del DNI ANTES de pasarla a Tesseract, para mejorar la
 * tasa de lectura en fotos de celular (ángulo, luz, reflejo) — mitigación del
 * riesgo de exactitud de ADR-M1-01.
 *
 * Pasos, en orden: escala de grises → ajuste de contraste → deskew.
 * Debe ser un componente AISLADO y testeable (mismo patrón que {@link DniParser}),
 * sin depender del binario nativo de Tesseract.
 *
 * Implementación en Java puro (java.awt/ImageIO) — sin dependencia nativa extra,
 * consistente con Artículo VII (la más simple que cumple el requisito).
 */
@Component
public class PreprocesadorImagen {

    /**
     * Pipeline completo: recibe los bytes de la imagen cruda (JPG/PNG), la
     * procesa y devuelve los bytes de la imagen lista para OCR (PNG en grises).
     * Si la imagen no se puede leer, devuelve null (el llamador lo trata como
     * documento ilegible).
     */
    public byte[] preprocesar(byte[] imagenCruda) {
        if (imagenCruda == null || imagenCruda.length == 0) {
            return null;
        }
        BufferedImage img = leer(imagenCruda);
        if (img == null) {
            return null;
        }
        BufferedImage gris = convierteAGrises(img);
        BufferedImage contraste = ajustaContraste(gris);
        BufferedImage enderezada = endereza(contraste);
        return aPng(enderezada);
    }

    BufferedImage leer(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }

    byte[] aPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    /** Escala de grises por luminancia estándar (BT.709). */
    public BufferedImage convierteAGrises(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int lum = (int) (0.2126 * r + 0.7152 * g + 0.0722 * b);
                out.setRGB(x, y, new Color(lum, lum, lum).getRGB());
            }
        }
        return out;
    }

    /**
     * Estirado de contraste basado en percentiles (robusto a outliers):
     * mapea el valor al percentil 1 → 0 y el 99 → 255. Mejora el contraste
     * de fotos subexpuestas o apagadas sin amplificar ruido de extremos.
     */
    public BufferedImage ajustaContraste(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int total = w * h;
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = (src.getRGB(x, y) >> 16) & 0xFF; // imagen ya en gris => r==g==b
                hist[g]++;
            }
        }
        int lo = percentil(hist, total, 1);
        int hi = percentil(hist, total, 99);
        if (hi <= lo) {
            hi = Math.min(255, lo + 1); // evita división por cero / rango vacío
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = (src.getRGB(x, y) >> 16) & 0xFF;
                int n;
                if (g <= lo) {
                    n = 0;
                } else if (g >= hi) {
                    n = 255;
                } else {
                    n = (int) Math.round(((g - lo) * 255.0) / (hi - lo));
                }
                out.setRGB(x, y, new Color(n, n, n).getRGB());
            }
        }
        return out;
    }

    private int percentil(int[] hist, int total, int pct) {
        int acum = 0;
        int objetivo = Math.max(1, (int) Math.round(pct / 100.0 * total));
        for (int v = 0; v < 256; v++) {
            acum += hist[v];
            if (acum >= objetivo) {
                return v;
            }
        }
        return 255;
    }

    /**
     * Deskew: estima el ángulo de inclinación mediante el análisis del perfil
     * de proyección horizontal (varianza de la suma de filas) y rota la imagen
     * para enderezarla.
     */
    public BufferedImage endereza(BufferedImage src) {
        double angulo = estimarAngulo(src);
        if (Math.abs(angulo) < 0.25) {
            return src; // ya está (casi) derecha
        }
        return rotar(src, -angulo);
    }

    /**
     * Estima el ángulo de inclinación en grados: devuelve el ángulo con signo
     * que hay que NEGAR para enderezar (ver {@link #endereza}). P.ej. un texto
     * inclinado +5° devuelve -5 (rotando +5° se endereza).
     */
    public double estimarAngulo(BufferedImage src) {
        double mejor = 0;
        double mejorVar = -1;
        for (double a = -10; a <= 10; a += 0.5) {
            // Muestrear la imagen rotada o, más barato, el perfil con la rotación.
            double var = varianzaProyeccion(rotar(src, a));
            if (var > mejorVar) {
                mejorVar = var;
                mejor = a;
            }
        }
        return mejor;
    }

    private double varianzaProyeccion(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double[] rowSum = new double[h];
        for (int y = 0; y < h; y++) {
            double s = 0;
            for (int x = 0; x < w; x++) {
                int g = (img.getRGB(x, y) >> 16) & 0xFF;
                // textos oscuros => valores bajos; sumamos el "oscuro" como señal.
                s += 255 - g;
            }
            rowSum[y] = s;
        }
        double media = 0;
        for (double v : rowSum) media += v;
        media /= h;
        double var = 0;
        for (double v : rowSum) var += (v - media) * (v - media);
        return var / h;
    }

    BufferedImage rotar(BufferedImage src, double grados) {
        double rad = Math.toRadians(grados);
        int w = src.getWidth(), h = src.getHeight();
        double sin = Math.abs(Math.sin(rad));
        double cos = Math.abs(Math.cos(rad));
        int nw = (int) Math.ceil(w * cos + h * sin);
        int nh = (int) Math.ceil(w * sin + h * cos);

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform at = new AffineTransform();
        at.translate((nw - w) / 2.0, (nh - h) / 2.0);
        at.rotate(rad, (double) w / 2, (double) h / 2);
        g.drawImage(src, at, null);
        g.dispose();
        return out;
    }
}
