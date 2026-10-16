package com.tinku.identidad.ocr;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Lector FALSO para dev y test: nunca lee nada ("no procesable"), así que no rechaza ni compara el
 * nombre — el Admin decide. Los tests que ejercitan el triaje lo reemplazan con un mock.
 */
@Service
@Profile({"dev", "test"})
public class StubLectorTextoDocumento implements LectorTextoDocumento {

    @Override
    public TextoDocumento leer(byte[] contenido) {
        return TextoDocumento.noProcesable();
    }
}
