-- Un solo motor PostgreSQL, un schema por modulo (Constitucion, Articulo VIII).
-- Los limites de dominio entre modulos se refuerzan aca, no solo en el codigo Java.

CREATE SCHEMA IF NOT EXISTS identidad;   -- M1
CREATE SCHEMA IF NOT EXISTS matching;    -- M2 (solo catalogo/cache; el indice vive en pgvector o en el proceso Python, ver ADR-M2-01)
CREATE SCHEMA IF NOT EXISTS aula;        -- M3
CREATE SCHEMA IF NOT EXISTS reservas;    -- M4
CREATE SCHEMA IF NOT EXISTS pagos;       -- M5
CREATE SCHEMA IF NOT EXISTS resumen;     -- M6
CREATE SCHEMA IF NOT EXISTS reputacion;  -- M7
CREATE SCHEMA IF NOT EXISTS admin;       -- M8
CREATE SCHEMA IF NOT EXISTS seguridad;   -- M9

-- Extension necesaria para UUID como PK en todas las tablas (ver Planes tecnicos).
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
