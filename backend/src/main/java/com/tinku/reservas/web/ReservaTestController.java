package com.tinku.reservas.web;

import com.tinku.reservas.service.ReservaService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * STUB TEMPORAL — reemplazar cuando M5 implemente el webhook real de
 * MercadoPago (Chunk M5-B). Solo existe en los perfiles dev/test: en prod no
 * hay bean y el endpoint responde 404 (no abre un camino de confirmación sin
 * pasar por M5). El webhook real validará firma y mapeará este mismo
 * resultado (pendiente_pago → confirmada).
 */
@Profile({"dev", "test"})
@RestController
@RequestMapping("/api/test/reservas")
public class ReservaTestController {

    private final ReservaService reservaService;

    public ReservaTestController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    /** STUB TEMPORAL — equivale al webhook de MercadoPago confirmando el pago (Chunk M5-B). */
    @PostMapping("/{id}/confirmar-pago-simulado")
    public ResponseEntity<ReservaResponse> confirmarPagoSimulado(@PathVariable UUID id) {
        return ResponseEntity.ok(ReservaResponse.from(reservaService.confirmarPagoSimulado(id)));
    }
}