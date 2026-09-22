# Spec: M9 — Denuncias, Seguridad y Moderación

**Módulo:** M9 (ver Constitución, Artículo II, Artículo X)
**Estado:** Borrador para revisión
**Depende de:** M3 (Alertas de Seguridad del kill-switch, buffer de 30s), M1 (identidades, modelo de acceso del menor), M5 (consulta si hay escrow activo sobre la sesión denunciada)
**Acoplado bidireccionalmente con:** M8 (M9 define el flujo y los plazos; M8 es la interfaz desde donde el Admin de Moderación y Seguridad los ejecuta — no son independientes entre sí)
**Alimenta a:** M2 (bloqueos que excluyen del matching), M4 (cancelación de reservas por sanción, FR-SEC-008/012), M5 (pausa/reanudación de escrow, reembolsos), M7 (suspensiones y señales), M8 (casos a resolver)
**Referencia de tiempos:** Tabla_Tiempos_Tinku.md

---

## 1. Resumen

Este módulo orquesta el flujo de Denuncias y de las Alertas de Seguridad del kill-switch (M3). **El menor nunca presenta una Denuncia directamente** — no porque carezca de cuenta (sí la tiene, ver Spec de M1), sino por restricción explícita de permisos: si algo le ocurre, su Adulto Responsable la presenta en su nombre. Existen **dos tracks separados y con reglas distintas**: el de Denuncia estándar (con descargo de 48hs antes de la decisión del Admin de Moderación y Seguridad) y el de Alerta de kill-switch (revisión del Admin de Moderación y Seguridad en 12hs, sin esperar un descargo previo — la suspensión ya es automática desde M3, y el descargo del Tutor puede presentarse en cualquier momento como vía de apelación, incluso después de una resolución inicial).

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Presentar una Denuncia
*Como* Estudiante, Usuario con capacidad Adulto Responsable, o Tutor — el menor tiene cuenta propia pero no esta función habilitada —, *quiero* denunciar una sesión o a un usuario, *para* reportar un problema que la plataforma no detectó sola.

- **Dado** que ocurrió una sesión o interacción, **cuando** presiono "Denunciar" desde una cuenta con login propio, **entonces** elijo un motivo de lista cerrada y agrego evidencia opcional. Si el motivo involucra a un menor a mi cargo, la presento yo como su Adulto Responsable — el menor no tiene esa opción en ningún momento (FR-SEC-001).
- **Dado** que la sesión denunciada tenga dinero en escrow sin liberar, **cuando** se registre la Denuncia, **entonces** se pausa la liberación **de esa sesión específica** — no de todas las interacciones entre las dos cuentas involucradas (FR-SEC-003).

### US-2 — Alerta de Seguridad automática (kill-switch) — track independiente
*Como* Tinku, *quiero* que las detecciones del clasificador se conviertan en casos revisables con su propia urgencia, *para* que ninguna detección quede sin seguimiento y una cuenta no quede suspendida indefinidamente sin revisión humana.

- **Dado** que el kill-switch cortó una sesión, **cuando** se genera la Alerta con su evidencia, **entonces** la suspensión preventiva del Tutor implicado **ya es automática desde M3** — no es una decisión que tome el Admin de Moderación y Seguridad ni algo que "viva en M8"; el Admin de Moderación y Seguridad decide únicamente la reactivación o sanción final. La Alerta entra a la cola de M9 con prioridad alta, y el Admin de Moderación y Seguridad debe revisarla dentro de **12 horas** usando la evidencia disponible (más la opinión del Adulto Responsable si la rama fue la de menor, con su propia ventana de 12hs ya definida en M3) — **sin esperar un descargo previo del Tutor** (FR-SEC-004).
- **Dado** que el Tutor presente su descargo (300 caracteres) en cualquier momento, **cuando** eso ocurra, **entonces** se incorpora al caso — si llega después de una resolución inicial, puede fundamentar una revisión/apelación, pero no bloquea que el Admin de Moderación y Seguridad haya actuado dentro de las 12hs.

### US-3 — Puesta a disposición del denunciado (Denuncia estándar)
*Como* usuario denunciado, *quiero* conocer la denuncia y poder defenderme, *para* que la decisión no sea unilateral.

- **Dado** que se registró una Denuncia estándar (no derivada de kill-switch), **cuando** pasa a `en_revision`, **entonces** el denunciado recibe el motivo (sin identidad del denunciante, FR-SEC-006) y tiene 48hs para su descargo antes de que el Admin de Moderación y Seguridad decida.
- **Dado** que no presente descargo en 48hs, **cuando** el plazo venza, **entonces** el caso avanza igual a decisión del Admin de Moderación y Seguridad. Si el Admin de Moderación y Seguridad no resuelve dentro de los **5 días hábiles** siguientes al vencimiento del descargo, **cuando** eso ocurra, **entonces** el caso escala automáticamente con prioridad alta — el escrow pausado no puede quedar indefinido (FR-SEC-010).

### US-4 — Resolución del Admin de Moderación y Seguridad
*Como* Admin, *quiero* ver caso, evidencia y descargo juntos, *para* decidir con todo el contexto.

- **Dado** que el caso tenga lo necesario para decidir, **cuando** el Admin de Moderación y Seguridad lo resuelva, **entonces** puede: (a) infundada → se cierra, se reanuda la liberación del escrow **de esa sesión puntual**, se notifica a ambos; (b) fundada con sanción → elige de la escala (FR-SEC-005); (c) fundada con contenido ilegal → escalado (US-5).
- **Dado** que existan denuncias cruzadas entre las mismas dos cuentas (Estudiante denuncia a Tutor y viceversa), **cuando** una se resuelva como infundada, **entonces** su escrow correspondiente se libera igual, sin esperar a que la otra denuncia también se resuelva — cada caso libera su propio escrow de forma independiente (FR-SEC-011).

### US-5 — Escalado por contenido ilegal
*Como* Tinku, *quiero* un camino definido para material potencialmente ilegal, *para* cumplir obligaciones legales y proteger a los menores.

- **Dado** que la evidencia sugiera contenido ilegal, **cuando** el Admin de Moderación y Seguridad lo clasifique así, **entonces** se escala: preservación de evidencia con cadena de custodia, suspensión definitiva, y expediente para reporte a las autoridades (Artículo X).

### US-5bis — Retención de evidencia escalada _(agregado, auditoría 2026-09-18)_
*Como* Tinku, *quiero* que la evidencia preservada por un escalado de contenido ilegal tenga un plazo y un acceso explícitos, *para* que "cadena de custodia" no choque en la práctica con el Artículo V (minimización de datos) sin que nadie haya decidido el límite.

- **Dado** que un caso se escale por contenido ilegal (US-5), **cuando** eso ocurra, **entonces** el clip de evidencia de 30s deja de estar sujeto al régimen general de descarte de M3 (BR-KS-02, se elimina si el caso no escala) y se retiene por un plazo fijo a definir por asesoría legal antes del piloto — este Spec no fija el número por no ser una decisión de producto, pero exige que exista uno explícito, nunca "indefinido" (FR-SEC-013).
- **Dado** que el plazo de retención se cumpla sin que haya habido un reporte formal a la autoridad competente, **cuando** eso ocurra, **entonces** el clip se elimina igual que cualquier otro dato bajo el Artículo V — la excepción de "cadena de custodia" no es una excepción permanente al principio de minimización (FR-SEC-014).
- **Dado** que el clip esté en este régimen de retención extendida, **cuando** se consulte quién puede acceder a él, **entonces** el acceso queda restringido al Admin de Moderación y Seguridad que gestiona el caso — mismo criterio de acceso restringido que ya define BR-KS-05 para el clip general, sin ampliarlo (FR-SEC-015).

### US-6 — Sanciones y su efecto en la plataforma
*Como* Tinku, *quiero* que las sanciones tengan efectos consistentes, *para* que una cuenta sancionada no siga operando con normalidad.

- **Dado** que se sancione a un **Tutor**, **cuando** se active, **entonces** M2 lo excluye del matching, M4 bloquea nuevas reservas, sus sesiones futuras se cancelan con reembolso (FR-RES-006), y M5 aplica la regla de fondos de sanción definitiva (FR-PAG-011) — se paga el trabajo ya realizado, se retiene y reembolsa lo futuro.
- **Dado** que se sancione a un **Estudiante o Usuario con capacidad Adulto Responsable** (ej. por una denuncia frívola comprobada, o conducta abusiva), **cuando** se active, **entonces** M1 suspende la cuenta, y M4 cancela sus reservas futuras con reembolso normal a esa cuenta — el Tutor no pierde nada porque esas sesiones no llegaron a ocurrir (FR-SEC-012).
- **Dado** que una suspensión temporal venza, **cuando** eso ocurra, **entonces** la cuenta recupera sus funciones automáticamente, con notificación.

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-SEC-001 | Denuncia disponible para Estudiante, Adulto Responsable y Tutor — **restringida para el perfil de menor** (aunque tenga cuenta propia, esta función no está habilitada en su sesión). |
| FR-SEC-001bis _(agregado, AUD-011 / decisión D5, 2026-09-22)_ | Dos tipos de Denuncia. **Con sesión** (`sesionId`): denunciante y denunciado tienen que ser participantes de esa Sesión (tutor, beneficiario o pagador de su Reserva — el Adulto Responsable que paga la sesión de su menor cuenta como participante); si no, 403. Es la única que puede pausar un escrow (FR-SEC-003). **De perfil** (sin `sesionId`): la puede presentar cualquier usuario no-menor, sin exigir vínculo, y no pausa ningún escrow. En ambos casos se rechaza la auto-denuncia (422). **Riesgo aceptado:** la denuncia de perfil puede spamear la cola de moderación y arrancar el plazo de descargo sobre un inocente; se mitiga con rate limiting (FASE 2, AUD-012), no con un chequeo de vínculo. |
| FR-SEC-002 | Estados de la Denuncia estándar: `registrada` → `en_revision` (descargo 48hs) → `resuelta` / `escalada`. |
| FR-SEC-003 | Denuncia con escrow activo pausa la liberación **de esa sesión específica**, no de otras interacciones entre las mismas cuentas. |
| FR-SEC-004 | Alertas del kill-switch: track independiente, prioridad alta, revisión del Admin de Moderación y Seguridad en 12hs sin descargo previo obligatorio; suspensión preventiva ya automática desde M3. |
| FR-SEC-005 | Escala de sanciones manual del Admin de Moderación y Seguridad: advertencia, suspensión temporal, suspensión definitiva, baneo + reporte a autoridades. |
| FR-SEC-006 | Anonimato del denunciante frente al denunciado. |
| FR-SEC-007 | Toda resolución registrada con evidencia, descargo, decisión, sanción y Admin responsable. |
| FR-SEC-008 | Efectos de sanción a un Tutor propagados a M2, M4, M5, M1. |
| FR-SEC-009 | Casos de contenido ilegal: cadena de custodia, escalado, suspensión definitiva automática. |
| FR-SEC-010 | SLA de 5 días hábiles para que el Admin de Moderación y Seguridad resuelva una Denuncia estándar tras el descargo; si se excede, escala con prioridad alta. |
| FR-SEC-011 | La pausa de escrow en denuncias cruzadas es independiente por caso, no por par de cuentas. |
| FR-SEC-012 | Sanción a Estudiante/Adulto Responsable: suspensión de cuenta (M1) + cancelación con reembolso de sus reservas futuras (M4). |
| FR-SEC-013 _(agregado, auditoría 2026-09-18)_ | Evidencia de un caso escalado por contenido ilegal: retención por un plazo fijo (a definir con asesoría legal antes del piloto), no indefinida — excepción explícita y acotada al régimen general de descarte de M3. |
| FR-SEC-014 _(agregado)_ | Vencido el plazo de FR-SEC-013 sin reporte formal a la autoridad, el clip se elimina igual que bajo el régimen general (Artículo V). |
| FR-SEC-015 _(agregado)_ | Acceso al clip en retención extendida: restringido al Admin de Moderación y Seguridad del caso, mismo criterio que BR-KS-05, sin ampliarlo. |

## 4. Reglas de Negocio Aplicadas (referencia)

- BR-DEN-01 a BR-DEN-04, BR-KS-02/03, Artículo II, Artículo X.

## 5. Casos Borde — Todos Resueltos

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Denuncia tras liberación del escrow | Se registra igual; recuperación de fondos ya liberados es caso de soporte Admin. |
| 2 | Denuncias cruzadas | Independientes; escrow se libera por caso, no por par (FR-SEC-011). |
| 3 | Denuncia frívola repetida | Sanción al denunciante con la misma escala — incluye ahora explícitamente a Estudiante/Adulto Responsable (FR-SEC-012). |
| 4 | Alerta de kill-switch resultó falsa | Suspensión se levanta manualmente, se notifica, se registra. Clip se elimina según BR-KS-02. |
| 5 | Tutor suspendido con sesiones futuras | Cancelación automática con reembolso (FR-RES-006 de M4). |
| 6 | ~~Menor denuncia directamente~~ | **Corregido esta ronda:** el menor nunca denuncia directamente — lo hace su Adulto Responsable en su nombre (FR-SEC-001). |
| 7 | Descargo con material adicional | El Admin evalúa todo; plazos no se reinician salvo decisión explícita. |
| 8 | El denunciante retira la denuncia | El caso sigue igual hacia revisión del Admin de Moderación y Seguridad; el retiro queda registrado. |
| 9 | Fondos de Tutor con sanción definitiva | Se libera lo de sesiones ya realizadas, se retiene/reembolsa lo futuro (FR-PAG-011 de M5). |
| 10 | Reembolso al Estudiante detectado como infractor en la rama 2 del kill-switch (ambos adultos) | Decisión explícita: recibe el 100% de vuelta igual (FR-PAG-009 prohíbe parciales) — la consecuencia real para el infractor es la sanción de cuenta (FR-SEC-012), no quedarse con su dinero. |
| 11 | Conflicto de plazos: 12hs del kill-switch vs. 48hs de descargo | Resuelto: son dos tracks distintos. El kill-switch no espera un descargo previo para la revisión de 12hs; el descargo del Tutor puede llegar en cualquier momento y fundamentar una apelación posterior (FR-SEC-004). |
| 12 | Quién decide la suspensión preventiva | Es 100% automática desde M3 en el momento del corte — nunca una "decisión" del Admin de Moderación y Seguridad. El Admin decide solo la reactivación o sanción final. |
| 13 _(agregado, auditoría 2026-09-18)_ | Plazo de retención de evidencia de un caso escalado (choca con Artículo V si queda indefinido) | Plazo fijo a definir con asesoría legal antes del piloto; vencido sin reporte formal, se elimina igual que cualquier otro dato (FR-SEC-013/014). |

## 6. Fuera de Alcance de este Spec

- La detección y el corte de la sesión — M3.
- Las decisiones del Admin de Moderación y Seguridad — interfaz en M8; aquí el flujo y los plazos.
- Reputación/calificaciones — M7.
- Reporte a autoridades: M9 genera el expediente; la ejecución es proceso legal externo.

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Casos borde resueltos (12 de 12 originales, incluyendo E-31 a E-35 del informe de QA, + caso 13 agregado en la auditoría 2026-09-18).
- [x] Revisado contra la Constitución (Artículo II, Artículo X).

---

**Estado: APROBADO.**
