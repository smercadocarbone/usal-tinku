package com.tinku.identidad.ocr;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test E2E del OCR REAL (ADR-M1-01): instancia {@link TesseractOcrService} de
 * verdad (Tess4J sobre el binario nativo de Tesseract, idioma español) y
 * procesa una imagen sintética de DNI estilo libreta (formato pre-2009,
 * campos rotulados). Verifica que el pipeline completo — preprocesado →
 * Tesseract → agrupación en líneas → {@link DniParser} — extrae los campos.
 *
 * El servicio real está gated a perfiles fuera de dev/test, así que este test
 * NO usa el contexto Spring: construye el bean a mano, igual que los tests
 * aislados de {@code DniParser}/{@code PreprocesadorImagen} (mismo patrón de
 * ADR-M1-01: componentes aislados y testeables).
 *
 * Es dependiente del entorno: si el binario nativo de Tesseract o el data
 * español no están disponibles, se SKIPEA (assumption), nunca finge que el
 * OCR funciona. En esta sesión se verificó con tesseract 5.5.3 + spa.traineddata.
 */
class TesseractOcrServiceRealTest {

    private static String tessdata;
    private static boolean bibliotecaNativaDisponible;

    @BeforeAll
    static void resolverEntorno() {
        tessdata = detecarTessdata();
        bibliotecaNativaDisponible = configurarRutaJnaDeLaBibliotecaNativa() != null;
    }

    private static String detecarTessdata() {
        String prefijo = System.getenv("TESSDATA_PREFIX");
        if (prefijo != null && new File(prefijo, "spa.traineddata").exists()) {
            return prefijo;
        }
        for (String dir : List.of(
                "/opt/homebrew/share/tessdata",   // Homebrew (Apple Silicon)
                "/usr/local/share/tessdata",      // Homebrew (Intel)
                "/usr/share/tesseract-ocr/5/tessdata" // Linux
        )) {
            if (new File(dir, "spa.traineddata").exists()) {
                return dir;
            }
        }
        return null;
    }

    /**
     * Tess4J carga el binario nativo vía JNA. devuelve el directorio que
     * contiene libtesseract (dylib/so) o null si no se encontró (el test se
     * skipea). Homebrew instala la lib en /opt/homebrew/lib — fuera de la ruta
     * por defecto de JNA — así que hay que exponerla con jna.library.path.
     */
    private static String configurarRutaJnaDeLaBibliotecaNativa() {
        String yaConfigurada = System.getProperty("jna.library.path");
        if (yaConfigurada != null && !yaConfigurada.isBlank()) {
            return yaConfigurada;
        }
        for (String dir : List.of(
                "/opt/homebrew/lib",   // Homebrew (Apple Silicon)
                "/usr/local/lib",      // Homebrew (Intel)
                "/usr/lib/x86_64-linux-gnu",
                "/usr/lib/aarch64-linux-gnu")) {
            File lib = new File(dir, System.getProperty("os.name").toLowerCase().contains("mac")
                    ? "libtesseract.dylib" : "libtesseract.so");
            if (lib.exists()) {
                System.setProperty("jna.library.path", dir);
                return dir;
            }
        }
        return null;
    }

    private static TesseractOcrService servicio() {
        Assumptions.assumeTrue(tessdata != null,
                "spa.traineddata no disponible — se SKIPEA el test real de OCR");
        Assumptions.assumeTrue(bibliotecaNativaDisponible,
                "Binario nativo de Tesseract (libtesseract) no disponible — se SKIPEA el test real de OCR");
        return new TesseractOcrService(new DniParser(), new PreprocesadorImagen(), tessdata, "spa");
    }

    /** DNI estilo libreta pre-2009 con campos rotulados, en alta resolución (validado con tesseract CLI). */
    private byte[] imagenDniLibreta() throws Exception {
        int w = 1400, h = 900;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 64));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = 100;
        for (String linea : List.of(
                "REPUBLICA ARGENTINA",
                "REGISTRO NACIONAL DE LAS PERSONAS",
                "DNI: 12.345.678",
                "APELLIDO: GARCIA",
                "NOMBRES: JUAN CARLOS",
                "SEXO: MASCULINO",
                "FECHA DE NACIMIENTO: 15/04/1980",
                "NACIONALIDAD: ARGENTINO")) {
            g.drawString(linea, 100, y);
            y += 110;
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void extraeCamposDeUnDniLibretaConOcrReal() throws Exception {
        ResultadoOcr r = servicio().procesarDocumento(imagenDniLibreta(), null);

        assertTrue(r.documentoLegible(), "El OCR real debe leer la imagen sintética libreta");
        assertEquals("12345678", r.dniExtraido());
        assertEquals("JUAN CARLOS", r.nombreExtraido());
        assertEquals("GARCIA", r.apellidoExtraido());
        assertEquals(LocalDate.of(1980, 4, 15), r.fechaNacimientoExtraida());
    }

    @Test
    void sinImagenDevuelveIlegible() {
        ResultadoOcr r = servicio().procesarDocumento(new byte[0], null);
        assertFalse(r.documentoLegible());
    }
}