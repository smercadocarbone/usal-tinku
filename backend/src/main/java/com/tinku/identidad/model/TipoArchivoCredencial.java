package com.tinku.identidad.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de archivo admitidos para una Credencial Académica (AUD-007). Se detectan
 * por su firma (magic bytes), NUNCA por el Content-Type ni la extensión que manda
 * el cliente: los dos son texto libre. La misma detección se usa al subir
 * (allowlist) y al servir el archivo al Admin (Content-Type de la respuesta).
 */
public enum TipoArchivoCredencial {
    PDF("application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
    PNG("image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}),
    JPEG("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

    private final String mediaType;
    private final byte[] firma;

    TipoArchivoCredencial(String mediaType, byte[] firma) {
        this.mediaType = mediaType;
        this.firma = firma;
    }

    public String getMediaType() {
        return mediaType;
    }

    public static Optional<TipoArchivoCredencial> detectar(byte[] contenido) {
        if (contenido == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.coincide(contenido)).findFirst();
    }

    private boolean coincide(byte[] contenido) {
        if (contenido.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (contenido[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
