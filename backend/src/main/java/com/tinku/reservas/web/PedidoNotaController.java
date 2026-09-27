package com.tinku.reservas.web;

import com.tinku.reservas.service.NotaClaseService;
import com.tinku.reservas.service.PedidoPrevioService;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pedido previo a la clase (FR-RES-027) y nota del Tutor al Adulto Responsable (FR-RES-026).
 * {@code GET} responde 204 si todavía no hay nada.
 */
@RestController
@RequestMapping("/api/reservas/{id}")
public class PedidoNotaController {

    private final PedidoPrevioService pedidos;
    private final NotaClaseService notas;
    private final UsuarioActual usuarioActual;

    public PedidoNotaController(PedidoPrevioService pedidos, NotaClaseService notas, UsuarioActual usuarioActual) {
        this.pedidos = pedidos;
        this.notas = notas;
        this.usuarioActual = usuarioActual;
    }

    public record NotaRequest(String texto) {
    }

    public record PedidoRequest(String texto) {
    }

    @PutMapping("/pedido")
    public ResponseEntity<PedidoPrevioService.Vista> guardarPedido(@PathVariable UUID id,
                                                                  @RequestBody PedidoRequest request,
                                                                  Authentication authentication) {
        return ResponseEntity.ok(pedidos.guardar(usuarioActual.obtener(authentication), id, request.texto()));
    }

    @GetMapping("/pedido")
    public ResponseEntity<PedidoPrevioService.Vista> verPedido(@PathVariable UUID id, Authentication authentication) {
        return pedidos.ver(usuarioActual.obtener(authentication), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/nota")
    public ResponseEntity<NotaClaseService.Vista> escribirNota(@PathVariable UUID id, @RequestBody NotaRequest request,
                                                              Authentication authentication) {
        return ResponseEntity.ok(notas.escribir(usuarioActual.obtener(authentication), id, request.texto()));
    }

    @GetMapping("/nota")
    public ResponseEntity<NotaClaseService.Vista> verNota(@PathVariable UUID id, Authentication authentication) {
        return notas.ver(usuarioActual.obtener(authentication), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
