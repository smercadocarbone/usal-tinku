package com.tinku.identidad.port;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AUD-007: {@code leer} recibe una URL que sale de la base, no del usuario que subió
 * el archivo — igual se valida que la ruta resuelta caiga DENTRO del directorio
 * configurado (defensa contra path traversal si la fila se corrompe o se manipula).
 */
class AlmacenamientoLocalTest {

    @TempDir Path dir;

    @Test
    void leeLoQueGuardo() {
        AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(dir.toString());
        byte[] contenido = "%PDF-1.4 hola".getBytes(StandardCharsets.UTF_8);

        String url = almacenamiento.guardar(contenido, "titulo.pdf");

        assertThat(almacenamiento.leer(url)).isEqualTo(contenido);
    }

    @Test
    void rechazaUnArchivoFueraDelDirectorio(@TempDir Path otroDir) throws Exception {
        AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(dir.toString());
        Path ajeno = Files.writeString(otroDir.resolve("secreto.txt"), "no");

        assertThatThrownBy(() -> almacenamiento.leer(ajeno.toUri().toString()))
                .isInstanceOf(ArchivoNoDisponibleException.class);
    }

    @Test
    void rechazaTraversalConPuntoPunto(@TempDir Path otroDir) throws Exception {
        AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(dir.toString());
        Files.writeString(dir.getParent().resolve("fuera.txt"), "no");

        String traversal = dir.toUri() + "../fuera.txt";

        assertThatThrownBy(() -> almacenamiento.leer(traversal))
                .isInstanceOf(ArchivoNoDisponibleException.class);
    }

    @Test
    void rechazaUrlsQueNoSonDeArchivoLocal() {
        AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(dir.toString());

        assertThatThrownBy(() -> almacenamiento.leer("https://cdn.test/titulo.pdf"))
                .isInstanceOf(ArchivoNoDisponibleException.class);
        assertThatThrownBy(() -> almacenamiento.leer("no es una uri"))
                .isInstanceOf(ArchivoNoDisponibleException.class);
    }

    @Test
    void archivoInexistenteDentroDelDirectorio() {
        AlmacenamientoLocal almacenamiento = new AlmacenamientoLocal(dir.toString());

        assertThatThrownBy(() -> almacenamiento.leer(dir.resolve("no-existe.pdf").toUri().toString()))
                .isInstanceOf(ArchivoNoDisponibleException.class);
    }
}
