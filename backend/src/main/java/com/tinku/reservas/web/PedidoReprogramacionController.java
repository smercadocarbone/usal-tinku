package com.tinku.reservas.web;

import com.tinku.reservas.service.PedidoReprogramacionService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de reprogramación del Tutor (FR-RES-029..031). Uno pendiente por reserva:
 * {@code GET} responde 204 si no hay.
 */
@RestController
@RequestMapping("/api/reservas/{id}/pedido-reprogramacion")
public class PedidoReprogramacionController {

    private final PedidoReprogramacionService pedidos;
    private final UsuarioActual usuarioActual;

    public PedidoReprogramacionController(PedidoReprogramacionService pedidos, UsuarioActual usuarioActual) {
        this.pedidos = pedidos;
        this.usuarioActual = usuarioActual;
    }

    public record PedirRequest(@NotNull Instant nuevoHorario,
                               @Size(max = 300, message = "El motivo no puede superar los 300 caracteres.") String motivo) {
    }

    @PostMapping
    public ResponseEntity<PedidoReprogramacionService.Vista> pedir(@PathVariable UUID id,
                                                                   @Valid @RequestBody PedirRequest request,
                                                                   Authentication authentication) {
        return ResponseEntity.status(201).body(pedidos.pedir(usuarioActual.obtener(authentication), id,
                request.nuevoHorario(), request.motivo()));
    }

    @GetMapping
    public ResponseEntity<PedidoReprogramacionService.Vista> ver(@PathVariable UUID id, Authentication authentication) {
        return pedidos.ver(usuarioActual.obtener(authentication), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/aceptar")
    public ResponseEntity<Void> aceptar(@PathVariable UUID id, Authentication authentication) {
        pedidos.aceptar(usuarioActual.obtener(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rechazar")
    public ResponseEntity<Void> rechazar(@PathVariable UUID id, Authentication authentication) {
        pedidos.rechazar(usuarioActual.obtener(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> retirar(@PathVariable UUID id, Authentication authentication) {
        pedidos.retirar(usuarioActual.obtener(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
