package com.tinku.pagos.web;

import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.repository.TarifaTutorRepository;
import com.tinku.pagos.service.PagoService;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plan M5 §4 — {@code POST /api/pagos/preferencia}: genera la preferencia de
 * pago de MercadoPago para una Reserva (US-1, T-M5-02). La reserva la paga
 * quien la creó (Estudiante adulto o Adulto Responsable, Artículo II); el
 * guard de pagador se aplica acá, no como capa de presentación.
 *
 * {@code GET /api/pagos/precio-referencia/{provincia}}: sugerencia de precio de
 * referencia regional (US-6, T-M5-09) — el Tutor la consulta al configurar su
 * perfil para no adivinar cuánto cobrar en su zona.
 *
 * {@code PUT /api/pagos/tarifa}: el Tutor fija el precio por hora de su
 * perfil (US-6, FR-PAG-006, Chunk M5-H). {@code GET /api/pagos/tarifa}: la
 * lee (UX-06 §4; antes la pantalla de precio no tenía de dónde leerla). 204 si
 * todavía no la definió.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final UsuarioActual usuarioActual;
    private final TarifaTutorRepository tarifaRepo;

    public PagoController(PagoService pagoService, UsuarioActual usuarioActual, TarifaTutorRepository tarifaRepo) {
        this.pagoService = pagoService;
        this.usuarioActual = usuarioActual;
        this.tarifaRepo = tarifaRepo;
    }

    @PostMapping("/preferencia")
    public ResponseEntity<PreferenciaResponse> preferencia(
            @Valid @RequestBody SolicitudPreferenciaRequest request,
            Authentication authentication) {
        PreferenciaPago preferencia = pagoService.generarPreferencia(
                usuarioActual.obtener(authentication), request.reservaId());
        return ResponseEntity.ok(PreferenciaResponse.from(preferencia));
    }

    @GetMapping("/precio-referencia/{provincia}")
    public ResponseEntity<PrecioReferenciaResponse> precioReferencia(
            @PathVariable String provincia) {
        PrecioReferenciaRegional precio = pagoService.sugerirPrecioReferencia(provincia);
        return ResponseEntity.ok(PrecioReferenciaResponse.from(precio));
    }

    @GetMapping("/tarifa")
    public ResponseEntity<TarifaTutorResponse> miTarifa(Authentication authentication) {
        return tarifaRepo.findByTutorId(usuarioActual.obtener(authentication).getId())
                .map(t -> ResponseEntity.ok(TarifaTutorResponse.from(t)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/tarifa")
    public ResponseEntity<TarifaTutorResponse> tarifa(
            @Valid @RequestBody ActualizarTarifaTutorRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(TarifaTutorResponse.from(
                pagoService.actualizarTarifaTutor(
                        usuarioActual.obtener(authentication), request.precioHora())));
    }
}