# Tinku — Monorepo

Estructura: **monorepo**, decidido por tamaño de equipo (1 desarrollador) —
ver la conversación de diseño en `docs/Constitucion_Tinku.md`. Backend y
frontend se despliegan por separado desde subcarpetas (Coolify/VPS para
`backend` y `matching-service`, Vercel para `frontend`), pero viven en el
mismo repo para minimizar la fricción de sincronizar contratos de API
mientras cambian seguido.

```
tinku/
├── backend/            → Java + Spring Boot, monolito modular (9 bounded contexts)
├── matching-service/   → Python, único proceso separado del monolito (M2)
├── frontend/           → Next.js + React (PWA)
├── docs/               → Constitución, Specs, Planes técnicos y Tasks — LEER ANTES DE CODEAR
├── .github/workflows/  → CI, con path filters por proyecto
└── docker-compose.yml  → infraestructura local (Postgres 16 para desarrollo)
```

## Orden de lectura antes de tocar código

1. `docs/Constitucion_Tinku.md` — principios que no se negocian sin una enmienda formal.
2. `docs/Spec_M{N}_*.md` del módulo en el que vas a trabajar — el QUÉ.
3. `docs/Plan_M{N}_*.md` — el CÓMO, con el modelo de datos y los contratos de API ya pensados.
4. `docs/Tasks_Tinku_Implementacion.md` — la tarea concreta a marcar.

## Estado actual (ver Tasks_Tinku_Implementacion.md para el detalle)

- ✅ Fase 0 (scaffolding) completa.
- 🔄 M1 — Identidad y Perfiles: **US-1 (registro de Usuario adulto) implementada** con las 3 validaciones de OCR (coincidencia, edad, unicidad de DNI). Pendiente: alta de menor (T-M1-06), backoff de OCR/Credencial (T-M1-07/10), capacidades (T-M1-08), Tutor (T-M1-09).
- ⬜ M2 a M9 — sin implementar todavía, scaffolding de carpetas listo.

## ADRs pendientes que bloquean trabajo real (no placeholders)

- **ADR-M1-01** (proveedor de OCR real — hoy solo hay un `StubOcrService` para dev/test).
- **ADR-M3-01** (spike del clasificador on-device del kill-switch — el más urgente según la Constitución, Artículo XI).

## Cómo correr todo localmente

Ver el `README.md` de cada subcarpeta (`backend/`, `matching-service/`, `frontend/`).
