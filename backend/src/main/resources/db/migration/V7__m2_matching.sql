-- M2 — Motor de Matching Semántico
-- Ver Plan_M2_Motor_Matching.md, sección 2 (modelo de datos lógico) y ADR-M2-01.
-- ADR-M2-01 (resuelto): el índice de embeddings vive en pgvector, dentro de
-- PostgreSQL, consultado por el servicio Python. La extensión se instala en el
-- schema `public` (default del search_path) para que el tipo `vector` siempre
-- resuelva, tanto para las inserciones del servicio Python como para consultas
-- de representantes. No se usa una columna vectorial de texto: `vector(384)`
-- es la dimensionalidad de los modelos sentence-transformers del Plan (sección 3).

CREATE EXTENSION IF NOT EXISTS vector SCHEMA public;

-- `public` queda en el search_path para que el tipo `vector` (extensión en
-- `public`) siempre resuelva dentro de esta migración y en las tablas matching.
SET search_path TO matching, public;

-- FR-MATCH-006: catálogo cerrado y curado de materias/niveles basado en los
-- niveles educativos oficiales de Argentina (primario desde los 6 años).
-- T-M2-01: se siembra la versión inicial; la curaduría es de operaciones.
CREATE TABLE matching.materias_niveles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nivel               VARCHAR(20) NOT NULL CHECK (nivel IN ('primario', 'secundario', 'universitario')),
    materia             VARCHAR(100) NOT NULL,
    CONSTRAINT uq_materia_nivel UNIQUE (nivel, materia)
);

CREATE INDEX idx_materias_niveles_nivel ON matching.materias_niveles(nivel);

-- Extensión del Tutor para matching (M1 crea el Tutor; esto lo completa para M2).
-- embedding: pgvector (ADR-M2-01); NULL hasta que el servicio Python lo computes
-- y lo persista. activo_para_matching: false mientras la Credencial no esté
-- aprobada (M1) o el Tutor esté suspendido (M9) — misma semántica que M1 usa
-- para el CAP (FR-ID-025).
CREATE TABLE matching.perfiles_tutor_matching (
    tutor_id                UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    materias_niveles_ids    UUID[] NOT NULL DEFAULT '{}',
    embedding               VECTOR(384),
    activo_para_matching    BOOLEAN NOT NULL DEFAULT FALSE
);

-- FR-MATCH-008: búsquedas guardadas re-ejecutables (resultados actualizados,
-- nunca una lista congelada).
CREATE TABLE matching.busquedas_guardadas (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES identidad.usuarios(id),
    texto_busqueda  VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_busquedas_guardadas_usuario ON matching.busquedas_guardadas(usuario_id);

-- T-M2-01: carga inicial del catálogo cerrado de materias/niveles.
-- Niveles educativos oficiales de Argentina (NAP primario/secundario, materias
-- típicas de tutoría universitaria).
INSERT INTO matching.materias_niveles (nivel, materia) VALUES
    -- Nivel primario (desde los 6 años, NAP)
    ('primario', 'Lengua'),
    ('primario', 'Matemática'),
    ('primario', 'Ciencias Sociales'),
    ('primario', 'Ciencias Naturales'),
    ('primario', 'Formación Ética y Ciudadana'),
    ('primario', 'Inglés'),
    ('primario', 'Educación Física'),
    ('primario', 'Educación Artística'),
    ('primario', 'Tecnología'),
    -- Nivel secundario (ciclo básico y orientado)
    ('secundario', 'Lengua y Literatura'),
    ('secundario', 'Matemática'),
    ('secundario', 'Historia'),
    ('secundario', 'Geografía'),
    ('secundario', 'Biología'),
    ('secundario', 'Física'),
    ('secundario', 'Química'),
    ('secundario', 'Inglés'),
    ('secundario', 'Formación Ética y Ciudadana'),
    ('secundario', 'Filosofía'),
    ('secundario', 'Economía'),
    ('secundario', 'Informática'),
    ('secundario', 'Arte'),
    ('secundario', 'Sociología'),
    ('secundario', 'Psicología'),
    -- Nivel universitario (materias típicas de tutoría universitaria)
    ('universitario', 'Análisis Matemático'),
    ('universitario', 'Álgebra Lineal'),
    ('universitario', 'Probabilidad y Estadística'),
    ('universitario', 'Programación'),
    ('universitario', 'Estructura de Datos'),
    ('universitario', 'Física General'),
    ('universitario', 'Química General'),
    ('universitario', 'Química Orgánica'),
    ('universitario', 'Anatomía'),
    ('universitario', 'Fisiología'),
    ('universitario', 'Derecho Civil'),
    ('universitario', 'Derecho Penal'),
    ('universitario', 'Contabilidad Básica'),
    ('universitario', 'Costos'),
    ('universitario', 'Microeconomía'),
    ('universitario', 'Macroeconomía'),
    ('universitario', 'Econometría'),
    ('universitario', 'Finanzas'),
    ('universitario', 'Administración General'),
    ('universitario', 'Cálculo');