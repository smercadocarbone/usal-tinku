package com.tinku.pagos.port;

/**
 * OAuth de MercadoPago (ADR-M5-02): canje del {@code code} de autorización y refresco de tokens,
 * ambos contra {@code POST /oauth/token}. El contrato se tomó de la documentación de MP para
 * marketplaces; verificarlo en sandbox con dos cuentas de prueba antes de cobrar plata real.
 */
public interface MercadoPagoOAuthClient {

    TokensMp canjearCodigo(String code, String codeVerifier, String redirectUri);

    TokensMp refrescar(String refreshToken);

    /** {@code expiresInSegundos}: MP devuelve ~15552000 (180 días). */
    record TokensMp(String accessToken, String refreshToken, String userId, String publicKey,
                    long expiresInSegundos) {
    }
}
