package com.tinku.identidad.ocr;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests del {@link DniParser} (componente AISLADO, ADR-M1-01) contra muestras
 * de texto de AMBOS formatos de DNI argentino vigentes:
 * libreta (pre-2009, campos rotulados) y tarjeta plástica (2009+, sin rótulos).
 * Es un test unitario puro (no depende del binario nativo de Tesseract).
 */
class DniParserTest {

    private final DniParser parser = new DniParser();

    /** Libreta pre-2009: campos rotulados. */
    @Test
    void parseLibretaConRotulos() {
        List<LineaTexto> lineas = List.of(
                linea("REPUBLICA ARGENTINA", 0),
                linea("REGISTRO NACIONAL DE LAS PERSONAS", 20),
                linea("DNI: 12.345.678", 40),
                linea("APELLIDO: GARCIA", 60),
                linea("NOMBRES: JUAN CARLOS", 80),
                linea("SEXO: MASCULINO", 100),
                linea("NACIDO EL: 15/04/1980", 120),
                linea("NACIONALIDAD: ARGENTINO", 140)
        );

        ResultadoOcr r = parser.parse(lineas);

        assertTrue(r.documentoLegible());
        assertEquals("12345678", r.dniExtraido());
        assertEquals("GARCIA", r.apellidoExtraido());
        assertEquals("JUAN CARLOS", r.nombreExtraido());
        assertEquals(LocalDate.of(1980, 4, 15), r.fechaNacimientoExtraida());
    }

    /** Libreta: valor del rótulo en la línea siguiente (OCR que separa rótulo y valor). */
    @Test
    void parseLibretaValorEnLineaSiguiente() {
        List<LineaTexto> lineas = List.of(
                linea("APELLIDO:", 0),
                linea("PEREZ", 20),
                linea("NOMBRES:", 40),
                linea("MARIA ELENA", 60),
                linea("NUMERO 30.123.456", 80),
                linea("FECHA DE NACIMIENTO", 100),
                linea("10/08/1995", 120)
        );

        ResultadoOcr r = parser.parse(lineas);

        assertTrue(r.documentoLegible());
        assertEquals("30123456", r.dniExtraido());
        assertEquals("PEREZ", r.apellidoExtraido());
        assertEquals("MARIA ELENA", r.nombreExtraido());
        assertEquals(LocalDate.of(1995, 8, 10), r.fechaNacimientoExtraida());
    }

    /** Tarjeta plástica 2009+: sin rótulos; apellido, nombres, N° DNI, fecha. */
    @Test
    void parseTarjetaPlasticaSinRotulos() {
        List<LineaTexto> lineas = List.of(
                linea("ARGENTINA", 0),
                linea("GARCIA", 30),
                linea("JUAN CARLOS", 60),
                linea("N 12345678", 90),
                linea("NACIMIENTO 17/04/1990", 120),
                linea("SEXO", 150)
        );

        ResultadoOcr r = parser.parse(lineas);

        assertTrue(r.documentoLegible());
        assertEquals("12345678", r.dniExtraido());
        assertEquals("GARCIA", r.apellidoExtraido());
        assertEquals("JUAN CARLOS", r.nombreExtraido());
        assertEquals(LocalDate.of(1990, 4, 17), r.fechaNacimientoExtraida());
    }

    /** Tarjeta plástica con marca "Nº" y fecha en la línea siguiente. */
    @Test
    void parseTarjetaPlasticaConMarcaN() {
        List<LineaTexto> lineas = List.of(
                linea("REPUBLICA ARGENTINA", 0),
                linea("FERNANDEZ", 25),
                linea("MARTIN", 55),
                linea("N DE 22.987.654", 85),
                linea("Fecha de nacimiento", 115),
                linea("09/03/1988", 140)
        );

        ResultadoOcr r = parser.parse(lineas);

        assertTrue(r.documentoLegible());
        assertEquals("22987654", r.dniExtraido());
        assertEquals("FERNANDEZ", r.apellidoExtraido());
        assertEquals("MARTIN", r.nombreExtraido());
        assertEquals(LocalDate.of(1988, 3, 9), r.fechaNacimientoExtraida());
    }

    /** Orden de las líneas no depende del orden de llegada (se ordena por Y). */
    @Test
    void parseOrdenaIgnoraElOrdenDeLlegada() {
        List<LineaTexto> desordenado = List.of(
                linea("NOMBRES: JUAN CARLOS", 80),
                linea("DNI: 12.345.678", 40),
                linea("APELLIDO: GARCIA", 60),
                linea("NACIDO EL: 15/04/1980", 120)
        );

        ResultadoOcr r = parser.parse(desordenado);

        assertTrue(r.documentoLegible());
        assertEquals("12345678", r.dniExtraido());
        assertEquals("GARCIA", r.apellidoExtraido());
        assertEquals("JUAN CARLOS", r.nombreExtraido());
        assertEquals(LocalDate.of(1980, 4, 15), r.fechaNacimientoExtraida());
    }

    /** Juego de campos incompleto → ilegible, nunca campos parciales. */
    @Test
    void parseIncompletoEsIlegible() {
        List<LineaTexto> lineas = List.of(
                linea("REPUBLICA ARGENTINA", 0),
                linea("GARCIA", 30),
                // falta el Nº de DNI y la fecha
                linea("JUAN CARLOS", 60)
        );

        ResultadoOcr r = parser.parse(lineas);

        assertFalse(r.documentoLegible());
    }

    @Test
    void parseIlegibleCuandoVacio() {
        assertFalse(parser.parse(List.of()).documentoLegible());
        assertFalse(parser.parse(null).documentoLegible());
    }

    private LineaTexto linea(String texto, double y) {
        return new LineaTexto(texto, y);
    }
}
