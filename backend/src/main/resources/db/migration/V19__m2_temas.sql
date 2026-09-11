-- M2-F — Catálogo granular de temas (trayectos + temas) y reemplazo del uso de
-- materias_niveles / materias_niveles_ids por tema_ids.
-- Ver docs/plan/Plan_M2_Temas.md (contratos 2a-2d).
--
-- Las tablas viejas (matching.materias_niveles y
-- matching.perfiles_tutor_matching.materias_niveles_ids) quedan sin uso por
-- código nuevo; V7 no se toca (AGENTS.md 7).
--
-- El SEED de los trayectos/temas NO vive acá: entra en V13__m2_temas_seed.sql
-- (ver contrato 2a del Plan_M2_Temas.md). Esta migración es el esqueleto.
SET search_path TO matching, public;

-- Catálogo granular: jerarquía nivel -> curso/año (o carrera) -> materia.
CREATE TABLE matching.trayectos (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nivel          TEXT NOT NULL CHECK (nivel IN ('primario', 'secundario', 'universitario')),
    anio_o_carrera TEXT NOT NULL,          -- '4°' (texto) o 'Ingeniería'
    materia        TEXT NOT NULL,
    CONSTRAINT uq_trayecto UNIQUE (nivel, anio_o_carrera, materia)
);

-- El TEMA es la unidad atómica: lleva la descripción en texto ("qué se toca")
-- que es la fuente del embedding del Tutor (contrato 2a).
CREATE TABLE matching.temas (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trayecto_id UUID NOT NULL REFERENCES matching.trayectos(id),
    nombre      TEXT NOT NULL,
    descripcion TEXT NOT NULL,             -- 'qué se toca'
    orden       INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tema UNIQUE (trayecto_id, nombre)
);

-- Elegidos por cada Tutor en su perfil (PUT /api/tutores/me/temas). Solo ids;
-- el embedding se repopula por el recompute del servicio Python (contrato 2c),
-- nunca en el PUT.
ALTER TABLE matching.perfiles_tutor_matching
    ADD COLUMN tema_ids UUID[] NOT NULL DEFAULT '{}';

-- GIN sobre el array: soporta `t.id = ANY(ptm.tema_ids)`, que usan los filtros
-- por nombre/materia de la búsqueda (contrato 2b). Filtro de candidatos por
-- búsqueda en un catálogo cerrado de ~1.400 temas: el índice es una línea y barato.
CREATE INDEX idx_perfiles_tutor_matching_tema_ids
    ON matching.perfiles_tutor_matching USING GIN (tema_ids);

-- unaccent se instala en public (search_path), mismo patrón que `vector` en V7.
-- Lo usa la búsqueda por nombre tolerante a tildes (contrato 2b). `public.`
-- calificado en las queries para resolver sin importar el search_path de la app.
CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA public;