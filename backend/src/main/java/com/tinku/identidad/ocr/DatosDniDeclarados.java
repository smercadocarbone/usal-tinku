package com.tinku.identidad.ocr;

import java.time.LocalDate;

/**
 * Datos de identidad que el usuario DECLARÓ en el formulario de registro.
 *
 * El proveedor real de OCR (Tesseract) los ignora: extrae todo de la imagen.
 * Solo {@link StubOcrService} (perfiles dev/test) los usa, para hacer eco de
 * lo declarado y así permitir registrar múltiples cuentas en desarrollo y
 * variar la edad/nombre en las pruebas — algo que no se puede con un resultado
 * firmado a constantes (ver el TODO que dejó la T-M1 en UsuarioServiceTest).
 *
 * No se usa para validar nada: las validaciones de coincidencia y edad siguen
 * ocurriendo sobre el resultado EXTRAÍDO (UsuarioService, paso 4), nunca sobre
 * lo declarado.
 */
public record DatosDniDeclarados(
        String dni,
        String nombre,
        String apellido,
        LocalDate fechaNacimiento
) {
}