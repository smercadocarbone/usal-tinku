# Propuesta de enmienda v2.5 → v2.6 — NFR de disponibilidad (sin piloto)

**Estado:** propuesta, **no aplicada**. La aplica el dueño (o un agente con su visto bueno explícito),
como enmienda formal y versionada (Constitución, encabezado y Artículo XII). Escrita el 2026-10-18.

## Motivo

El 2026-10-18 el dueño decidió que el piloto con usuarios reales (27/10/2026) **no se hace**:
`tinku.site` queda como demo pública de pruebas y las clases con menores se habilitan sin piloto
(`docs/superpowers/specs/m3c/M3C-09-habilitar-menores.md`). El NFR de disponibilidad está atado a
"durante el piloto", una etapa que ya no existe, así que hoy no dice cuándo rige.

## Cambio propuesto

`docs/Constitucion_Tinku.md`, sección "NFRs no negociables":

| | Texto |
|---|---|
| **Actual** | `- **Disponibilidad:** 95% mensual durante el piloto.` |
| **Propuesto (opción A, recomendada)** | `- **Disponibilidad:** 95% mensual mientras Tinku corra en un único servidor (ADR-000-07); se revisa al separar la infraestructura o al crecer la operación.` |
| Propuesto (opción B) | `- **Disponibilidad:** 95% mensual.` |

La opción A conserva el número y lo ata a la condición que lo justifica (un servidor hogareño, riesgo
aceptado en ADR-000-07). La opción B lo vuelve permanente. Ninguna de las dos sube el número.

## Qué más cambia en el mismo commit

- Encabezado de la Constitución: `**Versión:** 2.5` → `2.6`.
- "Historial de Enmiendas": entrada nueva `### Enmienda v2.5 → v2.6 — NFR de disponibilidad (sin
  piloto)`, con **Motivo** (el de arriba), **Cambia:** NFR de disponibilidad, **No cambia:** los
  Artículos, el Registro de Decisiones Técnicas y los demás NFRs.
- `AGENTS.md` §8: "Este archivo refleja la Constitución v2.5" → "v2.6" (AGENTS §8 exige que vaya en
  el mismo commit que la enmienda).

## Qué no cambia

- La línea 116 ("una etapa de piloto donde…") es el motivo histórico de la enmienda v2.1 → v2.2:
  queda como está.
- Si la tesis cita este NFR (p. ej. un RNF de disponibilidad), su redacción la decide el trabajo de
  evaluación de la tesis, no esta propuesta.
