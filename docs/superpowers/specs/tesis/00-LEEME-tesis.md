# Specs derivadas de la tesis — cambios de producto y operación (para ejecutar con opencode)

> **Leé este archivo completo antes de abrir cualquier spec `T*.md`.** Estas specs aplican al
> sistema las decisiones que se tomaron al cerrar la evaluación económica (Cap. 5), el análisis
> de riesgos (Cap. 6) y los resultados (Cap. 7) de la tesis. Siguen **el mismo protocolo** que
> `docs/superpowers/specs/remediacion/00-LEEME-opencode.md` (§2 protocolo por tarea, §3
> guardrails A1–A12, §6 reporte): leelo también. `AGENTS.md` gana sobre cualquier spec; si se
> contradicen, **PARAR** y preguntar.

Estado al escribir estas specs (2026-09-23): `main` en `3eb7235`. Última migración: **V24**.
Las specs de remediación FASE 2–4 siguen vigentes y **no se reemplazan**: varias de estas
dependen de ellas.

---

## 1. Por qué existen estas specs

La tesis fijó decisiones que el código todavía no refleja:

| Tema | Hoy en el código | Lo que decidió la tesis |
|------|------------------|-------------------------|
| Comisión de plataforma | 15 % (`application.yml`, BR-PAG-01) | **27 %** |
| Precio de la sesión | Libre, sin piso | **Piso de USD 4 por hora** (en ARS) |
| Resumen automático | Para toda sesión ≥10 min, sin proveedor | **Adicional opcional pago**, solo entre adultos, con Gemini 3.5 Flash-Lite |
| Grabación de audio | Prohibida (AGENTS §1.5) | **Solo audio, solo si se contrató el adicional, borrado tras transcribir** |
| CAP (antecedentes penales) | Retirado (ADR-M1-02) | **Obligatorio solo para dictar clases a menores** |
| Piloto | Sin restricción de menores | **Sin menores** hasta kill-switch en cliente (T-M3-06) + CAP |
| Indicadores del piloto (I-01 a I-04) | No se miden | **Instrumentados** |
| Producción | Coolify en equipo propio | **VPS DigitalOcean 4 GB** + runbook de despliegue y rollback |

---

## 2. Orden de ejecución

El piloto arranca el **27/10/2026** (Sprint 11). Todo lo marcado *Antes del piloto* es
bloqueante.

| # | Spec | Etapa | Depende de | Riesgo |
|---|------|-------|-----------|--------|
| 1 | `T05-comision-27.md` | Antes del piloto | — | Medio (dinero) |
| 2 | `T10-gate-menores-piloto.md` | Antes del piloto | — | Bajo |
| 3 | `T06-piso-tarifa.md` | Antes del piloto | **FASE2-01** mergeada | Medio (dinero) |
| 4 | `T07-proveedor-llm.md` | Antes del piloto | — | Medio (datos) |
| 5 | `T08-grabacion-audio-resumen.md` | Antes del piloto | T07 | **ALTO** (privacidad) |
| 6 | `T09-adicional-resumen.md` | Antes del piloto | T05, T08 | Medio (dinero) |
| 7 | `T11-instrumentacion-piloto.md` | Antes del piloto | — | Bajo |
| 8 | `T12-ux-recomendaciones.md` | Antes del piloto | — | Bajo |
| 9 | `T13-despliegue-produccion.md` | Antes del piloto (semana previa) | 1–8 mergeadas | **ALTO** (operación) |
| 10 | `T01-cap-adr-enmienda.md` | Antes de habilitar menores | — | Bajo (solo docs) |
| 11 | `T02-cap-backend.md` | Antes de habilitar menores | T01 | **ALTO** (seguridad del menor) |
| 12 | `T03-cap-frontend-moderacion.md` | Antes de habilitar menores | T02 | Medio |
| 13 | `T04-cap-firma-digital.md` | Opcional | T02 | Bajo |
| 14 | `T14-cobertura-ci.md` | Cuando haya lugar | — | Bajo |

Branches: `tesis/<slug>` por spec (ej. `tesis/comision-27`), un commit por tarea como mínimo.
Las specs del CAP (T01–T03) van en `tesis/cap-menores` con sub-branches si hace falta.

**Registro de tareas (AGENTS §8):** cada spec se agrega como `T-TES-XX` en
`docs/Tasks_Tinku_Implementacion.md` **y** `docs/Tasks_Tinku_Chunks.md` en el primer commit de
la tarea, y se tilda en el commit que la cierra.

---

## 3. Decisiones ya tomadas por el usuario (no volver a preguntar)

| ID | Tema | Decisión |
|----|------|----------|
| DT1 | Comisión | **27 %** sobre el monto bruto de la sesión, a cargo del Tutor (BR-PAG-01 actualizada). |
| DT2 | Precio | **Piso de USD 4 por hora**, expresado en ARS en un parámetro configurable. El precio lo sigue fijando el Tutor. |
| DT3 | Resumen | **Adicional opcional** que se contrata por reserva, a **USD 0,50** (en ARS). **No se ofrece en sesiones con un menor.** |
| DT4 | Proveedor LLM | **Gemini 3.5 Flash-Lite** (cierra T-FIN-03). Gemini 2.0 Flash fue dado de baja el 01/06/2026. |
| DT5 | Grabación | **Solo audio**, **solo** en sesiones con el adicional contratado, **solo entre adultos**, con **consentimiento** de ambos participantes (aceptado en los Términos y Condiciones: el alumno al contratar el adicional, el Tutor en el onboarding), y se **borra** al obtener el transcript (máx. 24 hs). |
| DT6 | CAP | **Obligatorio y vigente solo para dictar clases a menores.** Los Tutores que solo enseñan a adultos no lo cargan. Reemplaza a ADR-M1-02. |
| DT7 | Piloto | **Sin sesiones con menores** hasta que estén cerrados T-M3-06 (kill-switch en cliente) y el CAP (T02). |
| DT8 | Producción | Backend + matching-service en **VPS DigitalOcean Basic 2 vCPU / 4 GB** con Coolify; imágenes construidas en CI, nunca en el servidor. |

---

## 4. Decisiones que estaban pendientes — **todas respondidas el 2026-09-23**

| ID | Pregunta | Recomendación | Respuesta del usuario |
|----|----------|---------------|------------------------|
| PT1 | Criterio de rechazo del CAP fuera de la lista automática (BR-CAP-02) | Definirlo con la asesoría legal presupuestada (USD 800) **antes** de habilitar menores. Hasta entonces, todo CAP que informe cualquier antecedente queda `en_revision_legal` y **no habilita** (fail-closed). | **Fail-closed hasta la asesoría legal** (recomendación aceptada). _(2026-09-23)_ |
| PT2 | ¿Verificación automática de la firma digital del CAP? (requiere PDFBox + BouncyCastle → ADR) | **Manual en el MVP**; automatizar después con ADR. | **Manual en el MVP**; T04 queda como mejora futura. _(2026-09-23)_ |
| PT3 | Piso en ARS: ¿valor y actualización? | Parámetro `tinku.tarifa.piso-hora-ars` = **6140** (USD 4 × $1.535, BNA 09/09/2026), revisado **mensualmente** por un Admin. Sin API de tipo de cambio (sería una dependencia externa nueva). | **$6.140 fijo, revisión mensual** por un Admin. _(2026-09-23)_ |
| PT4 | Tarifas vigentes por debajo del piso al desplegar | **No retroactivo:** se exige en la próxima edición de la tarifa; aviso al Tutor en su panel. | **No retroactivo + aviso** en el panel del Tutor. _(2026-09-23)_ |
| PT5 | Precio del adicional en ARS y qué pasa si el resumen falla | **770 ARS** (USD 0,50 redondeado) y **reembolso automático del adicional** si el resumen queda `fallido`. | **$770 + reembolso parcial del adicional** si el resumen falla. **El caso "sin consentimiento" no existe:** la grabación de audio es condición del adicional y se acepta en los Términos y Condiciones (el alumno, de forma explícita al contratarlo; el Tutor, en el onboarding). _(2026-09-23)_ |
| PT6 | Plazo máximo de retención del audio (va a la Tabla de Tiempos, A3) | Borrar **al generar el transcript**, con un **máximo de 24 hs** aunque falle. | **Hasta el transcript, máximo 24 hs.** _(2026-09-23)_ |
| PT7 | Pipeline del resumen: ¿una llamada con audio o dos (transcribir → anonimizar → resumir)? | **Dos llamadas**: mantiene la anonimización antes del resumen que ya implementa `ResumenService`; el costo extra es despreciable (el audio domina). | **Dos llamadas** (transcribir → anonimizar → resumir). _(2026-09-23)_ |
| PT8 | ¿Cuándo y cómo se pregunta el NPS (I-04)? | **Una vez por usuario al cierre del piloto**, in-app, pregunta 0–10. | **Una vez al cierre del piloto, in-app**, 0–10. _(2026-09-23)_ |
| PT9 | ¿Dónde escribe LiveKit Cloud el audio grabado? (no escribe en un disco local: necesita un endpoint compatible con S3) | **Supabase Storage** (compatible con S3, incluido en el plan Pro), bucket privado. | **Garage autoalojado en el VPS** (compatible con S3). **No MinIO:** su edición Community fue archivada en feb-2026 y no recibe parches de seguridad. Requiere ADR. _(2026-09-23)_ |
| PT10 | Si vence o se revoca el CAP de un Tutor con reservas confirmadas con Menores | Cancelar esas reservas con **reembolso total** y aviso al Adulto Responsable; el Tutor sigue habilitado para adultos. | **Cancelar con reembolso total** y aviso al Adulto Responsable. _(2026-09-23)_ |
| TS1 | ¿Cómo lee y borra el backend el audio en Garage? (T08) | **AWS SDK for Java v2, solo `s3` + `url-connection-client`**, declarado en ADR-000-06. Implementar la firma SigV4 a mano es la fuente de bugs más probable de T08. | _pendiente_ |

---

## 5. Inconsistencias conocidas entre la tesis y el código (no las arregla ninguna spec)

- El Cap. 4 de la tesis menciona un **chat en tiempo real** sobre Redis; el backend **no tiene
  módulo de chat**. Se corrige la tesis, no el código (el chat no está en ningún Spec — Art. VI).
- AUD-035 (tablas V6 del CAP huérfanas) deja de ser un problema: las tablas **vuelven a usarse**
  (T02). **Cancelar T-AUD-032** (migración que las dropea) en el commit de T01.
