# ADR-M1-04 — CAP obligatorio para dictar clases a Menores

## Estado
Aceptado, 2026-09-23. **Reemplaza a ADR-M1-02** (que queda como registro histórico del retiro,
anotado en su encabezado). Formaliza la decisión DT6 de la tesis y el cierre de R-10 (Cap. 6).

## Contexto
ADR-M1-02 retiró el CAP de todos los Tutores con dos motivaciones: fricción de onboarding en una
etapa de piloto donde la oferta de Tutores es el recurso más escaso, y un criterio de rechazo
(BR-CAP-02) sin respaldo legal profesional. En ese ADR, la alternativa "CAP obligatorio solo para
sesiones con Menores" fue descartada explícitamente.

La tesis (Cap. 6, riesgo R-10) **retoma esa alternativa** porque resuelve los dos motivos del retiro
sin renunciar al control sobre el caso de riesgo real (sesión 1:1 entre un Menor y un Tutor):

1. **Fricción de onboarding:** los Tutores que solo enseñan a adultos se registran sin trámite
   adicional; la oferta general no se frena por el CAP.
2. **Criterio legal sin respaldo:** el criterio de rechazo de BR-CAP-02 queda fail-closed (cualquier
   antecedente fuera de la lista automática de BR-CAP-01 deja el CAP `en_revision_legal` y **no
   habilita**) hasta que la asesoría legal presupuestada en la evaluación económica (USD 800)
   defina la política interna de descalificación (PT1, 2026-09-23).

## Decisión
**El CAP vuelve como requisito, obligatorio y vigente, solo para que un Tutor quede habilitado a
dictar clases a Menores.** Los Tutores que solo enseñan a adultos no lo cargan y su
`activo_para_matching` no depende de él — el CAP define una capacidad aparte
(`habilitadoParaMenores`, separada del matching), tal como especifica T02. Un Tutor queda
habilitado para Menores solo con un CAP `aprobado` y vigente (12 meses desde la emisión,
FR-ID-026, ver Tabla de Tiempos).

Esta decisión **revierte parcialmente ADR-M1-02** y requiere la enmienda v2.2 → v2.3 del
Artículo I de la Constitución (el CAP deja de estar retirado y vuelve acotado a Menores). El
Artículo II no cambia.

## BR-CAP-01 y BR-CAP-02 (acotadas a Menores)
- **BR-CAP-01 (vuelve):** rechazo automático, sin excepción y sin apelación en producto, ante
  delitos contra la integridad sexual (abuso sexual, corrupción de menores, grooming Ley 26.904,
  pornografía infantil, trata con fines de explotación sexual), cualquier delito específicamente
  vinculado a menores, homicidio/tentativa de homicidio.
- **BR-CAP-02 (fail-closed hasta la asesoría legal, PT1):** cualquier otro antecedente o proceso
  en trámite sin sentencia firme queda `en_revision_legal` y **no habilita**. No se aplica una
  regla automática de rechazo ni de aprobación; la decisión es manual y documentada por el Admin
  de Moderación y Seguridad, conforme a la política que defina la asesoría legal. Hasta que esa
  política exista, el estado `en_revision_legal` es el único resultado posible para este caso.

## Riesgo residual (aceptado, con mitigación)
- **Documento adulterado o mal evaluado:** un CAP falso o mal leído podría habilitar a un Tutor
  con antecedentes. Se mitiga con la revisión manual del Admin de Moderación y Seguridad sobre el
  documento original (visor del PDF, T02) y, opcionalmente, con la verificación automática de la
  firma digital del CAP (T04, diferida a futuro por PT2: manual en el MVP).
- No es una garantía absoluta de idoneidad de trato con menores; sigue siendo un control
  documental complementario a la Credencial Académica, la calificación explícita y el kill-switch.

## Impacto en el piloto
El piloto arranca el **27/10/2026 sin sesiones con Menores** (DT7, T10): no se habilitan hasta que
se cierren T-M3-06 (kill-switch en el cliente) y T02 (implementación backend del CAP). Este ADR
solo documenta la decisión; la habilitación operativa es posterior.

## Consecuencias
- **ADR-M1-02:** anotado "Reemplazado por ADR-M1-04", queda como registro histórico.
- **Constitución:** enmienda v2.2 → v2.3 del Artículo I (retorno del CAP, acotado a Menores).
- **Spec_M1_Identidad_Perfiles.md:** FR-ID-021 a 025 dejan de estar `RETIRADO` y quedan acotados
  a Tutores de Menores; nuevo FR-ID-026; vuelven BR-CAP-01 (automática) y BR-CAP-02 (fail-closed).
- **Tabla_Tiempos_Tinku.md:** fila "Vigencia del CAP — 12 meses desde la fecha de emisión — M1".
- **Tareas:** T-AUD-032 (migración que dropea las tablas de V6) queda **cancelada** — las tablas
  vuelven a usarse en T02. AUD-035 queda `EN CURSO` hasta T02.
- **Specs M4 y M2:** referencias a FR-ID-026 donde se crea una Reserva o Solicitud con un Menor y
  donde se muestran tutores a un Menor.