package com.tinku.admin.web;

import com.tinku.admin.AdminModeracionGate;
import com.tinku.matching.TemasSugeridosService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * FR-ADM-009: temas que la gente busca y el catálogo no cubre (agregados, ADR-M2-04), para
 * que Moderación actualice el catálogo. Solo los pedidos varias veces; "resuelto" lo saca.
 */
@RestController
@RequestMapping("/api/admin/temas-sugeridos")
public class TemasSugeridosController {

    private final TemasSugeridosService temasSugeridos;
    private final AdminModeracionGate gate;

    public TemasSugeridosController(TemasSugeridosService temasSugeridos, AdminModeracionGate gate) {
        this.temasSugeridos = temasSugeridos;
        this.gate = gate;
    }

    @GetMapping
    public List<TemasSugeridosService.TemaSugerido> listar(Authentication authentication) {
        gate.requiereModeracion(authentication);
        return temasSugeridos.listar();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> resolver(@PathVariable UUID id, Authentication authentication) {
        gate.requiereModeracion(authentication);
        return temasSugeridos.descartar(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
