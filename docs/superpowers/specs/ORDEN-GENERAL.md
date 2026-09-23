# Orden general de ejecución — remediación + UX + tesis

Hay tres paquetes de specs, todos para ejecutar con opencode y con el mismo protocolo:

| Paquete | Carpeta | LEEME |
|---|---|---|
| Remediación de la auditoría (FASE 2-4) | `specs/remediacion/` | `00-LEEME-opencode.md` |
| Rediseño UX/UI | `specs/ux/` | `00-LEEME-ux.md` |
| Decisiones de la tesis | `specs/tesis/` | `00-LEEME-tesis.md` |

**Fecha que manda: piloto el 27/10/2026, sin menores (DT7).** El orden de abajo prioriza lo que el
piloto necesita (dinero, seguridad, que los flujos funcionen) y deja para después lo que solo aplica
con menores. Las olas se ejecutan en orden; **dentro** de una ola, las specs sin dependencias entre sí
pueden ir en paralelo (en branches distintos).

## Ola 0 — Arrancar ya (no dependen de ninguna decisión pendiente)

| Spec | Por qué ahora |
|---|---|
| `ux/02-bugs-y-deuda-ux.md` | **Nadie puede registrarse como tutor desde la UI** y los errores de validación salen como 403. Sin esto no hay piloto. |
| `remediacion/FASE2-10-colision-alerta-denuncia.md` | Dinero: un tutor bajo investigación puede cobrar antes de que se revise la Alerta. |
| `remediacion/FASE2-05-webhook-desconexion.md` | Dinero: se libera el escrow por clases de 5 minutos. |
| `tesis/T05-comision-27.md` | Dinero: comisión del piloto. |
| `tesis/T10-gate-menores-piloto.md` | El piloto no puede aceptar sesiones con menores. |
| `remediacion/FASE2-04-matching-auth-pool.md` | `matching-service` sin autenticación. Chica, sin decisiones (salvo el ADR del pool). |

## Ola 1 — Necesita decisiones: P1, P2, P4, P5, PT3, PT4

| Spec | Decisión |
|---|---|
| `remediacion/FASE2-02-rate-limiting.md` | P4 |
| `remediacion/FASE2-01-disponibilidad-bloques-30.md` (**riesgo alto**) | P1 |
| `tesis/T06-piso-tarifa.md` | depende de FASE2-01 · PT3, PT4 |
| `remediacion/FASE2-07-modo-bypass.md` | P2 |
| `remediacion/FASE2-03-notificaciones-outbox.md` | P5 solo para el email |
| `remediacion/FASE3-03-jwt-uuid-credentials-version.md` (**riesgo alto**) | — Hoy el JWT expone el DNI de cada usuario. Idealmente antes del piloto; si no entra, primera tarea después. |

## Ola 2 — Resumen pago y medición del piloto: PT5 a PT9

`tesis/T07-proveedor-llm.md` → `tesis/T08-grabacion-audio-resumen.md` (**riesgo alto**, privacidad) →
`tesis/T09-adicional-resumen.md` · `tesis/T11-instrumentacion-piloto.md` · `tesis/T12-ux-recomendaciones.md`
(coordinar con `ux/04`).

## Ola 3 — Rediseño UX (lo que entre antes del piloto): U1, U2

`ux/01-sistema-visual.md` primero, y después, por impacto en el piloto: `ux/04` (embudo de reserva,
depende de FASE2-01) → `ux/06` (tutor) → `ux/03` (público) → `ux/05` (cuenta) → `ux/08` (admin) →
`ux/07` (aula). Lo que no entre antes del 27/10 sigue después: el piloto funciona con la UI actual
una vez resuelta `ux/02`.

## Ola 4 — Semana previa al piloto

`remediacion/FASE2-08-ci-matching-e2e.md` (P3) → `tesis/T13-despliegue-produccion.md` (**riesgo alto**,
con todo lo anterior mergeado).

## Después del piloto / antes de habilitar menores

- CAP: `tesis/T01` → `T02` (**riesgo alto**) → `T03` → `T04` (opcional). PT1, PT2, PT10.
- `remediacion/FASE2-06-baja-menor-anonimizacion.md`, `remediacion/FASE2-09-evidencia-upload.md`.
- T-M3-06 (clasificador on-device del kill-switch): chunk propio, fuera de estos paquetes.
- `remediacion/FASE3-01`, `FASE3-02` (sin la parte C), `FASE3-04` (P6), `FASE3-05`, `FASE3-06`,
  `FASE4-01`, `tesis/T14`.

## Todas las decisiones pendientes

| Paquete | IDs | Dónde están las recomendaciones |
|---|---|---|
| Remediación | P1, P2, P3, P4, P5, P6 | `remediacion/00-LEEME-opencode.md` §5 |
| UX | U1, U2 | `ux/00-LEEME-ux.md` §5 |
| Tesis | PT1 a PT10 | `tesis/00-LEEME-tesis.md` §4 |

Cuando respondas una, completá la columna "Respuesta del usuario" del LEEME correspondiente, en un
commit de docs. Opencode lee esa tabla y se frena si está `_pendiente_`.
