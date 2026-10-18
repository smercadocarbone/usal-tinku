package com.tinku.admin.evaluacion;

import com.tinku.admin.AdminModeracionGate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * T-TES-11b §4.3: pestaña "Evaluación" del panel M8. Solo lectura y transversal a los dos roles de
 * Admin (como "Salud de Infraestructura"): son números agregados, sin datos por usuario. Responde
 * también con el flag apagado (lo informa en {@code habilitada}) para poder mirar los datos después
 * de cerrar las sesiones.
 */
@RestController
@RequestMapping("/api/admin/evaluacion")
public class IndicadoresEvaluacionController {

    private final IndicadoresEvaluacionService indicadores;
    private final AdminModeracionGate gate;

    public IndicadoresEvaluacionController(IndicadoresEvaluacionService indicadores, AdminModeracionGate gate) {
        this.indicadores = indicadores;
        this.gate = gate;
    }

    @GetMapping("/indicadores")
    public ResponseEntity<IndicadoresEvaluacionResponse> indicadores(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        gate.adminAutenticado(authentication);
        return ResponseEntity.ok(indicadores.calcular(desde, hasta));
    }
}
