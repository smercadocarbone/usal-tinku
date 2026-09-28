package com.tinku.pagos.web;

import com.tinku.pagos.service.EscrowService;
import com.tinku.pagos.model.PrecioReferenciaRegional;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.repository.TarifaTutorRepository;
import com.tinku.pagos.service.PagoService;
import com.tinku.pagos.service.ComisionMercadoPagoEstimada;
import com.tinku.pagos.service.ComisionPlataforma;
import com.tinku.pagos.service.PisoTarifa;
import com.tinku.shared.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

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
 * lee (UX-06 §4) junto con el piso por hora (T06); si todavía no la definió,
 * {@code precioHora} viene null (antes 204: el Tutor nuevo no conocía el piso).
 *
 * {@code GET /api/pagos/cobros/export?mes=AAAA-MM} (FR-PAG-020): los cobros del Tutor de ese
 * mes en CSV, para facturar y declarar en ARCA.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;
    private final UsuarioActual usuarioActual;
    private final TarifaTutorRepository tarifaRepo;
    private final PisoTarifa pisoTarifa;
    private final EscrowService escrowService;
    private final com.tinku.pagos.service.CobrosTutorService cobrosTutor;
    private final ComisionPlataforma comision;
    private final ComisionMercadoPagoEstimada comisionMp;

    public PagoController(PagoService pagoService, UsuarioActual usuarioActual, TarifaTutorRepository tarifaRepo,
                          PisoTarifa pisoTarifa, EscrowService escrowService,
                          com.tinku.pagos.service.CobrosTutorService cobrosTutor,
                          ComisionPlataforma comision, ComisionMercadoPagoEstimada comisionMp) {
        this.pagoService = pagoService;
        this.usuarioActual = usuarioActual;
        this.tarifaRepo = tarifaRepo;
        this.pisoTarifa = pisoTarifa;
        this.escrowService = escrowService;
        this.cobrosTutor = cobrosTutor;
        this.comision = comision;
        this.comisionMp = comisionMp;
    }

    /** Al volver de MercadoPago con el pago aprobado: confirma consultando a MP (ver
     *  {@link EscrowService#confirmarDesdeRetorno}). Idempotente con el webhook. */
    @PostMapping("/confirmar-retorno")
    public ResponseEntity<Void> confirmarRetorno(@Valid @RequestBody ConfirmarRetornoRequest request,
                                                 Authentication authentication) {
        pagoService.exigirPagador(usuarioActual.obtener(authentication), request.reservaId());
        escrowService.confirmarDesdeRetorno(request.reservaId(), request.paymentId());
        return ResponseEntity.noContent().build();
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

    /** R5: "Mis cobros" del Tutor (403 para cualquier otro). */
    @GetMapping("/mis-cobros")
    public com.tinku.pagos.service.CobrosTutorService.MisCobros misCobros(Authentication authentication) {
        return cobrosTutor.de(usuarioActual.obtener(authentication));
    }

    public record ConfigPaqueteRequest(boolean habilitado, int descuentoPorcentaje) {
    }

    /** v2.5 (ADR-M5-03): el Tutor ofrece el paquete mensual y fija su descuento. */
    @PutMapping("/tarifa/paquete")
    public ResponseEntity<TarifaTutorResponse> configurarPaquete(@RequestBody ConfigPaqueteRequest request,
                                                                 Authentication authentication) {
        return ResponseEntity.ok(TarifaTutorResponse.from(
                pagoService.configurarPaquete(usuarioActual.obtener(authentication), request.habilitado(),
                        request.descuentoPorcentaje()),
                pisoTarifa.pisoHora(), comision.porcentaje(), comisionMp.porcentaje()));
    }

    /** FR-PAG-020: CSV del mes (separador {@code ;} y BOM para que Excel en español lo abra bien). */
    @GetMapping("/cobros/export")
    public ResponseEntity<byte[]> exportarCobros(@RequestParam String mes, Authentication authentication) {
        YearMonth periodo;
        try {
            periodo = YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().build();
        }
        String csv = cobrosTutor.exportarMes(usuarioActual.obtener(authentication), periodo);
        byte[] cuerpo = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tinku-cobros-" + periodo + ".csv\"")
                .body(cuerpo);
    }

    @GetMapping("/tarifa")
    public ResponseEntity<TarifaTutorResponse> miTarifa(Authentication authentication) {
        UUID yo = usuarioActual.obtener(authentication).getId();
        BigDecimal piso = pisoTarifa.pisoHora();
        return ResponseEntity.ok(tarifaRepo.findByTutorId(yo)
                .map(t -> TarifaTutorResponse.from(t, piso, comision.porcentaje(), comisionMp.porcentaje()))
                .orElseGet(() -> TarifaTutorResponse.sinTarifa(yo, piso, comision.porcentaje(), comisionMp.porcentaje())));
    }

    @PutMapping("/tarifa")
    public ResponseEntity<TarifaTutorResponse> tarifa(
            @Valid @RequestBody ActualizarTarifaTutorRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(TarifaTutorResponse.from(
                pagoService.actualizarTarifaTutor(
                        usuarioActual.obtener(authentication), request.precioHora()),
                pisoTarifa.pisoHora(), comision.porcentaje(), comisionMp.porcentaje()));
    }
}