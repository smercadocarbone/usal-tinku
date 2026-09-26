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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test E2E del OCR REAL (ADR-M1-01, ADR-M1-08): instancia {@link TesseractOcrService} de verdad,
 * que invoca el programa {@code tesseract} del sistema, y procesa imágenes sintéticas de DNI:
 * libreta, y fotos de celular de la tarjeta actual (frente, frente con rotación EXIF y dorso con
 * MRZ; ver {@code src/test/resources/ocr/generar_dnis.py}, datos inventados).
 *
 * <p>Depende del entorno: sin el programa {@code tesseract} con el idioma español se SKIPEA
 * (assumption), nunca finge que el OCR funciona. La imagen de producción lo trae (Dockerfile).</p>
 */
class TesseractOcrServiceRealTest {

    private static boolean tesseractDisponible;

    @BeforeAll
    static void resolverEntorno() {
        try {
            Process p = new ProcessBuilder("tesseract", "--list-langs").redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes());
            tesseractDisponible = p.waitFor() == 0 && salida.contains("spa");
        } catch (Exception e) {
            tesseractDisponible = false;
        }
    }

    private static TesseractOcrService servicio() {
        Assumptions.assumeTrue(tesseractDisponible,
                "Programa tesseract con idioma español no disponible — se SKIPEA el test real de OCR");
        return new TesseractOcrService(new DniParser(), new PreprocesadorImagen(), "tesseract", "", "spa");
    }

    private static byte[] recurso(String nombre) throws Exception {
        try (var in = TesseractOcrServiceRealTest.class.getResourceAsStream("/ocr/" + nombre)) {
            return in.readAllBytes();
        }
    }

    /** Foto de celular del frente de la tarjeta actual (rótulos bilingües, mes en letras). */
    @Test
    void leeElFrenteDeLaTarjetaActualEnUnaFotoDeCelular() throws Exception {
        ResultadoOcr r = servicio().procesarDocumento(recurso("frente_foto.jpg"), null);

        assertTrue(r.documentoLegible(), "Debe leer el frente de la tarjeta actual");
        assertEquals("34567890", r.dniExtraido());
        assertEquals("GONZALEZ", r.apellidoExtraido());
        assertEquals("MARIA SOL", r.nombreExtraido());
        assertEquals(LocalDate.of(1990, 5, 15), r.fechaNacimientoExtraida());
    }

    /** Foto vertical: el sensor la guarda acostada y el EXIF dice cómo mostrarla. */
    @Test
    void respetaLaRotacionExifDeLaFoto() throws Exception {
        ResultadoOcr r = servicio().procesarDocumento(recurso("frente_exif6.jpg"), null);

        assertTrue(r.documentoLegible(), "Debe enderezar la foto según el EXIF");
        assertEquals("40123456", r.dniExtraido());
        assertEquals("PEREYRA", r.apellidoExtraido());
        assertEquals("JUAN IGNACIO", r.nombreExtraido());
        assertEquals(LocalDate.of(1997, 8, 3), r.fechaNacimientoExtraida());
    }

    /** Dorso: la zona de lectura mecánica (MRZ) con dígitos de control. */
    @Test
    void leeElDorsoPorLaZonaMrz() throws Exception {
        ResultadoOcr r = servicio().procesarDocumento(recurso("dorso_foto.jpg"), null);

        assertTrue(r.documentoLegible(), "Debe leer la MRZ del dorso");
        assertEquals("34567890", r.dniExtraido());
        assertEquals("GONZALEZ", r.apellidoExtraido());
        assertEquals("MARIA SOL", r.nombreExtraido());
        assertEquals(LocalDate.of(1990, 5, 15), r.fechaNacimientoExtraida());
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