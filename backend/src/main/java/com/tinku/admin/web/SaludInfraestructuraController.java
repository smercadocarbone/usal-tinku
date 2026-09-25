package com.tinku.admin.web;

import com.tinku.admin.service.SaludInfraestructuraService;
import com.tinku.admin.AdminModeracionGate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pestaña "Salud de Infraestructura" del panel M8. Read-only y transversal a
 * ambos roles de Admin: cualquier Admin activo puede ver estado de infraestructura
 * (un 403 por rol no tendría sentido para diagnóstico). Excluida de la auditoría
 * de T-M8-02 en {@code AdminWebConfig}: sondear es lectura, no una acción.
 */
@RestController
@RequestMapping("/api/admin/salud")
public class SaludInfraestructuraController {

    private final SaludInfraestructuraService salud;
    private final AdminModeracionGate gate;

    public SaludInfraestructuraController(SaludInfraestructuraService salud,
                                          AdminModeracionGate gate) {
        this.salud = salud;
        this.gate = gate;
    }

    @GetMapping
    public ResponseEntity<SaludInfraestructuraResponse> salud(Authentication authentication) {
        gate.adminAutenticado(authentication);
        return ResponseEntity.ok(salud.datos());
    }
}