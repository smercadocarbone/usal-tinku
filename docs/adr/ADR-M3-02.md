# ADR-M3-02 — Modelo de amenaza del kill-switch y desacople del dinero (anexo a ADR-M3-01)

## Estado

Aceptado (2026-09-22). Implementa la decisión de producto **D3, opción 2** del plan de remediación
(`docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`, Task 1.10) y cierra la parte
financiera de **AUD-005**. Se revisa en FASE 2 (T-AUD-024) con el clasificador real integrado.

## Contexto

ADR-M3-01 decidió que el clasificador NSFW corre **on-device**, en el navegador. Consecuencia que
ADR-M3-01 no escribió: **el disparo del kill-switch es una afirmación del cliente**. El backend no
ve video (Artículo V) y no puede verificar que hubo una detección. `POST /api/sesiones/{id}/killswitch`
solo exige ser participante de la Reserva.

### Modelo de amenaza

| Actor | Capacidad | Qué ganaba antes de este ADR |
|---|---|---|
| Estudiante o Adulto Responsable (pagador) | Llama al endpoint con `curl`, sin clasificador, en cualquier minuto de la sesión | Rama menor: **reembolso total inmediato** de una clase ya dada (ej. 55 de 60 min) + suspensión preventiva del Tutor |
| Tutor | Ídem | Corta una sesión que no quiere dar. No gana dinero: el reembolso va al Estudiante |
| Cliente modificado | Ignora la detección local, o la fabrica | Lo mismo que los anteriores |

El vector con incentivo económico es el del pagador: `EscrowService.onSesionKillswitchMenor/Adultos`
llamaba a `reembolsarSiRetenida` sin mirar el tiempo transcurrido ni esperar ninguna revisión.

### Restricción no negociable

El Artículo II exige que, con un menor presente, el corte sea **inmediato e incondicional**: no puede
depender de evidencia previa, de una confirmación del menor ni de una revisión humana. Cualquier
control que demore el corte queda descartado de entrada.

## Decisión

1. **El corte no cambia.** `SesionService.ejecutarKillswitch` corta igual que antes, y en la rama
   menor cierra la sala (ADR-M3-03). El Artículo II queda intacto.
2. **Se desacopla el dinero.** Los listeners de `sesion.killswitch_menor` y
   `sesion.killswitch_adultos` dejan de reembolsar: la transacción pasa de `retenido_escrow` a
   `pausado_denuncia` (estado ya existente, en el CHECK de V11) y se cancela la liberación agendada.
3. **M9 decide cuándo se mueve la plata.** Al resolver la Alerta de Seguridad,
   `AlertaSeguridadService.resolver()` publica `alerta.resuelta` (M9 → M5, payload `reservaId`), y
   M5 reembolsa el total al Estudiante: con `reactivar` (falso positivo) y con `sancionar`. **En los dos
   casos el Estudiante cobra.** Lo que cambia es que ya no es automático ni instantáneo: pasa por la
   cola de moderación, con la ventana de revisión de 12hs de `Tabla_Tiempos_Tinku.md`.

El reembolso sigue siendo total (FR-PAG-009: prohibido el parcial automático) y sigue sin castigar
al detectado con su dinero (FR-PAG-012).

## Qué resuelve y qué no — dicho sin maquillaje

**Resuelve:** el premio **instantáneo y sin testigos**. Hoy cada disparo abusivo queda frente a un
Admin humano antes de mover un peso, con la Alerta, el detectado y el historial de disparos del mismo
usuario a la vista.

**No resuelve:**

- **El abuso sigue siendo rentable si el Admin no lo detecta.** Con D3 opción 2, un `reactivar` también
  reembolsa. El freno es la revisión humana, no una regla. Un patrón de disparos repetidos del mismo
  pagador es visible en la cola, pero **no existe hoy una vía para sancionar a quien disparó**: la
  sanción de M9 recae sobre el detectado. Si el Admin concluye que el disparo fue fraudulento, la
  herramienta que tiene es una Denuncia de perfil contra el pagador (FR-SEC-001bis).
- **El corte arbitrario sigue siendo posible.** Cualquier participante puede cortar una sesión con
  menor sin evidencia. Es el costo aceptado del Artículo II. Se mitiga, no se elimina, con el rate
  limiting de FASE 2 (AUD-012).
- **La suspensión preventiva del detectado sigue siendo inmediata.** Un Tutor puede quedar fuera del
  matching por un disparo falso hasta la resolución (12hs).

## Actualización FASE 2 — FASE2-10 (2026-09-23)

Cierra el **riesgo residual de la colisión** con Denuncias (T-AUD-024): los listeners de kill-switch
pasaron de reusar `pausado_denuncia` a un estado propio **`pausado_alerta`** (V25, nueva migración,
CHECK reemplazado sin tocar V11). La Alerta de seguridad manda sobre la Denuncia:

- `sesion.killswitch_menor/adultos` → escrow a `pausado_alerta`, incluso si ya estaba
  `pausado_denuncia` (la pausa por Alerta es más grave; no baja si la Denuncia llega después).
- `alerta.resuelta` solo actúa sobre `pausado_alerta` (reembolso total al Estudiante).
- `denuncia.resuelta` sobre un escrow `pausado_alerta` es no-op con log — M9 no puede liberar al
  Tutor (ni reembolsar) mientras la Alerta siga sin resolver. Solo la resuelve la Alerta.
- El reembolso parcial manual de Soporte Financiero sigue rechazando `pausado_alerta` (422), como
  ya rechazaba `pausado_denuncia`.

Queda vigente sin cambios: el reembolso total por kill-switch espera la resolución de M9
(`reactivar` o `sancionar`, los dos reembolsan al Estudiante — FR-PAG-009/012).

## Riesgos aceptados

- **Escrow congelado sin plazo automático.** Si el Admin no resuelve la Alerta, la plata queda en
  `pausado_alerta` indefinidamente. No hay job que la reembolse al vencer la ventana de 12hs. Es
  preferible a liberar o reembolsar sin revisión, y el Soporte Financiero la ve en la cola.
- ~~**Colisión con una Denuncia sobre la misma sesión.**~~ **Resuelto en FASE 2 (FASE2-10,
  2026-09-23):** estado propio `pausado_alerta` — la Alerta manda, `denuncia.resuelta` es no-op y
  solo `alerta.resuelta` destraba el escrow. Ver "Actualización FASE 2" más abajo.

## Alternativas consideradas

- **Status quo (reembolso inmediato):** descartada. Es el vector de AUD-005.
- **Exigir evidencia (clip de 30s) antes del corte:** descartada, porque viola el Artículo II. La
  evidencia llega después del corte, por diseño (BR-KS-01).
- **Reembolso proporcional al tiempo no dictado:** descartada. FR-PAG-009 prohíbe el reembolso parcial
  automático.
- **Liberar al Tutor si la Alerta se resuelve como falso positivo:** es la opción que corta el
  incentivo de raíz, pero castiga al Estudiante por una detección automática que puede ser un falso
  positivo legítimo del modelo. El usuario eligió D3 opción 2, que prioriza no retener el dinero de
  una familia por un error del clasificador. Queda como alternativa a reevaluar con datos reales de
  abuso.
