package com.tinku.reservas.web;

import com.tinku.reservas.service.NotaClaseService;
import com.tinku.reservas.service.PedidoPrevioService;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Pedido previo a la clase (FR-RES-027/028) y nota del Tutor al Adulto Responsable (FR-RES-026).
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

    @PutMapping(value = "/pedido", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PedidoPrevioService.Vista> guardarPedido(
            @PathVariable UUID id,
            @RequestPart(value = "texto", required = false) String texto,
            @RequestPart(value = "archivo", required = false) MultipartFile archivo,
            Authentication authentication) throws IOException {
        byte[] contenido = archivo == null || archivo.isEmpty() ? null : archivo.getBytes();
        return ResponseEntity.ok(pedidos.guardar(usuarioActual.obtener(authentication), id, texto, contenido,
                archivo == null ? null : archivo.getOriginalFilename()));
    }

    @GetMapping("/pedido")
    public ResponseEntity<PedidoPrevioService.Vista> verPedido(@PathVariable UUID id, Authentication authentication) {
        return pedidos.ver(usuarioActual.obtener(authentication), id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/pedido/archivo")
    public ResponseEntity<byte[]> archivoPedido(@PathVariable UUID id, Authentication authentication) {
        PedidoPrevioService.Archivo archivo = pedidos.archivo(usuarioActual.obtener(authentication), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(archivo.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(archivo.contenido());
    }

    @DeleteMapping("/pedido/archivo")
    public ResponseEntity<Void> quitarArchivo(@PathVariable UUID id, Authentication authentication) {
        pedidos.quitarArchivo(usuarioActual.obtener(authentication), id);
        return ResponseEntity.noContent().build();
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
