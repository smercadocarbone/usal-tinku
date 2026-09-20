package com.tinku.resumen.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.resumen.service.ResumenService;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consulta del resumen automático de una Sesión (M6, auditoría 2026-09-20).
 * Único endpoint del módulo — antes no existía ninguno: la generación
 * (T-M6-01..07) corría por completo pero no había forma de leer el resultado.
 */
@RestController
@RequestMapping("/api")
public class ResumenController {

    private final ResumenService resumenService;
    private final UsuarioActual usuarioActual;

    public ResumenController(ResumenService resumenService, UsuarioActual usuarioActual) {
        this.resumenService = resumenService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping("/sesiones/{sesionId}/resumen")
    public ResponseEntity<ResumenResponse> obtener(@PathVariable UUID sesionId,
                                                    Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        return ResponseEntity.ok(
                ResumenResponse.from(resumenService.obtenerParaParticipante(usuario, sesionId)));
    }
}
