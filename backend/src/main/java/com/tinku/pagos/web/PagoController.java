package com.tinku.pagos.web;

import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.service.PagoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan M5 §4 — {@code POST /api/pagos/preferencia}: genera la preferencia de
 * pago de MercadoPago para una Reserva (US-1, T-M5-02). La reserva la paga
 * quien la creó (Estudiante adulto o Adulto Responsable, Artículo II); el
 * guard de pagador se aplica acá, no como capa de presentación.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final UsuarioRepository usuarioRepository;

    public PagoController(PagoService pagoService, UsuarioRepository usuarioRepository) {
        this.pagoService = pagoService;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/preferencia")
    public ResponseEntity<PreferenciaResponse> preferencia(
            @Valid @RequestBody SolicitudPreferenciaRequest request,
            Authentication authentication) {
        PreferenciaPago preferencia = pagoService.generarPreferencia(
                usuarioActual(authentication), request.reservaId());
        return ResponseEntity.ok(PreferenciaResponse.from(preferencia));
    }

    private Usuario usuarioActual(Authentication authentication) {
        return usuarioRepository.findByDni(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuario autenticado no encontrado"));
    }
}