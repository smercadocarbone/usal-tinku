-- M6 — Resumen Automatico de la Sesion
-- Ver Plan_M6_Resumen_Automatico.md seccion 1 (modelo de datos logico). El schema
-- `resumen` ya existe (V1). El proveedor LLM (GPT-4o vs Gemini 2.0 Flash) esta
-- PENDIENTE de ADR (Constitucion, T-FIN-03): el puerto ResumenProveedor es
-- fail-closed hasta esa decision — esta migracion es solo estructura local.

SET search_path TO resumen, public;

-- FR-SUM-001/006/007/008: un resumen por sesion, no regenerable (FR-SUM-006).
-- `sesion_id` UNIQUE + FK a aula.sesiones_aprendizaje previene una regeneracion
-- accidental por un reintento mal manejado (Plan §1). Solo nace una fila para
-- sesiones con duracion efectiva >= 10 min (FR-SUM-001, Tabla_Tiempos): si M3 la
-- interrumpio (<50%) o hubo kill-switch jamas se emite `sesion.finalizada` y no
-- hay fila (caso borde #1 del Spec).
-- `transcript_anonimizado`/`prompt_anonimizado` guardan la data YA anonimizada
-- (FR-SUM-005); la anonimizacion se persiste siempre, aun sin proveedor
-- configurado. `estado` es la maquina unica del resumen.
CREATE TABLE resumen.resumenes_sesion (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id              UUID NOT NULL UNIQUE REFERENCES aula.sesiones_aprendizaje(id),
    estado                 VARCHAR(30) NOT NULL DEFAULT 'pendiente'
                           CHECK (estado IN ('pendiente', 'generado', 'suspendido_seguridad',
                                             'reintento_agotado', 'fallido')),
    prompt_anonimizado     TEXT,             -- prompt final armado (ya anonimizado)
    transcript_anonimizado TEXT,             -- transcript anonimizado — se persiste SIEMPRE (T-M6-04)
    resumen_final          TEXT,             -- salida del LLM (null hasta generar)
    intentos               INT NOT NULL DEFAULT 0 CHECK (intentos >= 0),
    proximo_reintento_at   TIMESTAMPTZ,      -- backoff 5/15/1h — mismo patron que M5 (T-M6-06)
    recordatorio_pendiente BOOLEAN NOT NULL DEFAULT FALSE, -- recordatorio unico 24hs (T-M6-07)
    suspendido_seguridad   BOOLEAN NOT NULL DEFAULT FALSE, -- denuncia/alerta activa (T-M6-03)
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumenes_sesion_estado ON resumen.resumenes_sesion(estado);