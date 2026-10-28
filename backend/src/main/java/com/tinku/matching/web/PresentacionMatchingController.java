package com.tinku.matching.web;

import com.tinku.matching.service.MatchingNoDisponibleException;
import com.tinku.matching.service.TemasPresentacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** FR-MATCH-015: matching semántico real de la presentación de la tesis (público, con rate limit). */
@RestController
@RequestMapping("/api/presentacion")
public class PresentacionMatchingController {

    private final TemasPresentacionService temas;

    public PresentacionMatchingController(TemasPresentacionService temas) {
        this.temas = temas;
    }

    @PostMapping("/temas")
    public ResponseEntity<?> temas(@RequestBody TemasRequest request) {
        try {
            return ResponseEntity.ok(temas.buscar(request == null ? null : request.texto()));
        } catch (MatchingNoDisponibleException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "El matching no está disponible en este momento."));
        }
    }

    public record TemasRequest(String texto) {
    }
}
