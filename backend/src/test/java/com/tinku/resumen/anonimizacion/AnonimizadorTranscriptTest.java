package com.tinku.resumen.anonimizacion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-M6-04 — anonimizacion ejecutada y probada AISLADA (componente puro, sin
 * Spring ni proveedor), antes de conectarla al pipeline de LLM. FR-SUM-005:
 * nombres, contactos, enlaces y datos de pago nunca llegan al modelo.
 * Fail-safe a favor de enmascarar de mas (Artículo II).
 */
class AnonimizadorTranscriptTest {

    private final AnonimizadorTranscript anonimizador = new AnonimizadorTranscript();

    @Test
    void enmascaraNombresDelDiccionario() {
        String limpio = anonimizador.anonimizar("Hola, soy Pablo y me acompaña Maria.");

        assertEquals("Hola, soy [nombre] y me acompaña [nombre].", limpio);
    }

    @Test
    void enmascaraNombreTrasPresentacion() {
        String limpio = anonimizador.anonimizar("Me llamo Juana. Mi nombre es Juan Carlos.");

        assertTrue(limpio.contains("Me llamo [nombre]."));
        assertTrue(limpio.contains("Mi nombre es [nombre]."));
        assertFalse(limpio.contains("Juana"));
        assertFalse(limpio.contains("Juan Carlos"));
    }

    @Test
    void enmascaraTelefonosArgentinos() {
        String limpio = anonimizador.anonimizar("Mi numero es +54 9 11 5555-1234 y el fijo "
                + "011 15 4321-5678, el otro 1155551234.");

        assertFalse(limpio.contains("5555"));
        assertFalse(limpio.contains("4321"));
        assertFalse(limpio.contains("1155551234"));
        assertTrue(limpio.contains("[telefono]"));
    }

    @Test
    void enmascaraEmailsYUrls() {
        String limpio = anonimizador.anonimizar("Escribime a juan.perez@gmail.com o visitá "
                + "http://geometria-facil.com.ar y también www.tinku.ar");

        assertFalse(limpio.contains("juan.perez@gmail.com"));
        assertFalse(limpio.contains("http://geometria-facil.com.ar"));
        assertFalse(limpio.contains("www.tinku.ar"));
        assertTrue(limpio.contains("[email]"));
        assertTrue(limpio.contains("[url]"));
    }

    @Test
    void enmascaraDatosDePago() {
        String limpio = anonimizador.anonimizar("Te pago por CBU 2850590940090418135201 o por "
                + "alias juan.perez.mp. Mi tarjeta es 4554 7843 2190 5566 y el CUIT "
                + "20-30405060-7.");

        assertFalse(limpio.contains("2850590940090418135201"));
        assertFalse(limpio.contains("juan.perez.mp"));
        assertFalse(limpio.contains("4554 7843 2190 5566"));
        assertFalse(limpio.contains("20-30405060-7"));
        assertEquals(4, contar(limpio, "[pago]"));
    }

    @Test
    void enmascaraDniConPuntos() {
        String limpio = anonimizador.anonimizar("mi DNI es 30.123.456, según te dije");

        assertFalse(limpio.contains("30.123.456"));
        assertFalse(limpio.contains("30123456"));
    }

    @Test
    void textoSinDatosPersonalesQuedaIntacto() {
        String texto = "Repasamos las fracciones equivalentes y resolvimos la guia de la pagina 12.";

        assertEquals(texto, anonimizador.anonimizar(texto));
    }

    @Test
    void nullSigueNull() {
        assertEquals(null, anonimizador.anonimizar(null));
    }

    /** T-M6-08 (pre-muestra): un transcript realista de prueba nunca conserva
     *  datos personales después de la anonimizacion. */
    @Test
    void transcriptDePruebaQuedaLibreDeDatosPersonales() {
        String transcript = "Hola María, soy el profe Sergio García. Mi celular es +54 9 11 "
                + "2626-3030 y mi mail es sergio.garcia.fisica@gmail.com. Hoy vemos la página "
                + "http://ejemplos-conservacion.ar. Para la próxima clase me pasás el pago por "
                + "alias nadia.estudiante.mp o por CBU 0110599540000001027312.";

        String limpio = anonimizador.anonimizar(transcript);

        assertFalse(limpio.contains("María"));
        assertFalse(limpio.contains("Sergio"));
        assertFalse(limpio.contains("2626"));
        assertFalse(limpio.contains("sergio.garcia.fisica@gmail.com"));
        assertFalse(limpio.contains("ejemplos-conservacion.ar"));
        assertFalse(limpio.contains("nadia.estudiante.mp"));
        assertFalse(limpio.contains("0110599540000001027312"));
        assertTrue(limpio.contains("[nombre]"));
        assertTrue(limpio.contains("[telefono]"));
        assertTrue(limpio.contains("[email]"));
        assertTrue(limpio.contains("[url]"));
        assertTrue(limpio.contains("[pago]"));
    }

    private static long contar(String texto, String subcadena) {
        int idx = 0;
        long n = 0;
        while ((idx = texto.indexOf(subcadena, idx)) != -1) {
            n++;
            idx += subcadena.length();
        }
        return n;
    }
}