package com.tinku.reputacion.web;

import com.tinku.reputacion.service.ComentariosTutorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** R-E E-3 (FR-REP-001): comentarios públicos del perfil del Tutor (autenticado, como el perfil). */
@RestController
@RequestMapping("/api/tutores")
public class ComentariosTutorController {

    private final ComentariosTutorService service;

    public ComentariosTutorController(ComentariosTutorService service) {
        this.service = service;
    }

    @GetMapping("/{tutorId}/calificaciones")
    public ResponseEntity<List<ComentariosTutorService.ComentarioPublico>> comentarios(
            @PathVariable UUID tutorId, @RequestParam(defaultValue = "5") int limite) {
        return ResponseEntity.ok(service.comentarios(tutorId, limite));
    }
}
