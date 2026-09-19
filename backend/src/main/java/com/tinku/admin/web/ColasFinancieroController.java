package com.tinku.admin.web;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.ReembolsoParcialProveedor;
import com.tinku.pagos.repository.PrecioReferenciaRegionalRepository;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.pagos.service.LiberacionEscrowService;
import com.tinku.pagos.service.PasarelaService;
import com.tinku.pagos.web.PrecioReferenciaResponse;
import com.tinku.shared.AdminModeracionGate;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Intervención manual del Admin de Soporte Financiero (US-5/US-8,
 * FR-ADM-004/007). Todas las operaciones requieren rol {@code soporte_financiero}
 * en {@code admin.admins} (T-M8-03/06) — un Admin de Moderación recibe 403 acá.
 *
 *  - {@code GET /pagos-fallidos}: cola de escrows con {@code intentos_liberacion}
 *    agotados (FR-PAG-007) — la regla de negocio (3 reintentos, backoff 5/15/1h)
 *    vive en M5, esta cola solo la consume.
 *  - {@code POST /pagos-fallidos/{id}/reintentar}: una liberación manual que
 *    reusa EL flujo de M5 ({@link LiberacionEscrowService#ejecutarLiberacion}),
 *    jamás reimplementa la llamada al proveedor.
 *  - {@code POST /transacciones/{id}/reembolso-parcial}: reembolso PARCIAL por
 *    disputa (FR-PAG-010) vía {@link ReembolsoParcialProveedor} — flujo manual
 *    exclusivo de M8, nunca una regla automática de M5.
 *  - {@code POST /precios-regionales}: agrega una versión nueva por provincia
 *    (nunca sobreescribe la vigente — FR-PAG-006, FR-ADM-007).
 */
@RestController
@RequestMapping("/api/admin/financiero")
public class ColasFinancieroController {

    /** FR-PAG-007 / Tabla_Tiempos: 3 reintentos automáticos; desde ahí, cola manual. */
    private static final int LIBERACIONES_AGOTADAS = 3;

    private final AdminModeracionGate gate;
    private final TransaccionRepository transaccionRepo;
    private final PrecioReferenciaRegionalRepository precioRepo;
    private final LiberacionEscrowService liberacionEscrow;
    private final ReembolsoParcialProveedor reembolsoParcial;
    private final PasarelaService pasarela;

    public ColasFinancieroController(AdminModeracionGate gate,
                                     TransaccionRepository transaccionRepo,
                                     PrecioReferenciaRegionalRepository precioRepo,
                                     LiberacionEscrowService liberacionEscrow,
                                     ReembolsoParcialProveedor reembolsoParcial,
                                     PasarelaService pasarela) {
        this.gate = gate;
        this.transaccionRepo = transaccionRepo;
        this.precioRepo = precioRepo;
        this.liberacionEscrow = liberacionEscrow;
        this.reembolsoParcial = reembolsoParcial;
        this.pasarela = pasarela;
    }

    @GetMapping("/pagos-fallidos")
    public ResponseEntity<List<PagoFallidoResponse>> pagosFallidos(Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        return ResponseEntity.ok(transaccionRepo
                .findByEstadoAndIntentosLiberacionGreaterThanEqual(
                        EstadoTransaccion.RETENIDO_ESCROW, LIBERACIONES_AGOTADAS)
                .stream().map(PagoFallidoResponse::from).toList());
    }

    @PostMapping("/pagos-fallidos/{transaccionId}/reintentar")
    public ResponseEntity<?> reintentarLiberacion(@PathVariable UUID transaccionId,
                                                  Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        Transaccion transaccion = transaccionRepo.findById(transaccionId)
                .orElse(null);
        if (transaccion == null) {
            return ResponseEntity.notFound().build();
        }
        // Solo la cola de intervención manual (estado retenido + reintentos agotados):
        // una transacción aún en reintento automático no se toca en paralelo.
        if (transaccion.getEstado() != EstadoTransaccion.RETENIDO_ESCROW
                || transaccion.getIntentosLiberacion() < LIBERACIONES_AGOTADAS) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", "La transacción no está en la cola de intervención manual."));
        }
        liberacionEscrow.ejecutarLiberacion(transaccionId);
        return ResponseEntity.ok(PagoFallidoResponse.from(
                transaccionRepo.findById(transaccionId).orElseThrow()));
    }

    /**
     * Reembolso PARCIAL por disputa (FR-PAG-010, T-M5-08): devuelve una PARTE del
     * escrow cuando una disputa en M8/M9 se resuelve así. Flujo 100% manual — que
     * este endpoint exista no habilita ningún reembolso parcial automático
     * (FR-PAG-009 sigue prohibiéndolo). {@code monto} lo decide Soporte: &gt; 0 y
     * &lt; {@code montoBruto} (devolver todo o más que lo cobrado es un reembolso
     * total, que tiene otro flujo). La diferencia de comisión de gateway la
     * absorbe Tinku. El estado de la Transacción no se toca: el resto del escrow
     * sigue su curso normal y MP maneja el saldo (Spec no define una transición).
     */
    @PostMapping("/transacciones/{transaccionId}/reembolso-parcial")
    public ResponseEntity<?> reembolsoParcial(@PathVariable UUID transaccionId,
                                              @Valid @RequestBody ReembolsoParcialRequest request,
                                              Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        Transaccion transaccion = transaccionRepo.findById(transaccionId)
                .orElse(null);
        if (transaccion == null) {
            return ResponseEntity.notFound().build();
        }
        // Solo hay algo que parcializar si el dinero sigue en el escrow.
        boolean escrowDisponible = transaccion.getEstado() == EstadoTransaccion.RETENIDO_ESCROW
                || transaccion.getEstado() == EstadoTransaccion.PAUSADO_DENUNCIA;
        boolean montoParcial = request.monto().compareTo(transaccion.getMontoBruto()) < 0;
        if (!escrowDisponible || !montoParcial) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error",
                            "El reembolso parcial requiere escrow retenido y un monto menor al cobrado."));
        }
        // V22 — transacción en modo Bypass: no hay dinero real que devolver.
        // El reembolso parcial es una operación contra MercadoPago; reembolsar un
        // id falso sería inventar una transacción con dinero que nunca existió.
        if (transaccion.isEnBypass()) {
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error",
                            "Transacción simulada (modo Bypass): no hay dinero real que reembolsar."));
        }
        reembolsoParcial.reembolsarParcial(transaccion.getMpPaymentId(), request.monto());
        return ResponseEntity.ok(PagoFallidoResponse.from(
                transaccionRepo.findById(transaccionId).orElseThrow()));
    }

    /**
     * Estado actual de la pasarela de pagos (V22): {@code habilitada=true} →
     * cobro real; {@code false} → modo Bypass. Lectura del flag real en base,
     * nunca cacheado (ver {@link PasarelaService}).
     */
    @GetMapping("/pasarela")
    public ResponseEntity<PasarelaEstadoResponse> pasarela(Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        return ResponseEntity.ok(PasarelaEstadoResponse.from(pasarela.estaHabilitada()));
    }

    /**
     * Alternar el modo Bypass de la pasarela. Efecto inmediato en toda llamada
     * posterior de M5 al proveedor (generarPreferencia, liberación, reembolsos).
     * Solo Soporte Financiero; la auditoría la registra el interceptor de M8.
     */
    @PatchMapping("/pasarela")
    public ResponseEntity<PasarelaEstadoResponse> actualizarPasarela(
            @Valid @RequestBody ActualizarPasarelaRequest request,
            Authentication authentication) {
        UUID adminUsuarioId = gate.requiereSoporteFinanciero(authentication);
        return ResponseEntity.ok(PasarelaEstadoResponse.from(
                pasarela.establecerHabilitada(request.habilitada(), adminUsuarioId)));
    }

    /** Auditoría 2026-09-18 (gap del frontend): la tabla vigente completa, una
     *  fila por provincia (mayor versión) — antes solo había POST a ciegas. */
    @GetMapping("/precios-regionales")
    public ResponseEntity<List<PrecioReferenciaResponse>> preciosRegionales(
            Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        return ResponseEntity.ok(precioRepo.vigentesPorProvincia().stream()
                .map(PrecioReferenciaResponse::from)
                .toList());
    }

    @PostMapping("/precios-regionales")
    public ResponseEntity<?> actualizarPrecioRegional(
            @Valid @RequestBody ActualizarPrecioRegionalRequest request,
            Authentication authentication) {
        gate.requiereSoporteFinanciero(authentication);
        // FR-ADM-007: versión nueva = mayor versión + 1 de esa provincia.
        int proximaVersion = precioRepo
                .findFirstByProvinciaOrderByVersionDesc(request.provincia())
                .map(p -> p.getVersion() + 1)
                .orElse(1);
        PrecioReferenciaRegional nuevo = new PrecioReferenciaRegional();
        nuevo.setProvincia(request.provincia());
        nuevo.setVersion(proximaVersion);
        nuevo.setValorSugerido(request.valorSugerido());
        nuevo.setVigenteDesde(Instant.now());
        try {
            precioRepo.save(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Dos revisiones concurrentes sobre la misma provincia: la otra ya
            // clavó su versión. Se reintenta una sola vez con la versión recalculada.
            int versionReintento = precioRepo
                    .findFirstByProvinciaOrderByVersionDesc(request.provincia())
                    .map(p -> p.getVersion() + 1)
                    .orElse(1);
            nuevo.setVersion(versionReintento);
            precioRepo.save(nuevo);
        }
        return ResponseEntity.ok(PrecioReferenciaResponse.from(nuevo));
    }
}