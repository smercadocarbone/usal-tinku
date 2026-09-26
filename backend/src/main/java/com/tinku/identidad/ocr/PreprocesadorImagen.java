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

    /** Lado largo con el que trabaja el OCR: ~300 dpi para una tarjeta de 86 mm, rápido en CPU. */
    static final int LADO_TRABAJO = 2000;
    /** Muestra chica para estimar la inclinación sin rotar la imagen entera 40 veces. */
    private static final int LADO_ESTIMACION = 600;

    /**
     * Carga para el OCR real (ADR-M1-08): respeta la orientación EXIF de la foto del celular
     * (sin esto una foto vertical llega acostada), la lleva al tamaño de trabajo, la pasa a
     * grises con más contraste y corrige la inclinación leve. {@code null} si no se puede leer.
     */
    public BufferedImage cargar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        BufferedImage img = leer(bytes);
        if (img == null) {
            return null;
        }
        img = rotar90(img, gradosExif(orientacionExif(bytes)));
        BufferedImage gris = convierteAGrises(escalar(img, LADO_TRABAJO));
        // La tarjeta suele ser lo más claro de la foto: recortarla saca la mesa y el fondo, que
        // confunden al OCR y a la estimación de la inclinación.
        gris = recortarTarjeta(gris);
        gris = ajustaContraste(escalar(gris, LADO_TRABAJO));
        double angulo = estimarAngulo(binarizar(escalar(gris, LADO_ESTIMACION)));
        return Math.abs(angulo) < 0.25 ? gris : rotar(gris, -angulo);
    }

    /**
     * Recorta la región clara más grande (la tarjeta) con un margen. Si no hay una región clara
     * que ocupe al menos un 15 % de la foto (p. ej. ya viene recortada), devuelve la imagen igual.
     */
    BufferedImage recortarTarjeta(BufferedImage gris) {
        BufferedImage chica = binarizar(escalar(gris, 400));
        int w = chica.getWidth(), h = chica.getHeight();
        boolean[] claro = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                claro[y * w + x] = (chica.getRGB(x, y) & 0xFF) > 128;
            }
        }
        boolean[] visto = new boolean[w * h];
        int[] cola = new int[w * h];
        int mejorTam = 0, bx0 = 0, by0 = 0, bx1 = w - 1, by1 = h - 1;
        for (int inicio = 0; inicio < w * h; inicio++) {
            if (!claro[inicio] || visto[inicio]) continue;
            int cab = 0, fin = 0, tam = 0, x0 = w, y0 = h, x1 = 0, y1 = 0;
            cola[fin++] = inicio;
            visto[inicio] = true;
            while (cab < fin) {
                int p = cola[cab++];
                int px = p % w, py = p / w;
                tam++;
                x0 = Math.min(x0, px); x1 = Math.max(x1, px);
                y0 = Math.min(y0, py); y1 = Math.max(y1, py);
                int[] vecinos = {p - 1, p + 1, p - w, p + w};
                for (int v : vecinos) {
                    if (v < 0 || v >= w * h || visto[v] || !claro[v]) continue;
                    if ((v == p - 1 && px == 0) || (v == p + 1 && px == w - 1)) continue;
                    visto[v] = true;
                    cola[fin++] = v;
                }
            }
            if (tam > mejorTam) {
                mejorTam = tam; bx0 = x0; by0 = y0; bx1 = x1; by1 = y1;
            }
        }
        int areaCaja = (bx1 - bx0 + 1) * (by1 - by0 + 1);
        if (areaCaja < 0.15 * w * h || areaCaja > 0.97 * w * h) {
            return gris; // no hay una tarjeta clara recortable (o ya ocupa toda la foto)
        }
        double f = (double) gris.getWidth() / w;
        int margen = (int) Math.round(0.02 * Math.max(gris.getWidth(), gris.getHeight()));
        int x = Math.max(0, (int) (bx0 * f) - margen), y = Math.max(0, (int) (by0 * f) - margen);
        int x2 = Math.min(gris.getWidth(), (int) ((bx1 + 1) * f) + margen);
        int y2 = Math.min(gris.getHeight(), (int) ((by1 + 1) * f) + margen);
        return gris.getSubimage(x, y, x2 - x, y2 - y);
    }

    /** Escala para que el lado largo mida {@code lado} (agranda fotos chicas, achica las grandes). */
    BufferedImage escalar(BufferedImage src, int lado) {
        int largo = Math.max(src.getWidth(), src.getHeight());
        if (largo == lado) {
            return src;
        }
        double f = (double) lado / largo;
        int w = Math.max(1, (int) Math.round(src.getWidth() * f));
        int h = Math.max(1, (int) Math.round(src.getHeight() * f));
        int tipo = src.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(w, h, tipo);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Rota de a 90° en sentido horario (0, 90, 180, 270); cualquier otro valor no rota. */
    public BufferedImage rotar90(BufferedImage src, int grados) {
        int g = ((grados % 360) + 360) % 360;
        if (g != 90 && g != 180 && g != 270) {
            return src;
        }
        int w = src.getWidth(), h = src.getHeight();
        boolean cruza = g != 180;
        int tipo = src.getType() == BufferedImage.TYPE_BYTE_GRAY ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(cruza ? h : w, cruza ? w : h, tipo);
        Graphics2D gr = out.createGraphics();
        AffineTransform at = new AffineTransform();
        if (g == 90) {
            at.translate(h, 0);
        } else if (g == 180) {
            at.translate(w, h);
        } else {
            at.translate(0, w);
        }
        at.rotate(Math.toRadians(g));
        gr.drawImage(src, at, null);
        gr.dispose();
        return out;
    }

    /** Blanco y negro con el umbral de Otsu: ayuda cuando el fondo de seguridad ensucia el texto. */
    public BufferedImage binarizar(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[src.getRGB(x, y) & 0xFF]++;
            }
        }
        int total = w * h;
        double suma = 0;
        for (int i = 0; i < 256; i++) suma += i * (double) hist[i];
        double sumaFondo = 0, mejorVar = -1;
        int pesoFondo = 0, umbral = 128;
        for (int t = 0; t < 256; t++) {
            pesoFondo += hist[t];
            if (pesoFondo == 0) continue;
            int pesoFrente = total - pesoFondo;
            if (pesoFrente == 0) break;
            sumaFondo += t * (double) hist[t];
            double mediaFondo = sumaFondo / pesoFondo;
            double mediaFrente = (suma - sumaFondo) / pesoFrente;
            double var = (double) pesoFondo * pesoFrente * (mediaFondo - mediaFrente) * (mediaFondo - mediaFrente);
            if (var > mejorVar) {
                mejorVar = var;
                umbral = t;
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        int blanco = Color.WHITE.getRGB(), negro = Color.BLACK.getRGB();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, (src.getRGB(x, y) & 0xFF) > umbral ? blanco : negro);
            }
        }
        return out;
    }

    /** Grados a rotar (horario) según el tag Orientation de EXIF (1, 3, 6, 8; los espejados se tratan igual). */
    static int gradosExif(int orientacion) {
        return switch (orientacion) {
            case 3, 4 -> 180;
            case 5, 6 -> 90;
            case 7, 8 -> 270;
            default -> 0;
        };
    }

    /**
     * Tag Orientation (0x0112) del EXIF de un JPEG, o 1 si no hay. Lectura mínima del segmento
     * APP1 sin dependencias: ImageIO ignora el EXIF y las fotos verticales llegaban acostadas.
     */
    static int orientacionExif(byte[] b) {
        try {
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
                return 1; // no es JPEG
            }
            int i = 2;
            while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
                int marca = b[i + 1] & 0xFF;
                int largo = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
                if (marca == 0xE1 && i + 10 <= b.length && new String(b, i + 4, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("Exif")) {
                    int tiff = i + 10;
                    boolean le = b[tiff] == 'I';
                    int ifd = tiff + leer32(b, tiff + 4, le);
                    int entradas = leer16(b, ifd, le);
                    for (int e = 0; e < entradas; e++) {
                        int p = ifd + 2 + e * 12;
                        if (leer16(b, p, le) == 0x0112) {
                            return leer16(b, p + 8, le);
                        }
                    }
                    return 1;
                }
                if (marca == 0xDA) {
                    return 1; // empezó la imagen: no hay EXIF antes
                }
                i += 2 + largo;
            }
        } catch (RuntimeException e) {
            // EXIF malformado: se sigue sin rotar
        }
        return 1;
    }

    private static int leer16(byte[] b, int p, boolean le) {
        return le ? (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) : ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF);
    }

    private static int leer32(byte[] b, int p, boolean le) {
        return le
                ? (b[p] & 0xFF) | ((b[p + 1] & 0xFF) << 8) | ((b[p + 2] & 0xFF) << 16) | ((b[p + 3] & 0xFF) << 24)
                : ((b[p] & 0xFF) << 24) | ((b[p + 1] & 0xFF) << 16) | ((b[p + 2] & 0xFF) << 8) | (b[p + 3] & 0xFF);
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
        for (double a = -10; a <= 10; a += 0.25) {
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
