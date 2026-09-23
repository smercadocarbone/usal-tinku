# T01 — ADR y enmienda: CAP obligatorio para tutores de menores (DT6) — solo documentación

**Branch:** `tesis/cap-menores` · **Riesgo:** bajo (solo docs) · **Bloqueada por:** —

## 1. Contexto

ADR-M1-02 retiró el CAP de todos los Tutores y la enmienda v2.2 de la Constitución lo registró.
En ese ADR, la alternativa "CAP obligatorio solo para sesiones con Menores" figura como
**descartada**. La tesis (Cap. 6, riesgo R-10) la **retoma** porque resuelve los dos motivos del
retiro:

1. **Fricción de onboarding:** los Tutores que solo enseñan a adultos se registran sin trámite
   adicional; la oferta general no se frena.
2. **Criterio legal sin respaldo:** el criterio de rechazo se define con la asesoría legal ya
   presupuestada en la evaluación económica (USD 800).

## 2. Tareas (un solo commit de documentación)

1. **ADR-M1-04** — "CAP obligatorio para dictar clases a Menores". Estado: Aceptado.
   **Reemplaza** a ADR-M1-02 (no lo borra: a ADR-M1-02 se le agrega "Reemplazado por
   ADR-M1-04"). Incluye: decisión, por qué se reconsidera la alternativa descartada, riesgo
   residual (documento adulterado o mal evaluado: se mitiga con revisión manual y, opcionalmente,
   T04) y el impacto en el piloto (sin menores hasta T02, ver T10).
2. **Constitución, enmienda v2.2 → v2.3** (Artículo I): el CAP vuelve como requisito, **acotado**
   a la habilitación para Menores. El Artículo II no cambia.
3. **Spec_M1_Identidad_Perfiles.md:** FR-ID-021 a FR-ID-025 dejan de estar `RETIRADO` y pasan a
   "acotado a Tutores de Menores (ADR-M1-04)". Requerimiento nuevo **FR-ID-026**: un Tutor queda
   habilitado para Menores solo con un CAP `aprobado` y vigente. BR-CAP-01 vuelve; BR-CAP-02 queda
   en modo fail-closed hasta que la asesoría legal defina el criterio (PT1).
4. **Specs de M4 y M2:** referencias a FR-ID-026 donde se crea una Reserva o Solicitud con un
   Menor y donde se muestran tutores a un Menor (ver T02).
5. **Tabla de Tiempos:** fila "Vigencia del CAP — 12 meses desde la fecha de emisión — M1"
   (la spec lo indica explícitamente, A3).
6. **Tareas:** cancelar **T-AUD-032** (dropear tablas V6) con la nota "cancelada por ADR-M1-04";
   agregar T-TES-01 a T-TES-04. `REGISTRO_FINDINGS.md`: AUD-035 queda `EN CURSO` hasta que T02
   vuelva a usar las tablas.

## 3. Criterios de aceptación

- Ningún documento sigue diciendo que el CAP está retirado sin la aclaración de ADR-M1-04.
- `rg -n "RETIRADO" docs/specs/Spec_M1_Identidad_Perfiles.md` no devuelve FR-ID-021 a 025.
