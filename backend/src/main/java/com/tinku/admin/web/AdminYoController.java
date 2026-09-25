package com.tinku.admin.web;

import com.tinku.admin.AdminModeracionGate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code GET /api/admin/yo} → {@code {"rol": "moderacion_seguridad" |
 * "soporte_financiero"}}: el rol del Admin logueado. Lo usa el panel para
 * ocultar del menú lo que ese rol no puede usar (B10) — el backend sigue
 * validando el rol en cada cola igual: esto es UX, no seguridad.
 *
 * Gateado con {@code adminAutenticado} (admin activo de CUALQUIER rol),
 * igual que {@code /api/admin/salud}; el detalle de rol de cada cola lo
 * siguen decidiendo {@code requiereModeracion}/{@code requiereSoporteFinanciero}.
 */
@RestController
@RequestMapping("/api/admin/yo")
public class AdminYoController {

    private final AdminModeracionGate gate;

    public AdminYoController(AdminModeracionGate gate) {
        this.gate = gate;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> yo(Authentication authentication) {
        return ResponseEntity.ok(Map.of("rol", gate.adminAutenticado(authentication).getRol().getValor()));
    }
}