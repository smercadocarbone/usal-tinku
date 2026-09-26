package com.tinku.pagos.web;

import com.tinku.pagos.service.CuentaMpException;
import com.tinku.pagos.service.CuentasMpService;
import com.tinku.pagos.service.CuentasMpService.EstadoConexion;
import com.tinku.shared.UsuarioActual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

/**
 * Conexión de MercadoPago del Tutor por OAuth (ADR-M5-02).
 * <ul>
 *   <li>{@code GET /api/pagos/mp/conectar}: URL de autorización (solo TUTOR).</li>
 *   <li>{@code GET /api/pagos/mp/callback}: vuelta de MP. Pública (el navegador llega sin JWT);
 *       la autenticación es el {@code state} de un solo uso. Redirige a {@code /cuenta/cobros}.</li>
 *   <li>{@code GET /api/pagos/mp/estado} y {@code DELETE /api/pagos/mp/conexion}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pagos/mp")
public class CuentaMpController {

    private static final Logger LOG = LoggerFactory.getLogger(CuentaMpController.class);

    private final CuentasMpService cuentasMp;
    private final UsuarioActual usuarioActual;
    private final String urlPublica;

    public CuentaMpController(CuentasMpService cuentasMp, UsuarioActual usuarioActual,
                              @Value("${tinku.app.url-publica:}") String urlPublica) {
        this.cuentasMp = cuentasMp;
        this.usuarioActual = usuarioActual;
        this.urlPublica = urlPublica == null ? "" : urlPublica.replaceAll("/+$", "");
    }

    @GetMapping("/conectar")
    public Map<String, String> conectar(Authentication authentication) {
        return Map.of("url", cuentasMp.iniciarConexion(usuarioActual.obtener(authentication)));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String resultado;
        if (error != null) {
            resultado = "cancelado";
        } else {
            try {
                cuentasMp.completarConexion(state, code);
                resultado = "ok";
            } catch (CuentaMpException e) {
                resultado = e.status() == HttpStatus.CONFLICT ? "otra-cuenta" : "vencido";
            } catch (RuntimeException e) {
                LOG.warn("No se pudo completar la conexión con MercadoPago", e);
                resultado = "error";
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(urlPublica + "/cuenta/cobros?mp=" + resultado).toString())
                .build();
    }

    @GetMapping("/estado")
    public EstadoConexion estado(Authentication authentication) {
        return cuentasMp.estado(usuarioActual.obtener(authentication));
    }

    @DeleteMapping("/conexion")
    public ResponseEntity<Void> desconectar(Authentication authentication) {
        cuentasMp.desconectar(usuarioActual.obtener(authentication));
        return ResponseEntity.noContent().build();
    }
}
