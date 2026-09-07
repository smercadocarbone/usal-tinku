package com.tinku.identidad.port;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Por defecto no hay storage real: no persiste el archivo y deriva una URL del
 * nombre. Reemplazable por una implementación real (S3/local) cuando se decida
 * (ADR de storage pendiente).
 */
@Component
public class StubAlmacenamiento implements Almacenamiento {
    @Override
    public String guardar(byte[] contenido, String nombreOriginal) {
        String base = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nombreOriginal.getBytes(StandardCharsets.UTF_8));
        return "stub:/credenciales/" + base;
    }
}
