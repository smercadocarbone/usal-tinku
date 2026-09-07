-- M1 — Certificado de Antecedentes Penales (CAP) de Tutor (US-6) — T-M1-14
-- Ver Plan_M1_Identidad_Perfiles.md sección 2.4 y Spec_M1 sección 4 (FR-ID-021..025).
--
-- NOTAS de decisión:
--  * `usuarios.activo_para_matching` se agrega AQUI (columna nueva, no se edita
--    V2 — AGENTS.md §7). Es el mismo flag que M2/M9 usan para suspender Tutores
--    del matching (Plan_M2/M9): el job de vencimiento (FR-ID-025) lo apaga, y la
--    aprobación de CAP lo enciende. M2 lo lee para excluir Tutores suspendidos.
--  * Tabla `certificados_antecedentes_penales`: estado enum con
--    `en_revision_legal` (BR-CAP-02) que NUNCA se auto-resuelve (decisión manual
--    documentada del Admin). `numero_intento`/`ciclo_espera_hasta` igual patrón
--    que credenciales (FR-ID-021 reutiliza FR-ID-012) — el backoff en sí vive en
--    la tabla `intentos_credencial` reutilizada por `CredencialBackoffService`.

ALTER TABLE identidad.usuarios
    ADD COLUMN activo_para_matching BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE identidad.certificados_antecedentes_penales (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id                UUID NOT NULL REFERENCES identidad.usuarios(id),
    archivo_url             VARCHAR(500) NOT NULL,
    fecha_emision           DATE NOT NULL,
    vence_at                DATE NOT NULL,
    estado                  VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'
        CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'EN_REVISION_LEGAL', 'VENCIDO')),
    tiene_antecedentes      BOOLEAN NOT NULL DEFAULT FALSE,
    categoria_antecedente   VARCHAR(255),
    numero_intento          INT NOT NULL DEFAULT 1 CHECK (numero_intento BETWEEN 1 AND 3),
    ciclo_espera_hasta      TIMESTAMPTZ,
    admin_revisor_id        UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    revisado_at             TIMESTAMPTZ
);

CREATE INDEX idx_cap_tutor ON identidad.certificados_antecedentes_penales(tutor_id);
CREATE INDEX idx_cap_estado_vence ON identidad.certificados_antecedentes_penales(estado, vence_at);
