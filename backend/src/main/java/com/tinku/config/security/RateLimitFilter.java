package com.tinku.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FASE2-02 / AUD-012 (decisión D8): límite de requests por IP en los endpoints
 * que no exigen JWT. Ventana deslizante de 1 minuto en memoria del proceso
 * (Tabla_Tiempos_Tinku.md, filas de rate limit): sin Redis, sin tabla, sin
 * dependencia nueva.
 *
 * <ul>
 *   <li>La clave es {@code request.getRemoteAddr()}, salvo que se configure
 *       {@code tinku.rate-limit.header-ip-cliente}: detrás de Cloudflare Tunnel todas las
 *       requests llegan desde cloudflared y la IP real viaja en {@code CF-Connecting-IP}
 *       (ADR-000-07). Solo se configura cuando el backend NO es alcanzable salvo por ese
 *       proxy; si no, cualquiera evade el límite mandando el header. Nunca se lee
 *       {@code X-Forwarded-For} por defecto.</li>
 *   <li>Un reinicio del proceso resetea los contadores: falla benigna, aceptada.</li>
 *   <li><strong>Instancia única</strong> (ADR-000-04): con 2+ réplicas cada una cuenta por
 *       su lado y el límite efectivo se multiplica. Deja de servir junto con el scheduler
 *       no clusterizado.</li>
 * </ul>
 * La limpieza de entradas vencidas es perezosa (al registrar requests), sin timers.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final Duration VENTANA = Duration.ofMinutes(1);

    private static final Set<String> VERIFICAR_DNI = Set.of(
            "/api/usuarios/verificar-dni", "/api/tutores/verificar-dni");
    private static final Set<String> PUBLICOS = Set.of(
            "/api/usuarios/registro", "/api/usuarios/login", "/api/tutores/registro",
            "/api/usuarios/recuperar-password", "/api/usuarios/resetear-password");
    private static final Set<String> WEBHOOKS = Set.of(
            "/api/webhooks/livekit", "/api/webhooks/mercadopago");

    private final int limiteVerificarDni;
    private final int limitePublicos;
    private final int limiteWebhooks;
    private final String headerIpCliente;
    private final Clock reloj;
    private final Map<String, Deque<Long>> ventanas = new ConcurrentHashMap<>();
    private volatile long ultimaLimpieza;

    public RateLimitFilter(int limiteVerificarDni, int limitePublicos, int limiteWebhooks,
                           String headerIpCliente, Clock reloj) {
        this.limiteVerificarDni = limiteVerificarDni;
        this.limitePublicos = limitePublicos;
        this.limiteWebhooks = limiteWebhooks;
        this.headerIpCliente = headerIpCliente == null ? "" : headerIpCliente.trim();
        this.reloj = reloj;
    }

    private String ipCliente(HttpServletRequest request) {
        if (!headerIpCliente.isEmpty()) {
            String ip = request.getHeader(headerIpCliente);
            if (ip != null && !ip.isBlank()) {
                return ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return grupo(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String grupo = grupo(request.getRequestURI());
        int limite = switch (grupo) {
            case "verificar-dni" -> limiteVerificarDni;
            case "webhooks" -> limiteWebhooks;
            default -> limitePublicos;
        };
        long ahora = reloj.millis();
        long desde = ahora - VENTANA.toMillis();
        limpiarSiCorresponde(ahora, desde);

        Deque<Long> ventana = ventanas.computeIfAbsent(grupo + "|" + ipCliente(request), k -> new ArrayDeque<>());
        long reintentarEnMs;
        synchronized (ventana) {
            while (!ventana.isEmpty() && ventana.peekFirst() <= desde) {
                ventana.pollFirst();
            }
            if (ventana.size() < limite) {
                ventana.addLast(ahora);
                reintentarEnMs = -1;
            } else {
                reintentarEnMs = ventana.peekFirst() + VENTANA.toMillis() - ahora;
            }
        }
        if (reintentarEnMs < 0) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, (reintentarEnMs + 999) / 1000)));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Demasiados intentos. Probá de nuevo en un minuto.\"}");
    }

    private static String grupo(String path) {
        if (VERIFICAR_DNI.contains(path)) return "verificar-dni";
        if (PUBLICOS.contains(path)) return "publicos";
        if (WEBHOOKS.contains(path)) return "webhooks";
        return null;
    }

    private void limpiarSiCorresponde(long ahora, long desde) {
        if (ahora - ultimaLimpieza < VENTANA.toMillis()) {
            return;
        }
        ultimaLimpieza = ahora;
        ventanas.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long ultimo = e.getValue().peekLast();
                return ultimo == null || ultimo <= desde;
            }
        });
    }
}
