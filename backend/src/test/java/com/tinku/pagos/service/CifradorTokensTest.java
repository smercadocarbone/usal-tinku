package com.tinku.pagos.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-M5-02: los tokens OAuth de los Tutores se guardan cifrados con AES-256-GCM. */
class CifradorTokensTest {

    private static final String CLAVE = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void cifraYDescifra_sinDejarElTextoPlano_yConIvDistintoCadaVez() {
        CifradorTokens cifrador = new CifradorTokens(CLAVE);
        byte[] a = cifrador.cifrar("APP_USR-token-secreto");
        byte[] b = cifrador.cifrar("APP_USR-token-secreto");

        assertThat(new String(a, StandardCharsets.ISO_8859_1)).doesNotContain("token-secreto");
        assertThat(a).isNotEqualTo(b);
        assertThat(cifrador.descifrar(a)).isEqualTo("APP_USR-token-secreto");
    }

    @Test
    void datosAlterados_noSeDescifran() {
        CifradorTokens cifrador = new CifradorTokens(CLAVE);
        byte[] a = cifrador.cifrar("APP_USR-token");
        a[a.length - 1] ^= 1;
        assertThatThrownBy(() -> cifrador.descifrar(a)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinClave_failClosed_yClaveDeLargoInvalido_noArranca() {
        assertThatThrownBy(() -> new CifradorTokens("").cifrar("x"))
                .isInstanceOf(MercadoPagoNoConfiguradoException.class);
        assertThatThrownBy(() -> new CifradorTokens(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }
}
