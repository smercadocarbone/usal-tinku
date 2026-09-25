package com.tinku.admin.web;

import com.tinku.admin.model.Admin;
import com.tinku.admin.service.AuditoriaAdminService;
import com.tinku.admin.AdminModeracionGate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Auditoría transversal de T-M8-02: registra en {@code log_auditoria_admin} TODA
 * request que termina en 2xx sobre {@code /api/admin/**} (cola, resolución, CAP,
 * tickets, ambas colas de rol) — se registró en el {@code WebMvcConfigurer} del
 * módulo con {@code addInterceptor(...).addPathPatterns("/api/admin/**")}.
 *
 * Se audita en {@code afterCompletion} SOLO cuando el status es 2xx: un 403 del
 * propio gate (o un 401 de la cadena de filtros) es un intento, no una acción de
 * Admin, y no se atribuye a nadie. Si el principal no resuelve a un {@code Admin}
 * (endpoint que todavía no exige rol, como los de CAP), no hay fila de auditoría.
 *
 * La escritura es en transacción propia y degrada a log sin romper el response
 * (Ver {@code AuditoriaAdminService}).
 */
@Component
public class AuditoriaInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaInterceptor.class);

    private static final Pattern PREFIJO_ADMIN = Pattern.compile("^/api/admin/([^/]+)(?:/([^/]+))?.*");
    private static final Pattern UUID_SEGMENTO = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final AdminModeracionGate gate;
    private final AuditoriaAdminService auditoria;

    public AuditoriaInterceptor(AdminModeracionGate gate, AuditoriaAdminService auditoria) {
        this.gate = gate;
        this.auditoria = auditoria;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        int status = response.getStatus();
        if (status < 200 || status >= 300 || !(handler instanceof HandlerMethod)) {
            return;
        }
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            Admin admin = gate.adminAutenticado(authentication);
            String path = request.getRequestURI();
            auditoria.registrar(admin, request.getMethod() + " " + path,
                    entidadTipo(path), entidadId(path), detalle(request));
        } catch (RuntimeException e) {
            // Sin fila de admin para el principal → no es una acción de Admin:
            // se omite, nunca un error de auditoría puede romper un 2xx ya emitido.
            log.debug("Request a /api/admin/** sin Admin resuelto — sin auditoría: {}", e.getMessage());
        }
    }

    /** Segunda parte de la ruta ({@code moderacion}/credenciales, {@code financiero}/pagos-fallidos, etc.). */
    private static String entidadTipo(String path) {
        var m = PREFIJO_ADMIN.matcher(path);
        return m.matches() && m.group(2) != null ? m.group(2) : "admin";
    }

    /** Primer segmento UUID de la ruta (el id del caso), si lo hay. */
    private static String entidadId(String path) {
        for (String segmento : path.split("/")) {
            if (UUID_SEGMENTO.matcher(segmento).matches()) {
                return segmento;
            }
        }
        return null;
    }

    private static Map<String, String> detalle(HttpServletRequest request) {
        return Map.of("metodo", request.getMethod(), "path", request.getRequestURI());
    }
}