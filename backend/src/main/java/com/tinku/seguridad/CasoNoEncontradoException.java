package com.tinku.seguridad;

/** Un dato referenciado por el caso de moderación no existe (404) — p. ej. el
 * usuario denunciado o la sesión denunciada. Se usa como 404 genérico del
 * módulo; los casos específicos (denuncia/alerta) tienen su propia clase. */
public class CasoNoEncontradoException extends RuntimeException {
    public CasoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}