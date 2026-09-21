# ADR-M1-02 — Retiro del Certificado de Antecedentes Penales (CAP) del onboarding de Tutores

## Estado
Aceptado, formalizado **retroactivamente** (AGENTS.md §7 y §2). El código que implementa esta
decisión (carga, revisión, vencimiento del CAP) fue eliminado en el commit `ebf6cc0`
(2026-09-10) sin ADR ni enmienda constitucional acompañante. Este documento cierra esa deuda de
gobernanza: dejar registrada la decisión con su justificación real, tal como la confirmó la
persona que decide, y habilitar la enmienda v2.1 → v2.2 de la Constitución que este ADR requiere.

## Contexto
El Artículo I de la Constitución (enmienda v2.1) exigía el CAP del Tutor como parte del
"mecanismo de confianza" que reemplaza la ausencia de acceso estatal directo a antecedentes
penales, con una cláusula explícita: *"ninguna decisión de producto puede debilitar este
conjunto para ganar velocidad de desarrollo o fricción de onboarding"*.

Al operar el flujo con Tutores reales (piloto, 1 desarrollador sin equipo legal), aparecieron dos
problemas concretos que el Spec de M1 ya anticipaba parcialmente (BR-CAP-02 quedaba "pendiente de
validación legal externa antes de aplicarse en producción"):

1. **Fricción de onboarding medible:** exigir un trámite adicional en argentina.gob.ar antes de
   habilitar matching reduce la conversión de alta de Tutores en una etapa de piloto donde la
   oferta de Tutores es el recurso más escaso del marketplace.
2. **Riesgo de criterio legal sin respaldo:** BR-CAP-02 (antecedentes fuera de la lista de rechazo
   automático, o procesos en trámite sin sentencia firme) exige una decisión de descalificación
   manual y documentada. Sin asesoría legal externa contratada, ese criterio lo aplicaría el mismo
   desarrollador que opera el resto de la plataforma, sin respaldo profesional — un riesgo de
   responsabilidad mayor que operar sin el control.

## Decisión
**Se retira el CAP como requisito de habilitación para matching.** La Credencial Académica
aprobada (US-4, ya vigente) queda como el único requisito documental para que un Tutor quede
`activo_para_matching`. No se reemplaza por ningún control equivalente en este incremento.

Esta decisión **prioriza explícitamente la velocidad de onboarding y el costo/riesgo legal por
sobre el nivel de verificación de antecedentes que el Artículo I (v2.1) exigía** — es decir, hace
exactamente lo que esa cláusula prohibía. Se documenta así, sin eufemismo, porque es la única
forma honesta de dejarlo escrito: la enmienda v2.2 que acompaña este ADR no reinterpreta el
Artículo I, lo revierte parcialmente a propósito.

## Riesgo aceptado (explícito, sin mitigación equivalente)
- **No hay verificación de antecedentes penales de ningún tipo sobre un Tutor antes de que quede
  habilitado para tomar sesiones 1:1 con un Menor.** El único filtro de confianza activo hoy es:
  Credencial Académica (conocimiento de la materia, no idoneidad de trato con menores), CAP —
  retirado —, calificación explícita (reactiva, posterior al hecho) y kill-switch (detecta
  contenido visual en curso, no antecedentes).
- Un Tutor con antecedentes específicamente vinculados a delitos contra la integridad sexual o
  contra menores (la lista que BR-CAP-01 rechazaba sin excepción) **no tiene hoy ningún control
  documental que se lo impida** en Tinku.
- Este riesgo se acepta explícitamente para el piloto, no se considera resuelto ni mitigado.

## Alternativas descartadas / consideradas
- **CAP obligatorio solo para sesiones con Menores** (gate angosto: Tutor matchea y da clases a
  Estudiantes adultos sin CAP; se exige CAP aprobado únicamente antes de confirmar una sesión
  donde el Estudiante es Menor). Reduce la fricción de onboarding para el caso general sin tocar
  el caso de riesgo real. **Propuesta durante la revisión de este ADR y descartada explícitamente
  por la persona que decide**, que optó por el retiro total.
- **Mantener CAP pero sin BR-CAP-02** (solo lista de rechazo automático BR-CAP-01, sin la revisión
  legal discrecional): reduce el riesgo de criterio legal sin respaldo, mantiene algo de
  verificación. No fue la opción elegida.
- **Tercerizar la revisión del CAP** (servicio de verificación de antecedentes especializado):
  fuera de presupuesto USD 0-100/mes del piloto (Artículo VII). No evaluado en profundidad por
  costo evidente.

## Implementación
Ya aplicada en el commit `ebf6cc0` (2026-09-10), previo a este ADR:
- Eliminados: `CertificadoAntecedentesPenales` (modelo), `CertificadoService`, `AdminCapController`,
  `CapVencimientoJob`, DTOs (`CapResponse`, `CargarCapRequest`, `RevisarCapRequest`,
  `AccionRevisionCap`), `CapNoEncontradoException`.
- `CredencialService` queda como única compuerta de `activo_para_matching` del Tutor.
- La tabla `certificados_antecedentes_penales` (migración `V6`) **no se elimina** (AGENTS.md §7:
  una migración aplicada no se edita ni se dropea con una migración correctiva sin necesidad
  operativa) — queda en la base sin uso, documentada como tal.
- `V21` limpia el job/trigger de Quartz huérfano del CAP retirado.
- Frontend: `registro/tutor/page.tsx` pierde el paso de carga de CAP.

Este ADR no agrega código nuevo — formaliza lo ya deployado en `main`.

## Consecuencias
- **Constitución:** requiere la enmienda v2.1 → v2.2 del Artículo I (ver `Constitucion_Tinku.md`),
  aprobada junto con este ADR.
- **Spec_M1_Identidad_Perfiles.md:** US-6, FR-ID-021 a 025 y BR-CAP-01/02 quedan marcados
  `RETIRADO` en el documento, no eliminados — se conserva el texto original como registro de qué
  control existió y por qué se sacó (referencia a este ADR).
- **Plan_M1_Identidad_Perfiles.md:** sección 2.4 y las filas de API de CAP quedan marcadas
  `RETIRADO` con la misma referencia.
- **Revisión futura obligatoria:** este ADR se reabre antes de salir de piloto cerrado o de
  escalar el volumen de Tutores activos — no se considera una decisión permanente sin
  revisitarla con datos reales de uso e incidentes (o su ausencia).

## Registro de Decisiones Técnicas (Constitución)
No aplica una fila del Registro (no es una elección de proveedor/tecnología). El cambio de
principio queda en la enmienda v2.2 de la Constitución, no en el Registro.
