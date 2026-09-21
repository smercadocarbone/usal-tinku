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

## Estado actual (ver `docs/Tasks_Tinku_Implementacion.md` para el detalle atómico — es la única fuente de verdad confiable sobre qué está hecho; este README y `Tasks_Tinku_Chunks.md` son un resumen y pueden quedar desactualizados entre revisiones)

- ✅ Fase 0 (scaffolding) completa.
- ✅ **M1 a M9 tienen código de producción en `main`**, no scaffolding: los 9 paquetes de dominio (`identidad`, `matching`, `aula`, `reservas`, `pagos`, `resumen`, `reputacion`, `admin`, `seguridad`) existen con lógica real. ~320 tests de integración/unitarios verdes, incluidos el E2E del flujo feliz completo (T-FIN-01) y el E2E de la rama de seguridad completa — sesión con menor → kill-switch → suspensión → Alerta → resolución del Admin → efectos propagados a M1/M2/M4/M5 (T-FIN-02).
- 🔴 **Pendiente crítico de seguridad (T-M3-06):** el clasificador NSFW on-device del kill-switch (spike y ADR-M3-01 ya cerrados) **no está integrado en el cliente real** (`frontend/src/app/aula/[id]/page.tsx` tiene LiveKit funcionando pero ninguna dependencia de NSFWJS/TensorFlow.js). El backend del kill-switch (endpoint, ramas menor/adultos, evidencia, eventos) está completo — falta la mitad que corre en el navegador y dispara el corte. No debería haber una sesión real con un menor presente hasta que esto esté cerrado.
- 🟡 Pendiente de UX, no bloqueante de seguridad: integración del `DynamicTimeSlotPicker` en `/reservar` (T-M4-12 a T-M4-15) — el componente existe pero el backend no expone el endpoint de horarios que necesita.
- 🟡 ADR pendiente real: proveedor de LLM para M6 (GPT-4o vs. Gemini 2.0 Flash) — el código ya aisló esto detrás de un puerto fail-closed (`ResumenProveedor`), así que no bloquea el resto del sistema, pero M6 no genera resúmenes reales hasta que se cierre.
- 🟡 CI solo cubre `backend/` (`.github/workflows/ci-backend.yml`, con path filter); no hay pipeline para `frontend/` ni `matching-service/` todavía.

## ADRs cerrados relevantes (dejaron de bloquear)

- **ADR-M1-01** (OCR real: Tesseract/Tess4J in-process) — cerrado 2026-09-04.
- **ADR-M2-01** (índice de matching: pgvector) — cerrado.
- **ADR-M3-01** (clasificador on-device del kill-switch: NSFWJS + TensorFlow.js) — cerrado 2026-09-08. Cerrado el spike, pendiente la integración real (ver arriba, T-M3-06).

## Cómo correr todo localmente

Ver el `README.md` de cada subcarpeta (`backend/`, `matching-service/`, `frontend/`).
