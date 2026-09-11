package com.tinku.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tinku.identidad.model.Usuario;
import com.tinku.shared.UsuarioActual;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrato 2b de M2-F: catálogo granular de temas y perfil de temas del Tutor.
 *  - GET /api/catalogos: árbol nivel -> curso -> materia -> temas (con filtros
 *    opcionales exactos ?nivel=&curso=&materia=). Autenticado, sin restricción.
 *  - GET /api/tutores/me/temas: los ids propios (cualquier perfil).
 *  - PUT /api/tutores/me/temas: SOLO TUTOR (403 si no); valida 404/422 contra
 *    el catálogo vigente (FR-MATCH-006) y persiste solo los ids (sin tocar
 *    embedding: lo repuebla el recompute de 2c).
 */
@RestController
@RequestMapping("/api")
public class CatalogoTemasController {

    private final CatalogoService catalogoService;
    private final PerfilTutorTemasService temasService;
    private final UsuarioActual usuarioActual;

    public CatalogoTemasController(CatalogoService catalogoService,
                                   PerfilTutorTemasService temasService,
                                   UsuarioActual usuarioActual) {
        this.catalogoService = catalogoService;
        this.temasService = temasService;
        this.usuarioActual = usuarioActual;
    }

    @GetMapping("/catalogos")
    public List<CatalogoService.CatalogoRama> catalogos(
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) String curso,
            @RequestParam(required = false) String materia) {
        return catalogoService.arbol(trimToNull(nivel), trimToNull(curso), trimToNull(materia));
    }

    @GetMapping("/tutores/me/temas")
    public ResponseEntity<TemaIdsResponse> temasPropios(Authentication authentication) {
        List<UUID> ids = temasService.temasDel(usuarioActual.obtener(authentication));
        return ResponseEntity.ok(new TemaIdsResponse(ids));
    }

    @PutMapping("/tutores/me/temas")
    public ResponseEntity<TemaIdsResponse> guardarTemas(@RequestBody TemaIdsRequest request,
                                                        Authentication authentication) {
        if (request.temaIds() == null) {
            throw new TemaIdMalformadoException();
        }
        Usuario usuario = usuarioActual.obtener(authentication);
        List<UUID> guardados = temasService.guardarTemas(usuario, request.temaIds());
        return ResponseEntity.ok(new TemaIdsResponse(guardados));
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    public record TemaIdsRequest(@JsonProperty("tema_ids") List<String> temaIds) {
    }

    public record TemaIdsResponse(@JsonProperty("tema_ids") List<UUID> temaIds) {
    }
}