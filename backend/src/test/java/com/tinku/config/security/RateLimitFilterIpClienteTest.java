package com.tinku.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Detrás de Cloudflare Tunnel todas las requests llegan desde el contenedor de
 * cloudflared (un solo remoteAddr): sin leer la IP real, el límite de login sería
 * compartido por todos los usuarios. El header solo se usa si se configura.
 */
class RateLimitFilterIpClienteTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);
    private static final String CLOUDFLARED = "172.18.0.9";

    private int login(RateLimitFilter filtro, String ipCloudflare) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/usuarios/login");
        req.setRequestURI("/api/usuarios/login");
        req.setRemoteAddr(CLOUDFLARED);
        if (ipCloudflare != null) {
            req.addHeader("CF-Connecting-IP", ipCloudflare);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtro.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @Test
    void conHeaderConfigurado_cadaClienteTieneSuPropioLimite() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(5, 2, 120, "CF-Connecting-IP", RELOJ);

        assertThat(login(filtro, "200.1.1.1")).isEqualTo(200);
        assertThat(login(filtro, "200.1.1.1")).isEqualTo(200);
        assertThat(login(filtro, "200.1.1.1")).isEqualTo(429);
        // Otro usuario detrás del mismo túnel no queda bloqueado por el primero.
        assertThat(login(filtro, "190.2.2.2")).isEqualTo(200);
    }

    @Test
    void sinHeaderConfigurado_seIgnora_paraQueNoSePuedaFalsificar() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(5, 2, 120, "", RELOJ);

        assertThat(login(filtro, "1.1.1.1")).isEqualTo(200);
        assertThat(login(filtro, "2.2.2.2")).isEqualTo(200);
        // Cambiar el header no evade el límite: cuenta el remoteAddr.
        assertThat(login(filtro, "3.3.3.3")).isEqualTo(429);
    }

    @Test
    void conHeaderConfiguradoPeroAusente_usaElRemoteAddr() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(5, 2, 120, "CF-Connecting-IP", RELOJ);

        assertThat(login(filtro, null)).isEqualTo(200);
        assertThat(login(filtro, null)).isEqualTo(200);
        assertThat(login(filtro, null)).isEqualTo(429);
    }
}
