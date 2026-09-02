-- M1 — Gestión de Identidad y Perfiles
-- Ver Plan_M1_Identidad_Perfiles.md, sección 1.

SET search_path TO identidad;

CREATE TABLE identidad.usuarios (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dni                             VARCHAR(20) NOT NULL UNIQUE,
    nombre                          VARCHAR(150) NOT NULL,
    apellido                        VARCHAR(150) NOT NULL,
    fecha_nacimiento                DATE NOT NULL,
    tipo                            VARCHAR(20) NOT NULL CHECK (tipo IN ('ADULTO', 'MENOR', 'TUTOR')),
    capacidad_estudiante            BOOLEAN NOT NULL DEFAULT FALSE,
    capacidad_adulto_responsable    BOOLEAN NOT NULL DEFAULT FALSE,
    adulto_responsable_id           UUID REFERENCES identidad.usuarios(id),
    password_hash                   VARCHAR(255) NOT NULL,
    estado_cuenta                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVA' CHECK (estado_cuenta IN ('ACTIVA', 'SUSPENDIDA')),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- FR-ID-001: si tipo = ADULTO, al menos una capacidad debe estar activa.
    CONSTRAINT chk_adulto_tiene_capacidad CHECK (
        tipo != 'ADULTO' OR (capacidad_estudiante OR capacidad_adulto_responsable)
    ),
    -- adulto_responsable_id solo tiene sentido para tipo = MENOR.
    CONSTRAINT chk_menor_tiene_responsable CHECK (
        (tipo = 'MENOR' AND adulto_responsable_id IS NOT NULL) OR
        (tipo != 'MENOR' AND adulto_responsable_id IS NULL)
    )
);

CREATE INDEX idx_usuarios_adulto_responsable ON identidad.usuarios(adulto_responsable_id);
CREATE INDEX idx_usuarios_tipo ON identidad.usuarios(tipo);

CREATE TABLE identidad.credenciales_academicas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id            UUID NOT NULL REFERENCES identidad.usuarios(id),
    tipo_documento      VARCHAR(30) NOT NULL CHECK (tipo_documento IN ('TITULO', 'CERTIFICADO_ANALITICO', 'MATRICULA')),
    archivo_url         VARCHAR(500) NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    numero_intento      INT NOT NULL DEFAULT 1 CHECK (numero_intento BETWEEN 1 AND 3),
    ciclo_espera_hasta  TIMESTAMPTZ,
    admin_revisor_id    UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    revisado_at         TIMESTAMPTZ
);

CREATE INDEX idx_credenciales_tutor ON identidad.credenciales_academicas(tutor_id);
CREATE INDEX idx_credenciales_estado ON identidad.credenciales_academicas(estado);

CREATE TABLE identidad.autorizaciones_tutor (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adulto_responsable_id       UUID NOT NULL REFERENCES identidad.usuarios(id),
    menor_id                    UUID NOT NULL REFERENCES identidad.usuarios(id),
    tutor_id                    UUID NOT NULL REFERENCES identidad.usuarios(id),
    no_confiable                BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_autorizacion UNIQUE (adulto_responsable_id, menor_id, tutor_id)
);

CREATE TABLE identidad.consentimientos_menor (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menor_id                    UUID NOT NULL REFERENCES identidad.usuarios(id),
    adulto_responsable_id       UUID NOT NULL REFERENCES identidad.usuarios(id),
    version_texto               VARCHAR(50) NOT NULL,
    aceptado_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    revocado_at                 TIMESTAMPTZ
);

CREATE INDEX idx_consentimientos_menor ON identidad.consentimientos_menor(menor_id);
