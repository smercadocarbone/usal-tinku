package com.tinku.matching.web;

import com.tinku.matching.model.BusquedaGuardada;
import com.tinku.matching.port.DatosDeReservaResultados;
import com.tinku.matching.service.BusquedaInvalidaException;
import com.tinku.matching.service.BusquedasGuardadasService;
import com.tinku.matching.service.MatchingContextoService;
import com.tinku.matching.service.MatchingOrquestador;
import com.tinku.matching.service.RegistroBusquedasEvaluacion;

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
    private final DatosDeReservaResultados datosDeReserva;
    private final RegistroBusquedasEvaluacion registroEvaluacion;

    /** T-TES-11b: id de la búsqueda registrada (solo con tinku.evaluacion.habilitada=true). */
    public static final String HEADER_BUSQUEDA_ID = "X-Busqueda-Id";

    public BusquedaController(MatchingOrquestador orquestador,
                              BusquedasGuardadasService guardadasService,
                              UsuarioActual usuarioActual,
                              DatosDeReservaResultados datosDeReserva,
                              RegistroBusquedasEvaluacion registroEvaluacion) {
        this.orquestador = orquestador;
        this.guardadasService = guardadasService;
        this.usuarioActual = usuarioActual;
        this.datosDeReserva = datosDeReserva;
        this.registroEvaluacion = registroEvaluacion;
    }

    /** US-1: búsqueda en lenguaje natural. US-2: respeta el contexto del menor.
     * Contrato 2b: al menos uno de texto/nombre/materia debe venir no vacío. */
    @PostMapping
    public ResponseEntity<List<BusquedaResponse>> buscar(
            @Valid @RequestBody BusquedaRequest request,
            Authentication authentication
    ) {
        Usuario usuario = usuarioActual.obtener(authentication);
        String texto = trimToNull(request.textoBusqueda());
        String nombre = trimToNull(request.nombre());
        String materia = trimToNull(request.filtroMateria());
        if (texto == null && nombre == null && materia == null) {
            throw new BusquedaInvalidaException();
        }
        List<BusquedaResponse> resultados = orquestador.buscar(usuario, texto, nombre, materia,
                trimToNull(request.filtroNivel()));
        List<BusquedaResponse> mostrados = datosDeReserva.completar(resultados, request.precioMaxHora());
        // T-TES-11b §4.1: con el flag encendido se registra qué se mostró (sin el texto) y se devuelve
        // el id en un header; el cuerpo no cambia.
        ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
        registroEvaluacion.registrar(usuario, texto, mostrados)
                .ifPresent(id -> ok.header(HEADER_BUSQUEDA_ID, id.toString()));
        return ok.body(mostrados);
    }

    /** T-TES-11b §4.1 (I-01): el usuario abrió el perfil o reservó un Tutor de esta búsqueda (204). */
    @PostMapping("/{id}/aceptaciones")
    public ResponseEntity<Void> aceptar(@PathVariable UUID id,
                                        @RequestBody AceptacionRequest request,
                                        Authentication authentication) {
        registroEvaluacion.aceptar(usuarioActual.obtener(authentication).getId(), id,
                request.tutorId(), request.tipo());
        return ResponseEntity.noContent().build();
    }

    /** US-6: guarda una búsqueda para re-ejecutar después (FR-MATCH-008).
     * Almacena el texto EFECTIVO (nombre/materia si no vino texto libre). */
    @PostMapping("/guardadas")
    public ResponseEntity<GuardadaResponse> guardar(
            @Valid @RequestBody BusquedaRequest request,
            Authentication authentication
    ) {
        Usuario usuario = usuarioActual.obtener(authentication);
        String textoEfectivo = textoEfectivo(request);
        if (textoEfectivo == null) {
            throw new BusquedaInvalidaException();
        }
        BusquedasGuardadasService.Guardado guardado =
                guardadasService.guardar(usuario.getId(), textoEfectivo, filtros(request));
        // R-E E-2 (T1-6): guardar lo mismo dos veces devuelve la que ya estaba (200), sin duplicar.
        return ResponseEntity.status(guardado.nueva() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(GuardadaResponse.from(guardado.guardada()));
    }

    /** US-6: lista las búsquedas guardadas del usuario autenticado. */
    @GetMapping("/guardadas")
    public ResponseEntity<List<GuardadaResponse>> listar(Authentication authentication) {
        Usuario usuario = usuarioActual.obtener(authentication);
        List<GuardadaResponse> guardadas = guardadasService.listar(usuario.getId())
                .stream()
                .map(GuardadaResponse::from)
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
        java.util.Map<String, Object> f = guardada.getFiltros() == null ? java.util.Map.of() : guardada.getFiltros();
        if (f.isEmpty()) {
            List<BusquedaResponse> resultados = orquestador.buscar(usuario, guardada.getTextoBusqueda());
            return ResponseEntity.ok(datosDeReserva.completar(resultados, null));
        }
        // R-E E-2 (T1-6): se re-ejecuta con los mismos filtros con que se guardó.
        List<BusquedaResponse> resultados = orquestador.buscar(usuario, texto(f, "texto_busqueda"),
                texto(f, "nombre"), texto(f, "filtro_materia"), texto(f, "filtro_nivel"));
        Object precio = f.get("precio_max_hora");
        return ResponseEntity.ok(datosDeReserva.completar(resultados,
                precio == null ? null : new java.math.BigDecimal(precio.toString())));
    }

    /** R-E E-2: borra una búsqueda guardada propia (204); ajena o inexistente, 404. */
    @org.springframework.web.bind.annotation.DeleteMapping("/guardadas/{id}")
    public ResponseEntity<Void> borrar(@PathVariable UUID id, Authentication authentication) {
        guardadasService.borrar(usuarioActual.obtener(authentication).getId(), id);
        return ResponseEntity.noContent().build();
    }

    private static java.util.Map<String, Object> filtros(BusquedaRequest request) {
        java.util.Map<String, Object> f = new java.util.LinkedHashMap<>();
        if (trimToNull(request.textoBusqueda()) != null) f.put("texto_busqueda", request.textoBusqueda().trim());
        if (trimToNull(request.nombre()) != null) f.put("nombre", request.nombre().trim());
        if (trimToNull(request.filtroMateria()) != null) f.put("filtro_materia", request.filtroMateria().trim());
        if (trimToNull(request.filtroNivel()) != null) f.put("filtro_nivel", request.filtroNivel().trim());
        if (request.precioMaxHora() != null) f.put("precio_max_hora", request.precioMaxHora());
        // Solo texto libre: es la búsqueda de siempre, sin filtros que guardar.
        return f.size() == 1 && f.containsKey("texto_busqueda") ? java.util.Map.of() : f;
    }

    private static String texto(java.util.Map<String, Object> f, String clave) {
        Object v = f.get(clave);
        return v == null ? null : trimToNull(v.toString());
    }

    /** Primer campo no vacío siguiendo el orden del contrato 2b: texto_busqueda
     * si viene, si no nombre, si no filtro_materia. Null si ninguno. */
    private static String textoEfectivo(BusquedaRequest request) {
        String texto = trimToNull(request.textoBusqueda());
        if (texto != null) return texto;
        String nombre = trimToNull(request.nombre());
        if (nombre != null) return nombre;
        return trimToNull(request.filtroMateria());
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}