package com.tinku.admin.notificacion;

import com.tinku.shared.UsuarioActual;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bandeja in-app (FASE2-03, AUD-014). Cada usuario ve y marca SOLO sus avisos: uno
 * ajeno responde 404 (no se confirma que exista).
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionesController {

    private static final int TAMANIO_PAGINA = 30;

    private final NotificacionRepository repo;
    private final UsuarioActual usuarioActual;

    public NotificacionesController(NotificacionRepository repo, UsuarioActual usuarioActual) {
        this.repo = repo;
        this.usuarioActual = usuarioActual;
    }

    /** Más nuevas primero, de a 30. */
    @GetMapping
    public List<NotificacionResponse> mias(@RequestParam(defaultValue = "0") int pagina, Authentication authentication) {
        UUID yo = usuarioActual.obtener(authentication).getId();
        return repo.findByDestinatarioIdOrderByCreadaAtDesc(yo, PageRequest.of(Math.max(pagina, 0), TAMANIO_PAGINA))
                .map(NotificacionResponse::from)
                .getContent();
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> noLeidas(Authentication authentication) {
        return Map.of("cantidad", repo.countByDestinatarioIdAndLeidaAtIsNull(usuarioActual.obtener(authentication).getId()));
    }

    @PostMapping("/{id}/leida")
    @Transactional
    public ResponseEntity<NotificacionResponse> marcarLeida(@PathVariable UUID id, Authentication authentication) {
        UUID yo = usuarioActual.obtener(authentication).getId();
        Notificacion n = repo.findByIdAndDestinatarioId(id, yo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (n.getLeidaAt() == null) {
            n.setLeidaAt(Instant.now());
        }
        return ResponseEntity.ok(NotificacionResponse.from(n));
    }
}
