# Plan Técnico: M6 — Resumen Automático de la Sesión

**Basado en:** Spec_M6_Resumen_Automatico.md (aprobado)
**Stack (Constitución, Registro de Decisiones):** un único proveedor LLM (GPT-4o o Gemini 2.0 Flash, ADR pendiente) recibe el audio directamente — **Whisper queda fuera del stack**, decisión ya tomada en la sesión de arquitectura: transcripción y resumen se resuelven en una sola llamada.

---

## 1. Modelo de Datos (lógico)

### `resumenes_sesion`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| sesion_id | UUID (FK → sesiones_aprendizaje.id), UNIQUE | Uno por sesión, no regenerable (FR-SUM-006) — la unicidad a nivel de constraint de base de datos previene una regeneración accidental por un reintento mal manejado. |
| estado | enum(`pendiente`, `generado`, `fallido`, `suspendido_seguridad`) | `suspendido_seguridad` si la sesión tuvo una Alerta de M3/M9 (FR-SUM-008). |
| contenido_json | jsonb | Estructura fija: temas, conceptos clave, ejercicios, dudas abiertas, sugerencia. |
| intentos_generacion | int, default 0 | Máximo 3 antes de `fallido` definitivo. |
| generado_at | timestamp, nullable | |

## 2. Flujo Técnico

1. Al recibir `sesion.finalizada` (de M3), valida `duracion_efectiva_segundos ≥ 600` (10 min, Tabla de Tiempos). Si no, no crea ni siquiera la fila `pendiente` — la sesión simplemente no tiene resumen.
2. Valida que no exista una Denuncia activa sobre esa sesión (consulta a M9, FR-SEC-003) ni una Alerta de Seguridad (M3) — si existe cualquiera de las dos, crea la fila con `estado = suspendido_seguridad` y no continúa.
3. Obtiene la transcripción de audio/video (y de los tramos de chat si hubo degradación) desde el storage de M3.
4. **Paso de anonimización (FR-SUM-005), ejecutado antes de armar el prompt:** un filtro basado en expresiones regulares + un modelo liviano de reconocimiento de entidades (NER) reemplaza nombres propios, números de teléfono, emails, y URLs por marcadores genéricos (`[nombre]`, `[contacto]`). Este paso corre **en el backend, antes de que nada salga hacia el proveedor de LLM** — nunca se envía el transcript crudo a un tercero.
5. Envía el transcript anonimizado al LLM elegido (audio o texto, según el proveedor final) con un prompt que fuerza la estructura fija de salida (temas, conceptos clave, ejercicios, dudas, sugerencia) y explícitamente instruye no evaluar a las personas ni hacer predicciones de desempeño (FR-SUM-008).
6. Si la llamada falla: reintento con backoff, hasta 3 veces (mismo patrón que M5, por consistencia — Artículo I de la Constitución: reutilizar el mismo patrón de reintentos en todo el sistema en vez de inventar uno distinto por módulo).
7. Al persistir el resultado, dispara la notificación de disponibilidad al Estudiante/Tutor (y Adulto Responsable si corresponde), y programa el job de recordatorio a las 24hs (FR-SUM-004, ya definido como requisito en el Spec).

## 3. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `GET` | `/api/sesiones/{id}/resumen` | Devuelve `pendiente`/`generado`/`fallido`/`suspendido_seguridad` según corresponda — el frontend debe manejar los cuatro estados, no asumir que siempre hay contenido. |

*(No hay endpoint de escritura expuesto — la generación es enteramente disparada por el evento interno, nunca por una llamada directa del cliente, precisamente para que no se pueda forzar una regeneración desde el frontend.)*

## 4. ADRs de este Módulo

- **Depende de ADR pendiente en la Constitución:** elección final entre GPT-4o y Gemini 2.0 Flash — este Plan asume que el proveedor elegido acepta audio como input directo; si el benchmark final decide lo contrario, este módulo necesitaría reincorporar un paso de transcripción separado (revisar antes de implementar si ese ADR todavía no se cerró).
- **ADR-M6-01:** biblioteca/modelo de NER para la anonimización (paso 4). Candidatos livianos existen tanto en español específicamente entrenados como genéricos multilenguaje — evaluar precisión sobre nombres hispanos antes de comprometerse, dado que es la pieza que más directamente protege datos personales de menores.

## 5. Trazabilidad con el Spec

FR-SUM-001 a 008 cubiertos. El paso 4 (anonimización previa al envío externo) es el más importante desde la perspectiva de la Constitución (Artículo V, minimización de datos) — cualquier cambio futuro al pipeline debe mantener este paso como no-opcional y anterior a cualquier llamada de red saliente.

---

**Estado: Borrador de Plan técnico.**
