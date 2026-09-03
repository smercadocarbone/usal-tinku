package com.tinku.identidad.ocr;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Implementación FALSA para desarrollo local y tests — NUNCA activar en
 * un perfil que no sea "dev" o "test". Devuelve siempre un resultado
 * "exitoso" fabricado a partir de datos fijos, para poder probar el resto
 * del flujo de registro (UsuarioService) sin depender de una cuenta real
 * de un proveedor externo mientras el ADR-M1-01 sigue abierto.
 *
 * Al resolver el ADR, esta clase se queda SOLO para "dev"/"test"; la
 * implementación real (ej. GoogleVisionOcrService) se activa en el resto
 * de los perfiles.
 */
@Service
@Profile({"dev", "test"})
public class StubOcrService implements OcrService {

    @Override
    public ResultadoOcr procesarDocumento(byte[] imagenDocumento) {
        if (imagenDocumento == null || imagenDocumento.length == 0) {
            return ResultadoOcr.ilegible();
        }
        // Datos fabricados fijos — reemplazar por parseo real de un payload
        // de prueba si los tests necesitan variar el resultado.
        // Nota: nombre/apellido usan ESPACIO (no guion bajo) para que la
        // comparacion tolerante de UsuarioService (mayusculas/acentos, no
        // caracter exacto) los tome como equivalentes a "Nombre Stub".
        return new ResultadoOcr(
                true,
                "00000000",
                "NOMBRE STUB",
                "APELLIDO STUB",
                LocalDate.of(2000, 1, 1)
        );
    }
}
