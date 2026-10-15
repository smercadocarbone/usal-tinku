# Pruebas del aula en vivo y del kill-switch en `tinku.site` (2026-10-14)

> Segunda ronda de las pruebas exploratorias de `PLAN_pruebas_produccion.md`, con la pasarela en
> bypass (`habilitada:false`, `bypassPermitido:true`) y la flag de menores encendida. Corrió sobre el
> deploy del PR #85 (`03915af`, run 53 en verde): el kill-switch del cliente ya estaba en producción.
> La primera ronda está en `HALLAZGOS_pruebas.md`. Scripts, capturas y resultados crudos:
> `docs/operacion/pruebas_aula/`.

## 1. Veredicto

**El kill-switch funciona de punta a punta, en las dos ramas y en la falla del clasificador.** Salas
de LiveKit reales, dos navegadores por escenario, backend de producción. No hay bloqueantes.

Hay **un hallazgo de severidad media** (quien no recibe el aviso del otro navegador queda mirando
"Esperando al otro participante" tras un corte) y dos bajos. Todo lo demás se comportó como dicen
ADR-M3-07, el Spec M3 y la Tabla de Tiempos.

## 2. Cómo se probó, y los límites

| Tanda | Qué corrió | Qué prueba |
|---|---|---|
| 1 | Frontend **de producción** (`https://tinku.site`), dos navegadores Chromium con dispositivos falsos, backend y LiveKit reales | Aula, clasificador real cargado, puerta del token, y el disparo por **API** (lo que haría un cliente que detecta algo, ADR-M3-02) |
| 2 | Frontend **mergeado, en modo desarrollo en este contenedor**, apuntando a la API y a LiveKit de producción, con un **modelo falso** controlable | El flujo **completo del cliente**: detecta → oculta → avisa → dispara → graba el clip → muestra el cierre |

- **No se puede disparar el clasificador real con contenido de verdad**, y el hook del modelo falso se
  quita del build de producción a propósito (verificado: 0 apariciones de `__tinkuModeloFalso` en el
  JS de producción). Por eso la tanda 2 usa el build de desarrollo. En la tanda 1 sí corrió el modelo
  real en vivo, con video de prueba de Chromium, sin falsos cortes.
- **Exactitud del modelo con contenido real: no probada** (BR-KS-04, fuera del MVP).
- **Latencia en un celular real: no medida.** En el entorno de prueba (dos navegadores, GPU por
  software, 4 núcleos compartidos) algunas clasificaciones superaron los 3 s y apareció el cartel
  "Verificando la protección…". No es representativo de un teléfono. Ver H-AULA-2.
- **Plata:** las reservas son en bypass (`en_bypass`), así que no hay dinero real. El estado del
  escrow tras el corte (`pausado_alerta`) **no se verificó**: no hay endpoint de lectura para eso.
- **Las pruebas corrieron en la ventana de 5 min previa al inicio de la clase** (la sala abre a T-5),
  salvo la de adultos, que cruzó las 22:00. Por eso la sesión figura `no_iniciada` en varias salidas.
  Las reglas de reembolso por corte antes del 50 % (US-4/US-5) **no se ejercitaron**.

## 3. Resultados

### 3.1 Tanda 1 — producción, 30 de 37 bien

Los 7 marcados mal son **5 errores de mi chequeo y 1 hallazgo real (H-AULA-1)**, contado dos veces:

| Marcado mal | Qué pasó de verdad |
|---|---|
| AULA-1, RAMA-AD-1, RAMA-AD-4, MENOR-1 | Esperé `en_curso`, pero la clase todavía no había empezado (hora de inicio 21:00, prueba a las 20:55). `no_iniciada` es lo correcto |
| RAMA-AD-2 | Puse un tope de 301 s para el plazo de 5 min y dio 302 s (latencia de red) |
| RAMA-AD-10, RAMA-MEN-3 | **Real:** tras el corte, el navegador queda en "Esperando al otro participante". Ver H-AULA-1 |

Lo que anduvo (todo contrastado contra la API):

- **Puerta del token (M3C-02):** menor sin clasificador listo → 409 `CLASIFICADOR_NO_LISTO`; tercero →
  403; adultos sin clasificador → 200 con `hayMenor=false`; menor con clasificador listo → 200 con
  `hayMenor=true` y `pizarraHabilitada=false`.
- **Aula:** cada navegador recibe video del otro; el modelo real se descarga en los dos; no aparece
  ningún cartel de protección con adultos; sin pizarra con menor.
- **Rama adultos:** el disparo no corta; confirmación pendiente con plazo de ≈300 s; el propio
  detectado no puede responder (422); "No" mantiene la clase; un segundo disparo vuelve a preguntar;
  "Sí" corta (`finalizada`).
- **Rama menor:** corta directo aunque el cliente mande `rama=adultos`; no hay confirmación que
  responder (422); el menor no puede volver a entrar (422).
- **Evidencia y avisos:** el clip WebM se sube (200); un archivo que no es video se rechaza (422); la
  alerta aparece en la cola de moderación con su rama; el Adulto Responsable recibe `KILLSWITCH_MENOR`
  sin datos del Tutor ni de lo detectado.

### 3.2 Tanda 2 — cliente real con detección forzada

**D — Menor (Mía con la Tutora Lorena): 10 de 10 bien**

| # | Verificado | Resultado |
|---|---|---|
| D-1 | Los dos en la sala con el clasificador corriendo | 58 clasificaciones en 15 s, video en los dos sentidos |
| D-2 | "Sexy" ≥ 0,85 durante 3 muestras dispara el corte (umbral estricto con menor) | **2,5 s** desde que el modelo empezó a detectar |
| D-3 | A la menor no se le pregunta nada | Sin diálogo |
| D-4 | El cartel no revela qué se detectó | "La clase se cortó por seguridad. El equipo de Tinku lo está revisando." |
| D-5 | La Tutora detectada también ve el cierre | Sí, por el aviso entre navegadores |
| D-6 | Llamadas del cliente | `POST /killswitch {"detectadoId":…}` (sin `rama`) y `POST /evidencia` |
| D-7 | Alerta con clip grabado del track remoto | `rama=menor`, `pendiente_revision` |
| D-8 | El admin descarga el clip | 200, WebM válido (`1a45dfa3`), **280 871 bytes** (límite 5 MB) |
| D-9, D-10 | Sesión cerrada, sin pendiente; no se puede reentrar | 422 al pedir token |

**C — Adultos (Abril con el Tutor Juan): 10 de 13 bien**

| # | Verificado | Resultado |
|---|---|---|
| C-1 | Entre adultos "Sexy" no dispara nada | Correcto. Las únicas llamadas fueron el `POST /token` de entrada (**mi chequeo contaba el token como disparo**) |
| C-2 | "Porn" ≥ 0,85 ×3 oculta el video y pregunta a quien detectó | 1,2 s. Diálogo: "Su video está oculto mientras respondés. Si no respondés, se toma como «No» en 5:00" |
| C-3 | El detectado recibe el aviso de que su video se ocultó | Sí, sin decir por qué |
| C-4 | El video del detectado está oculto | Correcto. La captura muestra "Video oculto por seguridad" (**mi chequeo contó el video propio**) |
| C-5 | Confirmación pendiente con plazo | Pendiente, vence en 297 s |
| C-6 | "No" restaura el video y sigue la clase | Sí |
| C-7 | Segunda detección vuelve a preguntar | Sí |
| C-8 | **Sin respuesta, a los 5 min el diálogo se cierra solo** | **300 s exactos** |
| C-9 | El backend lo resolvió como "No", sin alerta | Sí |
| C-10 | Tras vencer, vuelve el video | Sí |
| C-11 | "Sí" corta; el que respondió ve el cierre de seguridad | Sí |
| C-12 | El detectado ve un mensaje claro de cierre | **No: "Esperando al otro participante"**. Ver H-AULA-1 |
| C-13 | Alerta `rama=adultos` con clip | Sí |

**E — Falla del clasificador con un menor (Mía con Lorena): 6 de 8 bien, los otros 2 son errores de mi chequeo**

La línea de tiempo del cliente, tomada de las llamadas reales:

| Tiempo | Evento |
|---|---|
| +14,5 s | `CAIDA` (el clasificador tira error). El video del otro se oculta con "Reactivando la protección…"; el audio sigue |
| +19,5 s | `REACTIVADO`, **5,0 s después**: revivió en el primer reintento y volvió el video |
| +21,0 s | `CAIDA` otra vez (esta vez el modelo ya no puede recargarse) |
| +47,0 s | `CORTE_SIN_REACTIVAR`, **26 s después**: 5 reintentos cada 5 s. El cliente sale de la sala y muestra "Se cortó la clase porque no se pudo reactivar la protección" |

E-2 y E-3 figuran mal en el JSON porque mi chequeo contó el video propio; E-4 porque lo revisé antes
de que pasaran los 5 s del primer reintento. **La línea de tiempo de arriba confirma que los tres
funcionaron.** E-7 es el hallazgo real (H-AULA-1).

## 4. Hallazgos

| ID | Sev. | Hallazgo |
|---|---|---|
| **H-AULA-1** | **Media** | **Tras un corte, quien no recibe el aviso del otro navegador queda mirando "Esperando al otro participante"**, con el botón "Finalizar clase" y sin ningún mensaje de cierre. Pasa cuando **la sesión quedó cerrada en el backend** y el cliente no recibió el aviso: (a) rama adultos con "Sí", el adulto detectado (C-12); (b) cualquier corte pedido por API o por otro cliente (tanda 1, dos veces); (c) cualquier cliente que no llegue a recibir el aviso. **Precisión:** en el escenario E (falla del clasificador) la otra persona ve "Esperando al otro participante" porque el que perdió la protección salió de la sala y puede volver a entrar: eso es correcto y **no** es este hallazgo (E-7 fue un chequeo mío demasiado estricto). **Causa** (confirmada en el código, `aula/[id]/page.tsx`): el cartel de cierre depende de un mensaje de datos `corte-seguridad` entre navegadores, que `alDetectar` solo envía cuando hay un menor, y el handler de `Disconnected` cae en `setEstado("esperando")`. Con un menor funciona en la rama de detección (D-5), pero no en la falla del clasificador. **Arreglo propuesto:** en `Disconnected`, consultar el estado de la sesión al backend (`GET /api/sesiones/por-reserva/{id}`) y, si está `finalizada` o `interrumpida`, mostrar un cierre ("La clase terminó"; "La clase se cortó por seguridad" si hay alerta del kill-switch). Así el mensaje no depende de un aviso entre pares |
| H-AULA-2 | Baja | El cartel "Verificando la protección del aula…" dura 6 s fijos aunque el video ya haya vuelto (`ui.aviso` con temporizador de 6 s, `alNormalizar` no lo borra). Además, en el entorno de prueba apareció seguido por la demora de >3 s de la clasificación, que oculta el video con un menor. Hay que medir en un celular real si la demora de 3 s es adecuada. **Propuesta:** que `alNormalizar` limpie el aviso |
| H-AULA-3 | Baja | En la pantalla "La clase se cortó por seguridad" el **título casi no se lee** (verde oscuro sobre fondo oscuro, `AvisoProteccion.tsx`); el texto de abajo sí. Accesibilidad |
| H-AULA-4 | Baja | `https://tinku.site/dev/killswitch` (y `/dev/componentes`, que ya era así) responde 200 con una pantalla "Cargando…" y el título de la página de desarrollo, en vez de 404. No expone nada: el contenido hace `notFound()` y el hook del modelo falso no está en el JS de producción. A confirmar si el 200 es solo del shell |
| Confirmado | Media | **TRANS-1** sigue abierto: `POST /clasificador {"evento":"OTRO"}` da **403 sin cuerpo** en vez de 400 (ver `HALLAZGOS_pruebas.md`). M3C-10 todavía no se hizo |

## 5. Qué no se probó

- La detección con el clasificador real y contenido explícito de verdad (ver §2).
- La latencia y la exactitud del clasificador en un dispositivo real.
- El estado del escrow tras el corte y los reembolsos por corte antes del 50 % de la clase.
- Compartir pantalla con el kill-switch activo (se probó solo con cámara).
- El audio de la clase y el resumen automático con menor (el adicional sigue apagado en producción).
- Un corte con un solo participante en la sala.

## 6. Datos que dejaron estas pruebas en producción

- **Las 4 alertas de seguridad generadas se resolvieron como "reactivar"** (cola de moderación vacía),
  lo que devuelve a Lautaro (`40000001`), Abril (`40000002`) y Lorena (`31000028`) a su estado normal
  para el matching.
- 5 reservas de prueba, todas en bypass: 3 de Mía con Lorena y 2 entre adultos. Cuatro quedaron
  `finalizada` por el corte. La de las 23:00 (escenario E) sigue `confirmada` y se resuelve sola por
  no-show a T+10 min.
- Una calificación de 5 estrellas de Lorena a Mía, hecha para desbloquear la siguiente reserva
  (FR-REP-006).
- Los residuos de la ronda anterior siguen como estaban (ver `HALLAZGOS_pruebas.md` §8).
- **Pendiente de la ronda anterior:** Benjamín (`50000002`) sigue con `capacidadEstudiante:true`
  (T3-01 sin arreglar).

## 7. Cómo repetirlo

`docs/operacion/pruebas_aula/scripts/`:

- `live.mjs`: tanda 1, contra producción. Variables: `NODE_USE_ENV_PROXY=1`, `ADMIN_PASS` (la clave
  del admin de Moderación). Los ids de sesión están fijos arriba del archivo y hay que reemplazarlos
  con los de reservas nuevas.
- `live2.mjs`: tanda 2. Requiere `npm run dev` en `frontend/` con `NEXT_PUBLIC_API_URL=https://api.tinku.site`
  y `PLAYWRIGHT_DISABLE_FORCED_CHROMIUM_PROXIED_LOOPBACK=1` si el entorno obliga a pasar por un proxy.
  `ONLY=D,C,E` elige los escenarios.
- **Los tokens de login duran 60 min:** para una espera larga hay que volver a iniciar sesión.
- La sala de LiveKit solo existe desde 5 minutos antes de la hora de la clase, y no se puede reservar
  con menos de 30 minutos de anticipación: una prueba nueva tarda al menos esos 35 minutos en poder
  entrar a una sala.
