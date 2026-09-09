package com.tinku.identidad.ocr;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementación FALSA para desarrollo local y tests — NUNCA activar en
 * un perfil que no sea "dev" o "test". Devuelve un resultado "exitoso" que
 * hace eco de los datos declarados en el formulario, para poder probar el
 * resto del flujo de registro (UsuarioService) sin depender de una cuenta
 * real de un proveedor externo mientras el ADR-M1-01 sigue abierto.
 *
 * Hacer eco de lo declarado (en lugar de devolver constantes) permite
 * registrar MÚLTIPLES cuentas en dev — con constantes fijas el DNI extraído
 * es siempre el mismo y el segundo registro choca contra la unicidad
 * (FR-ID-018) — y probar la variante edad insuficiente declarando una fecha
 * de nacimiento de menor. Documentado en DatosDniDeclarados.
 *
 * Al resolver el ADR, esta clase se queda SOLO para "dev"/"test"; la
 * implementación real (ej. GoogleVisionOcrService) se activa en el resto
 * de los perfiles.
 */
@Service
@Profile({"dev", "test"})
public class StubOcrService implements OcrService {

    @Override
    public ResultadoOcr procesarDocumento(byte[] imagenDocumento, DatosDniDeclarados declarados) {
        if (imagenDocumento == null || imagenDocumento.length == 0) {
            return ResultadoOcr.ilegible();
        }
        return new ResultadoOcr(
                true,
                declarados.dni(),
                declarados.nombre(),
                declarados.apellido(),
                declarados.fechaNacimiento()
        );
    }
}
