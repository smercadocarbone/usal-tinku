package com.tinku.reservas.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.Paquete;
import com.tinku.reservas.service.PaqueteService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Paquete mensual (FR-RES-032..037, ADR-M5-03). El pago del paquete es el de su clase ancla:
 * el frontend va a {@code /pagar?reserva={reservaAnclaId}} como con una clase suelta.
 */
@RestController
@RequestMapping("/api/reservas/paquete")
public class PaqueteController {

    private final PaqueteService paquetes;
    private final UsuarioActual usuarioActual;

    public PaqueteController(PaqueteService paquetes, UsuarioActual usuarioActual) {
        this.paquetes = paquetes;
        this.usuarioActual = usuarioActual;
    }

    public record NuevoPaqueteRequest(@NotNull UUID tutorId, UUID beneficiarioId, @NotNull Instant horario,
                                      @NotNull Integer duracionMinutos) {
    }

    public record PaqueteResponse(UUID id, UUID reservaAnclaId, int cantidadClases, int duracionMinutos,
                                  int descuentoPorcentaje, BigDecimal precioTotal, String estado,
                                  Instant vigenteHasta, List<Instant> fechas) {
        static PaqueteResponse from(Paquete p) {
            return new PaqueteResponse(p.getId(), p.getReservaAnclaId(), p.getCantidadClases(), p.getDuracionMinutos(),
                    p.getDescuentoPorcentaje(), p.getPrecioTotal(), p.getEstado(), p.getVigenteHasta(),
                    PaqueteService.fechas(p.getVigenteHasta().minus(PaqueteService.VIGENCIA)));
        }
    }

    /** Si el Tutor ofrece el paquete y con qué descuento (para el paso de reserva). */
    @GetMapping("/oferta")
    public ResponseEntity<PaqueteService.Oferta> oferta(@RequestParam UUID tutorId) {
        return ResponseEntity.ok(paquetes.oferta(tutorId));
    }

    @PostMapping
    public ResponseEntity<PaqueteResponse> crear(@Valid @RequestBody NuevoPaqueteRequest request,
                                                 Authentication authentication) {
        Usuario yo = usuarioActual.obtener(authentication);
        Paquete paquete = paquetes.crear(yo, request.tutorId(), request.beneficiarioId(), request.horario(),
                request.duracionMinutos());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaqueteResponse.from(paquete));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id, Authentication authentication) {
        paquetes.cancelar(usuarioActual.obtener(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
