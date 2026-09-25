package com.tinku.pagos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra los tokens OAuth de MercadoPago de los Tutores (ADR-M5-02): AES-256-GCM con IV aleatorio
 * de 12 bytes, guardado adelante del texto cifrado. Solo JDK, sin dependencias nuevas. La clave
 * ({@code TINKU_CLAVE_CIFRADO}, 32 bytes en base64) vive fuera de la base; sin clave, fail-closed
 * al usarlo (el backend arranca igual, como sin {@code MP_ACCESS_TOKEN}).
 */
@Component
public class CifradorTokens {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec clave;

    public CifradorTokens(@Value("${tinku.pagos.clave-cifrado:}") String claveBase64) {
        SecretKeySpec k = null;
        if (claveBase64 != null && !claveBase64.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(claveBase64.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("TINKU_CLAVE_CIFRADO tiene que ser de 32 bytes en base64.");
            }
            k = new SecretKeySpec(bytes, "AES");
        }
        this.clave = k;
    }

    public boolean configurado() {
        return clave != null;
    }

    public byte[] cifrar(String texto) {
        exigirClave();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el token.", e);
        }
    }

    public String descifrar(byte[] datos) {
        exigirClave();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAG_BITS, datos, 0, IV_BYTES));
            return new String(cipher.doFinal(datos, IV_BYTES, datos.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el token.", e);
        }
    }

    private void exigirClave() {
        if (clave == null) {
            throw new MercadoPagoNoConfiguradoException();
        }
    }
}
