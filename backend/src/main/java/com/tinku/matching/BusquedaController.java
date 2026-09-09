package com.tinku.matching;

import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints de búsqueda de Tutores (T-M2-08, T-M2-09). Todos autenticados por
 * JWT (SecurityConfig); cualquier perfil puede buscar, y el contexto de
 * autorización lo resuelve {@link MatchingContextoService} a partir de la
 * cuenta autenticada — un menor nunca autoriza, solo busca lo que su Adulto
 * Responsable autorizó (Artículo II).
 */
@RestController
@RequestMapping("/api/busquedas")
public class BusquedaController {

    private final MatchingOrquestador orquestador;
    private final BusquedasGuardadasService guardadasService;
    private final UsuarioActual usuarioActual;

    public BusquedaController(MatchingOrquestador orquestador,
                              BusquedasGuardadasService guardadasService,
                              UsuarioActual usuarioActual) {
        this.orquestador = orquestador;
        this.guardadasService = guardadasService;
        this.usuarioActual = usuarioActual;
    }

    /** US-1: búsqueda en lenguaje natural. US-2: respeta el contexto del menor. */
    @PostMapping
    public ResponseEntity<List<BusquedaResponse>> buscar(
            @Valid @RequestBody BusquedaRequest request,
            Authentication authentication
    ) {
        List<BusquedaResponse> resultados = orquestador.buscar(
                usuarioActual.obtener(authentication), request.textoBusqueda().trim());
        return ResponseEntity.ok(resultados);
    }

    /** US-6: guarda una búsqueda para re-ejecutar después (FR-MATCH-008). */
    @PostMapping("/guardadas")
    public ResponseEntity<GuardadaResponse> guardar(
            @Valid @RequestBody BusquedaRequest request,
            Authentication authentication
    ) {
        BusquedaGuardada guardada = guardadasService.guardar(
                usuarioActual.obtener(authentication).getId(), request.textoBusqueda().trim());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new GuardadaResponse(guardada.getId(),
                        guardada.getTextoBusqueda(), guardada.getCreatedAt()));
    }

    /** US-6: lista las búsquedas guardadas del usuario autenticado. */
    @GetMapping("/guardadas")
    public ResponseEntity<List<GuardadaResponse>> listar(Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        List<GuardadaResponse> guardadas = guardadasService.listar(usuario.getId())
                .stream()
                .map(g -> new GuardadaResponse(g.getId(), g.getTextoBusqueda(), g.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(guardadas);
    }

    /** US-6: re-ejecuta contra el índice vigente — resultados frescos, no congelados. */
    @PostMapping("/guardadas/{id}/ejecutar")
    public ResponseEntity<List<BusquedaResponse>> ejecutar(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        Usuario usuario = usuarioActual.obtener(authentication);
        BusquedaGuardada guardada = guardadasService.propia(usuario.getId(), id);
        List<BusquedaResponse> resultados = orquestador.buscar(usuario, guardada.getTextoBusqueda());
        return ResponseEntity.ok(resultados);
    }
}