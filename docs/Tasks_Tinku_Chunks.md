# Tasks por Chunk — Tinku

> Base para agrupar trabajo por chunk (ver AGENTS.md, sección 6). Cada chunk
> se tilda solo cuando todas sus tareas están implementadas y verificadas.
> La memoria persistente entre sesiones es `Tasks_Tinku_Implementacion.md`;
> este archivo solo refleja el agrupamiento por chunk y su estado.

## Fase 0 — Setup General

- [x] **Chunk 000-A** — Setup base:
  - T-000-01: Proyecto Spring Boot con paquetes por módulo (9 bounded contexts + config + shared).
  - T-000-02: PostgreSQL + 9 schemas por módulo vía Flyway (`V1__crear_schemas.sql`).
  - Docker Compose para Postgres local (`docker-compose.yml` en la raíz del repo).
  - Verificado: app compila y se conecta a Postgres; `\dn` muestra los 9 schemas.
- [x] **Chunk 000-B** — T-000-03 a T-000-05 (Quartz persistido, eventos en memoria, Security+JWT).
- [ ] **Chunk 000-C** — T-000-06 a T-000-09 (cuentas LiveKit/MP, matching-service, CI).
