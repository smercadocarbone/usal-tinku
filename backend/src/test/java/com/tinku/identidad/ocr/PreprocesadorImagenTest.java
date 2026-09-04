package com.tinku.identidad.ocr;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del {@link PreprocesadorImagen} (componente AISLADO, ADR-M1-01) —
 * grayscale, contraste y deskew sobre imágenes sintéticas. Java puro, no
 * requiere binario nativo.
 */
class PreprocesadorImagenTest {

    private final PreprocesadorImagen p = new PreprocesadorImagen();

    @Test
    void convierteAGrises() {
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, new Color(255, 0, 0).getRGB());   // rojo puro
        img.setRGB(1, 0, new Color(0, 255, 0).getRGB());   // verde puro
        img.setRGB(2, 0, new Color(0, 0, 255).getRGB());   // azul puro
        img.setRGB(3, 0, Color.WHITE.getRGB());

        BufferedImage gris = p.convierteAGrises(img);

        for (int x = 0; x < 4; x++) {
            int rgb = gris.getRGB(x, 0);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            // En una imagen de grises monotípica, r==g==b.
            assertEquals(r, g);
            assertEquals(g, b);
        }
        // Blanco se mantiene claro, negro se mantiene oscuro.
        assertTrue(((gris.getRGB(3, 0) >> 16) & 0xFF) > 200);
        // El rojo puro (255,0,0) → gris oscuro ~54.
        assertTrue(((gris.getRGB(0, 0) >> 16) & 0xFF) < 100);
    }

    @Test
    void ajustaContraste() {
        // Imagen de bajo contraste: todos los grises entre 100 y 140.
        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                int v = 100 + (x * 2); // 100..138
                img.setRGB(x, y, new Color(v, v, v).getRGB());
            }
        }

        BufferedImage out = p.ajustaContraste(img);

        int min = 255, max = 0;
        for (int y = 0; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                int g = (out.getRGB(x, y) >> 16) & 0xFF;
                min = Math.min(min, g);
                max = Math.max(max, g);
            }
        }
        // El estirado lleva extremos al rango completo (o casi).
        assertTrue(max - min >= 200, "se esperaba mayor contraste, rango=" + (max - min));
    }

    @Test
    void deskewAnguloDeBarrasHorizontalesCero() {
        BufferedImage img = barrasHorizontales(200, 200);
        double angulo = p.estimarAngulo(img);
        assertTrue(Math.abs(angulo) < 1.0, "barras horizontales deberían estimar ~0°, dio " + angulo);
    }

    @Test
    void deskewAnguloDeBarrasInclinadas() {
        BufferedImage base = barrasHorizontales(200, 200);
        BufferedImage rotada = p.rotar(base, 5); // texto inclinado +5°
        double estimado = p.estimarAngulo(rotada);
        // Para una inclinación de +5°, endereza() rota por -ángulo; el ángulo
        // devuelto con signo es la corrección ANTES de negar, o sea -5.
        assertTrue(Math.abs(estimado + 5) < 1.5,
                "se esperaba estimar ~-5°, dio " + estimado);
    }

    @Test
    void enderezaImagenYaDerechaDevuelveMisma() {
        BufferedImage img = barrasHorizontales(100, 100);
        BufferedImage out = p.endereza(img);
        assertEquals(img.getWidth(), out.getWidth());
        assertEquals(img.getHeight(), out.getHeight());
    }

    @Test
    void preprocesarBytesNulosEsIlegible() {
        assertEquals(null, p.preprocesar(null));
        assertEquals(null, p.preprocesar(new byte[0]));
    }

    private BufferedImage barrasHorizontales(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        for (int y = 30; y < h - 20; y += 12) {
            g.fillRect(10, y, w - 40, 4); // líneas horizontales "texto"
        }
        g.dispose();
        return img;
    }
}
