# M3C-05 — Disparo del kill-switch y las dos ramas en la UI (US-6, US-7)

**Branch:** `chunk/m3-c` · **Riesgo:** alto (Artículo II) · **Bloqueada por:** M3C-02, M3C-04

## 1. Problema

El backend ya decide la rama con datos de M1 (`SesionService.ejecutarKillswitch`: rama menor por
`reserva.getBeneficiario().getTipo()`), corta, cierra la sala (ADR-M3-03), avisa al Adulto Responsable
y pausa el escrow (ADR-M3-02). Lo que falta es que el cliente:

- dispare `POST /api/sesiones/{id}/killswitch` cuando el clasificador confirma;
- muestre cada rama como dice el Spec;
- en la rama adultos, oculte el video y haga la pregunta.

Además, la confirmación de la rama adultos hoy **no vence**: si nadie responde, queda pendiente para
siempre (decisión D5).

## 2. Implementación

### Frontend (`aula/[id]/page.tsx` + `src/lib/killswitch/`)

1. Ante `deteccion({ duenoId, track })` de M3C-02:
   1. Congelar el buffer de ese track (M3C-04).
   2. **Ocultar el video del detectado de inmediato en este cliente**, sin esperar al backend: se
      reemplaza el tile por un placeholder estático ("Video oculto por seguridad"). Vale para las dos
      ramas.
   3. `POST /killswitch { detectadoId: duenoId }`. **El cliente nunca manda la rama**: la decide el
      backend (T-M3-11).
   4. Si el POST falla por red, reintentar. Si hay menor y no se logra el POST en 3 intentos, el
      cliente igual se desconecta (fail-closed): nunca sigue mostrando video del otro con una
      detección sin reportar.
2. **Rama menor** (la respuesta trae la sesión `interrumpida` o la sala se cierra, `RoomEvent.Disconnected`
   con motivo `ROOM_DELETED`):
   - Subir el clip (M3C-04).
   - Pantalla de cierre calma y sin detalles para los dos lados: "La clase se cortó por seguridad. El
     equipo de Tinku lo está revisando." Al menor **no se le pregunta nada** y no ve qué se detectó
     (Artículo II). Al Tutor tampoco se le dice qué se detectó.
   - Nada de "volver a entrar": el backend ya no emite tokens para esa sesión.
3. **Rama adultos** (la respuesta trae la sesión `en_curso` y hay confirmación pendiente):
   - En 1:1, quien detectó es justamente "el otro participante" que tiene que responder: la pregunta se
     muestra en **este mismo cliente**. Diálogo: "¿Viste algo incorrecto o ilegal en el video de
     {nombre}?" con "Sí" y "No", y la cuenta regresiva del plazo de D5.
   - "No" → `POST /killswitch/confirmacion { vio: false }`, se restaura el video y se reanuda la
     observación de ese track. El clip se descarta.
   - "Sí" → `{ vio: true }`. El backend corta: mismo cierre que la rama menor y se sube el clip.
   - Al detectado se le avisa por el canal de datos de LiveKit (`topic: "killswitch"`) que su video se
     ocultó un momento, sin decir por qué. Es solo informativo: el estado de verdad está en el backend.
4. **Dos disparos casi a la vez** (cada lado detecta al otro): el segundo `POST /killswitch` vuelve
   sin crear nada porque ya hay una confirmación pendiente. La UI del segundo no muestra la pregunta
   si el detectado es él mismo; el backend ya rechaza que el detectado responda (`ConfirmacionNoPendiente`).

### Backend

5. **Plazo de la rama adultos (D5):** al crear la confirmación en `ramaAdultos`, agendar un job de
   Quartz **persistido** (AGENTS §1.3) con el plazo de la Tabla de Tiempos. Al vencer, si sigue sin
   respuesta, se resuelve como "No" con `respondidoId = null` y un marcador `vencida = true`.
   - Requiere una columna nueva en `aula.confirmaciones_killswitch` → **migración nueva**, sin tocar V12.
   - Una respuesta que llega después del vencimiento da 409, como hoy una ya respondida.
6. `GET /api/sesiones/{id}/killswitch`: estado para el cliente que recarga la página a mitad de una
   confirmación. Devuelve `{ confirmacionPendiente, detectadoId, venceAt }` solo a participantes (403 a
   terceros). Sin datos de la Alerta: BR-KS-03 los deja en M9.
7. Ningún evento nuevo: siguen `sesion.killswitch_menor` y `sesion.killswitch_adultos` con su payload
   actual (AGENTS §4).

## 3. Tests (RED primero)

Backend (integración, junto a `KillswitchIntegracionTest`):
1. `ramaAdultos_sinRespuesta_alVencer_seResuelveComoNo_ySigueEnCurso`.
2. `ramaAdultos_respuestaTrasVencimiento_409`.
3. `estadoKillswitch_participante_ok`, `estadoKillswitch_tercero_403`.
4. Regresión: `T-M3-11` (forzar la rama adultos con un menor) sigue verde.

Frontend (Playwright, API mockeada):
5. Rama menor: la detección oculta el video al instante, hace un único `POST /killswitch` sin campo
   `rama`, muestra el cierre y **no** muestra ninguna pregunta.
6. Rama adultos "No": se muestra la pregunta, `vio:false`, vuelve el video y no se sube el clip.
7. Rama adultos "Sí": `vio:true`, cierre y subida del clip.
8. Con menor y `POST /killswitch` fallando 3 veces: el cliente se desconecta igual.

## 4. Criterios de aceptación

- `Spec_M3` caso borde 4 cubierto por los tests 5 y 8 más los de backend existentes.
- Revisión contra AGENTS §3 en el commit: la rama con menor nunca pregunta ni continúa.

## 5. NO tocar

- La decisión de rama en el backend.
- El dinero: escrow pausado hasta que M9 resuelva (ADR-M3-02).
- El aviso al Adulto Responsable (FASE2-03).
