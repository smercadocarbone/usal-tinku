# Plan Técnico: M2 — Motor de Matching Semántico

**Basado en:** Spec_M2_Motor_Matching.md (aprobado)
**Stack (Constitución):** Backend Java + Spring Boot llama internamente a un **proceso Python separado** (única excepción al monolito modular, Artículo VIII) para embeddings + similitud semántica.

---

## 1. Arquitectura del Componente

```
Cliente → Backend (Java) → [llamada interna HTTP/gRPC, red privada] → Servicio de Matching (Python)
                                                                              │
                                                                     sentence-transformers
                                                                     + índice FAISS
```

El servicio Python **no tiene su propia base de datos de negocio** — es stateless respecto a Reservas/Usuarios. Solo mantiene: (a) el índice FAISS de embeddings de perfiles de Tutor, y (b) el catálogo cerrado de materias/niveles como vector de referencia. El filtrado por autorización, exclusión de suspendidos, etc. ocurre **en el backend Java, antes de llamar al servicio de matching** — nunca al revés (evita que el servicio de matching necesite conocer reglas de negocio de otros módulos).

## 2. Modelo de Datos (lógico)

### `materias_niveles` (catálogo cerrado, FR-MATCH-006)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| nivel | enum(`primario`, `secundario`, `universitario`) | |
| materia | varchar | Curada por el equipo de operaciones (mismo criterio que BR-ID-01 de M1). |

### `perfiles_tutor_matching` (extensión del Tutor para esta función)
| Campo | Tipo | Notas |
|---|---|---|
| tutor_id | UUID (FK → usuarios.id) | |
| materias_niveles_ids | array de UUID | Referencia al catálogo cerrado. |
| embedding_vector | vector (pgvector o almacenado en el índice FAISS del servicio Python, no en Postgres) | Ver ADR-M2-01. |
| activo_para_matching | boolean | `false` mientras la Credencial no esté aprobada (M1) o esté suspendido (M9). |

### `busquedas_guardadas` (FR-MATCH-008)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| usuario_id | UUID (FK) | Quién la guardó (Estudiante o el perfil de menor). |
| texto_busqueda | varchar | Se re-ejecuta contra el índice vigente, no devuelve una lista congelada. |
| created_at | timestamp | |

## 3. Flujo de Búsqueda (paso a paso)

1. Backend recibe la búsqueda (texto libre + filtros de materia/nivel).
2. Backend resuelve el **contexto de autorización**, ANTES de llamar al servicio de matching:
   - Si la cuenta que busca es un `menor`: obtiene la lista de `autorizaciones_tutor` de su `adulto_responsable_id` (excluyendo `no_confiable = true`).
   - Si es un adulto (Estudiante o buscando con capacidad Adulto Responsable): sin restricción de lista.
3. Backend obtiene la lista de Tutores **excluidos** por Alerta de Seguridad activa (consulta a M9) — se descartan **antes** de llamar al servicio de matching (FR-MATCH-007, resuelve E-05/eficiencia).
4. Backend llama al servicio de Matching con: texto de búsqueda + el **conjunto acotado de tutor_ids candidatos** (no todo el universo) cuando aplica restricción de autorización — para un menor, esto es una lista corta; para un adulto sin restricción, se envía el universo completo de tutores activos.
5. El servicio de Matching calcula similitud semántica sobre ese conjunto y devuelve un ranking.
6. Backend aplica el ajuste final por señales implícitas de reputación (FR-MATCH-003) y por la sombra temporal de BR-MATCH-01 (Tutor con 1-2 estrellas recientes, consulta a M7) — este reordenamiento final también vive en Java, no en el servicio Python, porque son reglas de negocio, no de similitud semántica.
7. Si la cuenta es un menor y hay Tutores relevantes fuera de su lista de autorización, el backend igual los incluye en la respuesta marcados como `no_autorizado: true`, para que el frontend muestre el botón "Solicitar autorización" (FR-MATCH-005).

## 4. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/busquedas` | Body: texto libre + filtros. Devuelve resultados ya combinados (semántica + reputación + autorización). |
| `POST` | `/api/busquedas/guardadas` | Guarda una búsqueda para re-ejecutar después. |
| `GET` | `/api/busquedas/guardadas` | Lista las guardadas del usuario. |
| `POST` | `/api/busquedas/guardadas/{id}/ejecutar` | Re-ejecuta contra el índice vigente. |
| *(interno, no expuesto a internet)* | `POST /match` en el servicio Python | Recibe texto + lista acotada de candidatos, devuelve ranking. |

## 5. ADRs de este Módulo

- **ADR-M2-01:** ¿Dónde vive el índice de embeddings — en el propio proceso Python (en memoria + snapshot a disco) o en `pgvector` dentro de PostgreSQL, consultado por el servicio Python? La segunda opción simplifica la persistencia (no hay que reconstruir el índice al reiniciar el proceso) a costa de un poco más de latencia por ida y vuelta a la base. Dado el volumen esperado del piloto, `pgvector` es probablemente suficiente y más simple de operar (Artículo I) — pero queda como decisión a confirmar con el desarrollador antes de implementar.
- **ADR-M2-02:** Fórmula exacta de ponderación entre similitud semántica y señales implícitas de reputación (qué peso relativo tiene cada una en el ranking final). El Spec deja esto fuera de alcance a propósito (es HOW, no WHAT) — se resuelve acá, empíricamente, ajustando con datos reales del piloto, no con un número fijo de entrada.

## 6. Trazabilidad con el Spec

FR-MATCH-001 a 009 cubiertos. La sombra temporal de BR-MATCH-01 y el filtro de suspendidos (FR-MATCH-007) requieren que el backend consulte a M7 y M9 respectivamente **antes** de construir la lista de candidatos — este acoplamiento ya estaba declarado en el header de dependencias del Spec, y se refleja acá como llamadas internas síncronas (Artículo IX de la Constitución).

---

**Estado: Borrador de Plan técnico.** Pendiente: ADR-M2-01 antes de implementar la persistencia del índice.
