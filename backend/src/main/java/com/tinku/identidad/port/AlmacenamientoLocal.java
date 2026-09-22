package com.tinku.identidad.port;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Almacenamiento local de archivos (credenciales de Tutor, US-4). Persiste el
 * archivo en el filesystem del servidor y devuelve su URL {@code file:}. El
 * directorio es configurable (default: {@code java.io.tmpdir}/tinku) — para
 * el panel Admin (M8) la URL apunta a un archivo que el proceso sirve; cuando
 * haya infraestructura real (S3 u object storage) se reemplaza este bean por
 * el proveedor de ese ADR, sin tocar el contrato del puerto.
 */
@Component
public class AlmacenamientoLocal implements Almacenamiento {

    private final Path directorio;
    /** Ruta real (symlinks resueltos) contra la que se valida toda lectura. */
    private final Path directorioReal;

    public AlmacenamientoLocal(
            @Value("${tinku.almacenamiento.directorio:${java.io.tmpdir}/tinku}") String directorio) {
        this.directorio = Paths.get(directorio);
        try {
            Files.createDirectories(this.directorio);
            this.directorioReal = this.directorio.toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de almacenamiento: " + directorio, e);
        }
    }

    @Override
    public String guardar(byte[] contenido, String nombreOriginal) {
        String nombre = UUID.randomUUID() + "-" + sanear(nombreOriginal);
        Path destino = directorio.resolve(nombre);
        try {
            Files.write(destino, contenido);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + nombre, e);
        }
        return destino.toUri().toString();
    }

    /**
     * AUD-007: la referencia sale de la base, pero igual se valida — si una fila se
     * corrompe o se manipula, esto no puede convertirse en lectura arbitraria del
     * disco. Se resuelven {@code ..} y symlinks ({@code toRealPath}) ANTES de comparar
     * contra el directorio, así un enlace que apunte afuera tampoco pasa.
     */
    @Override
    public byte[] leer(String referencia) {
        Path archivo;
        try {
            URI uri = new URI(referencia);
            if (!"file".equals(uri.getScheme())) {
                throw new ArchivoNoDisponibleException();
            }
            archivo = Paths.get(uri).toRealPath();
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException
                 | IOException | NullPointerException e) {
            throw new ArchivoNoDisponibleException();
        }
        if (!archivo.startsWith(directorioReal) || !Files.isRegularFile(archivo)) {
            throw new ArchivoNoDisponibleException();
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new ArchivoNoDisponibleException();
        }
    }

    /** Quita separadores de path: el nombre original nunca arma subdirectorios. */
    private String sanear(String nombreOriginal) {
        String limpio = nombreOriginal.replaceAll("[^a-zA-Z0-9._-]", "_");
        return limpio.isBlank() ? "archivo" : limpio;
    }
}