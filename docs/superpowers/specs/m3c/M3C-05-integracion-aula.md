# M3C-05 — Integración en el aula: entrada protegida, disparo, ramas y reactivación

**Tarea:** T-M3-06e · **Riesgo:** alto (Artículo II) · **Depende de:** M3C-02, M3C-03, M3C-04 · **Commits:** 3 (orquestador con tests RED; orquestador GREEN; cableado en la página y componentes)

## 1. Qué hay que lograr

Conectar las piezas de M3C-02 a M3C-04 en `frontend/src/app/aula/[id]/page.tsx` para cumplir:
- US-6 (rama menor);
- US-6bis (falla del clasificador);
- US-7 (rama adultos, con plazo);
- casos borde 4 y 8 de `Spec_M3`.

Toda la lógica de decisión va en un **orquestador puro** con dependencias inyectadas, para poder
testearla sin LiveKit ni cámara. La página solo lo conecta a la sala y a la UI.

## 2. `frontend/src/lib/killswitch/orquestador.ts`

### 2.1 Constantes nuevas en `config.ts`

```ts
/** Tabla de Tiempos: "Reintentos del disparo del kill-switch". */
export const REINTENTOS_DISPARO = 3;
export const ESPERA_ENTRE_DISPAROS_MS = 1_000;
/** Tabla de Tiempos: "Plazo de respuesta de la rama adultos del kill-switch". */
export const PLAZO_RAMA_ADULTOS_MS = 60_000;
/** Canal de datos de LiveKit, separado de "pizarra" y del modo texto. */
export const TOPIC_KILLSWITCH = "killswitch";
```

### 2.2 Interfaz

```ts
export interface DepsOrquestador {
  sesionId: string;
  hayMenor: () => boolean;                       // fuente: token o estado; ante la duda, true
  api: {
    disparar(detectadoId: string): Promise<{ estado: string }>;
    confirmar(vio: boolean): Promise<{ estado: string }>;
    subirEvidencia(clip: Blob, duracionSegundos: number): Promise<void>;
    reportar(evento: EventoClasificador): Promise<void>;
  };
  congelarClip(trackId: string): { blob: Blob; duracionSegundos: number } | null;
  sala: {
    ocultarTrack(trackId: string): void;          // desconecta la pista de todo <video> y muestra el cartel
    mostrarTrack(trackId: string): void;
    ocultarTodoRemoto(): void;                    // reactivación con menor
    mostrarTodoRemoto(): void;                    // respeta los ocultos por detección
    enviarAviso(m: AvisoKillswitch): void;        // publishData reliable, topic TOPIC_KILLSWITCH
    salir(): void;                                // room.disconnect()
  };
  clasificador: { pausar(trackId: string): void; reanudar(trackId: string): void; destruir(): void };
  ui: {
    preguntar(p: { trackId: string; detectadoId: string; venceEn: number }): void; // muestra DialogoRamaAdultos
    cerrarPregunta(): void;
    cierre(motivo: "seguridad" | "sin-proteccion"): void;  // pantalla final, sin volver a entrar
    aviso(texto: string): void;                   // toast no bloqueante
  };
  esperar: (ms: number) => Promise<void>;         // inyectable para tests
  ahora: () => number;
}
export type AvisoKillswitch = { tipo: "video-oculto" } | { tipo: "corte-seguridad" };

export function crearOrquestador(deps: DepsOrquestador): {
  alDetectar(d: { trackId: string; duenoId: string }): Promise<void>;
  responder(vio: boolean): Promise<void>;
  vencioPregunta(): void;
  alCaer(): void;
  alReactivar(): void;
  alFallarDefinitivo(): void;
  alRecibirAviso(m: AvisoKillswitch): void;
  /** Disconnected de LiveKit. true si el orquestador ya mostró un cierre y la página no debe mostrar el suyo. */
  alDesconectar(): boolean;
};
```

### 2.3 Comportamiento exacto

**`alDetectar({ trackId, duenoId })`:**
1. Si ya hay una detección en curso (bandera interna), no hace nada.
2. `sala.ocultarTrack(trackId)` **antes de cualquier await** (ADR-M3-07: ocultar no espera al backend).
3. `clip = congelarClip(trackId)`, guardado en memoria del orquestador.
4. Si `hayMenor()`: `sala.enviarAviso({ tipo: "corte-seguridad" })`, best-effort, sin esperar.
5. `api.disparar(duenoId)` con hasta `REINTENTOS_DISPARO` intentos y `esperar(ESPERA_ENTRE_DISPAROS_MS)`
   entre cada uno.
6. Según el resultado:
   - **`estado === "finalizada"`** (rama menor):
     1. `subirClip()`;
     2. `clasificador.destruir()`;
     3. `ui.cierre("seguridad")`;
     4. `sala.salir()`.
   - **`estado === "en_curso"`** (rama adultos):
     1. `sala.enviarAviso({ tipo: "video-oculto" })`;
     2. `ui.preguntar({ trackId, detectadoId: duenoId, venceEn: ahora() + PLAZO_RAMA_ADULTOS_MS })`.
   - **Fallaron todos los intentos:**
     - **con menor:** `clasificador.destruir()`, `ui.cierre("seguridad")` y `sala.salir()`. El clip se
       descarta: sin Alerta no hay dónde subirlo. Fail-closed: nunca se sigue viendo al otro.
     - **entre adultos:** `ui.preguntar(...)` igual, en modo local. Si después responde "Sí", sale de
       la sala; si responde "No", restaura. No llama a `confirmar`.
   - **Otro estado** (la sesión ya estaba cerrada): `ui.cierre("seguridad")` y `sala.salir()`.

**`responder(vio)`:**
1. `ui.cerrarPregunta()`.
2. Si la pregunta era local (el disparo falló): con `vio`, `sala.salir()`; si no, restaurar.
3. Si no era local: `api.confirmar(vio)`.
   - Con `vio` y la respuesta en `"finalizada"`: `subirClip()`, `clasificador.destruir()`,
     `ui.cierre("seguridad")` y `sala.salir()`.
   - Con `!vio`, o si la API responde 422 (vencida o ya respondida): restaurar.
4. **Restaurar** es:
   - `sala.mostrarTrack(trackId)`;
   - `clasificador.reanudar(trackId)`;
   - descartar el clip;
   - limpiar la bandera de detección.

**`vencioPregunta()`:** lo llama el `DialogoRamaAdultos` al llegar a `venceEn`. Hace `ui.cerrarPregunta()`
y restaura, **sin** llamar a la API: el job del backend (M3C-02) ya la resuelve como "No".

**`subirClip()`:** si hay clip, `api.subirEvidencia(blob, duracionSegundos)`. Los reintentos ya están
en `subirEvidencia`. Errores: `console.error` sin datos del clip. Nunca bloquea el cierre.

**Falla del clasificador** (ADR-M3-07 §6):
- `alCaer()`: `api.reportar("CAIDA")`, sin esperar. Si `hayMenor()`, `sala.ocultarTodoRemoto()` y
  `ui.aviso("Reactivando la protección del aula…")`.
- `alReactivar()`: `api.reportar("REACTIVADO")`. Si `hayMenor()`, `sala.mostrarTodoRemoto()`.
- `alFallarDefinitivo()`: `api.reportar("CORTE_SIN_REACTIVAR")`. Si `hayMenor()`:
  `ui.cierre("sin-proteccion")` y `sala.salir()`. Entre adultos no hace nada más.

**`alRecibirAviso(m)`:** del otro participante, o sea del lado del detectado.
- `"video-oculto"` → `ui.aviso("Tu video se ocultó un momento para el otro participante.")`.
- `"corte-seguridad"` → anota la bandera `corteSeguridadRecibido`.
- El aviso **nunca** decide nada: el estado de verdad está en el backend.

**`alDesconectar()`:**
- si ya se mostró un cierre, devuelve `true`;
- si `corteSeguridadRecibido`, hace `ui.cierre("seguridad")` y devuelve `true`;
- en otro caso devuelve `false`, y la página sigue con su manejo actual de `Disconnected`.

## 3. Tests del orquestador (commit 1, RED) — `frontend/tests/aula/killswitch-orquestador.spec.ts`

Lógica pura en Node, con `deps` falsas que registran llamadas en un array (`esperar` resuelve al
instante). Tags `@aula` `@KS-03x`.

| ID | Caso | Se verifica |
|---|---|---|
| KS-030 | Rama menor | `ocultarTrack` es la 1ª llamada, antes de `disparar`; después `subirEvidencia`, `cierre("seguridad")` y `salir`; nunca `preguntar` |
| KS-031 | Rama menor, `disparar` falla 3 veces | 3 intentos; `cierre("seguridad")` y `salir`; sin `subirEvidencia` |
| KS-032 | Rama adultos + "No" | `preguntar`; `confirmar(false)`; `mostrarTrack` y `reanudar`; sin `subirEvidencia` ni `salir` |
| KS-033 | Rama adultos + "Sí" | `confirmar(true)` → `finalizada`; `subirEvidencia`, `cierre`, `salir` |
| KS-034 | Rama adultos, vence | `vencioPregunta()` restaura sin llamar a `confirmar` |
| KS-035 | Rama adultos, `confirmar` da 422 | Restaura |
| KS-036 | Adultos, `disparar` falla 3 veces, "Sí" | Pregunta local; `salir`; nunca `confirmar` |
| KS-037 | Dos `alDetectar` seguidos | Un solo `disparar` |
| KS-038 | `alCaer` con menor | `reportar("CAIDA")`, `ocultarTodoRemoto` |
| KS-039 | `alCaer` sin menor | Solo `reportar("CAIDA")`, sin ocultar |
| KS-040 | `alFallarDefinitivo` con menor | `cierre("sin-proteccion")`, `salir` |
| KS-041 | `alFallarDefinitivo` sin menor | Solo `reportar`, sin `salir` |
| KS-042 | `alReactivar` con menor | `mostrarTodoRemoto` |
| KS-043 | Aviso `corte-seguridad` y después `alDesconectar` | `cierre("seguridad")` y devuelve `true` |
| KS-044 | Ningún payload de `disparar` lleva `rama` | Revisar los argumentos |

Mensaje: `test(aula): RED del orquestador del kill-switch (M3C-05)`.

Commit 2: el orquestador en GREEN. Mensaje:
`feat(aula): orquestador del kill-switch (ramas, reintentos, reactivación) (M3C-05, ADR-M3-07)`.

## 4. Cableado en la página y componentes (commit 3)

### 4.1 Componentes nuevos en `frontend/src/components/aula/`

- **`AvisoProteccion.tsx`**: pantallas de bloqueo a pantalla completa, sin botón "volver a entrar".
  Textos en rioplatense, sin decir qué se detectó (Artículo II, BR-KS-03):
  - `"no-disponible"`: "No pudimos activar la protección del aula en este dispositivo. Para una clase
    con un menor tiene que estar activa. Probá con otro navegador (Chrome actualizado) o con otro
    dispositivo."
  - `"seguridad"`: "La clase se cortó por seguridad. El equipo de Tinku lo está revisando." Botón
    "Volver a mis clases".
  - `"sin-proteccion"`: "Se cortó la clase porque no se pudo reactivar la protección del aula. Si
    vuelve a pasar, probá con otro navegador o dispositivo." Botón "Volver a mis clases".
- **`DialogoRamaAdultos.tsx`**:
  - modal "¿Viste algo incorrecto o ilegal en el video de {nombre}?", con "Sí" y "No";
  - cuenta regresiva hasta `venceEn`, que al llegar a 0 llama a `vencioPregunta()`;
  - foco inicial en "No", cierra con Escape como "No", `aria-modal`, etiquetas accesibles.
- **Cartel sobre el tile oculto:** "Video oculto por seguridad" o "Reactivando la protección…". Va
  en el mismo componente de tile que ya existe; no inventes un layout nuevo.

### 4.2 Cambios en `frontend/src/app/aula/[id]/page.tsx`

1. **Estado nuevo:**
   - `hayMenor: boolean | null`;
   - `proteccion: "cargando" | "lista" | "no-disponible"`;
   - `ocultosPorDeteccion: Set<string>`;
   - `todoOculto: boolean`;
   - `pregunta`;
   - `cierreKillswitch`.
   - Refs: `clasificadorRef`, `buffersRef: Map<trackSid, BufferEvidencia>` y `orquestadorRef`.
2. **Al montar** (junto al `useEffect` de la previa):
   1. `getEstadoKillswitch(sesionId)` → `hayMenor`. Si falla, `hayMenor = true`.
   2. `ClasificadorKillswitch.crear({ hayMenor, ... })` cableando sus callbacks a `orquestadorRef`.
   3. Si rechaza:
      - con menor: `proteccion = "no-disponible"`;
      - sin menor: `proteccion = "no-disponible"`, `reportarClasificador("NO_CARGA")` y se puede
        entrar igual.
3. **Botón de entrar de la previa:**
   - con `hayMenor !== false` y `proteccion === "cargando"`: deshabilitado con el texto "Activando la
     protección del aula…";
   - con menor y `no-disponible`: se muestra `AvisoProteccion` `"no-disponible"` y **no** se llama a
     `conectar()`.
4. **`conectar()`:**
   - `api.post(`/api/sesiones/${sesionId}/token?clasificadorListo=${proteccion === "lista"}`)`;
   - si da `ApiError` con `codigo === "CLASIFICADOR_NO_LISTO"`, mostrar `"no-disponible"`;
   - si `tokenResp.hayMenor` difiere del `hayMenor` que se usó al crear el clasificador:
     `clasificadorRef.current?.actualizarHayMenor(tokenResp.hayMenor)`. Agregá ese método en
     `clasificador.ts`: cambia el umbral y reinicia los contadores.
5. **`TrackSubscribed`**, si es video (cámara o pantalla):
   - `clasificadorRef.current?.observar(pub.trackSid, participante.identity, track.mediaStreamTrack)`;
   - `buffersRef.current.set(pub.trackSid, BufferEvidencia.crear(track.mediaStreamTrack))`. Si `crear`
     devuelve `null`, registrarlo con `console.warn`: sin MediaRecorder no hay clip, pero el corte
     funciona igual.
   - Si el track está en `ocultosPorDeteccion` o `todoOculto`, **no** hacer `attach`.
6. **`TrackUnsubscribed`**, si es video: `dejarDeObservar`, `buffersRef.current.get(sid)?.descartar()` y
   `delete`.
7. **`DataReceived`** con `topic === TOPIC_KILLSWITCH`:
   - parsear y validar: solo `{tipo:"video-oculto"}` o `{tipo:"corte-seguridad"}`, todo lo demás se
     descarta, igual que `decodificar` de la pizarra;
   - después `orquestadorRef.current.alRecibirAviso(m)`.
8. **`Disconnected`:** `if (orquestadorRef.current?.alDesconectar()) return;` antes del manejo actual.
9. **Implementación de `sala` en el orquestador:**
   - `ocultarTrack(id)`: `track.detach()` de todos los elementos y sumarlo a `ocultosPorDeteccion`.
     El tile muestra el cartel en vez del `<video>`, tanto en grande como en miniatura.
   - `mostrarTrack(id)`: lo saca del set y vuelve a hacer `attach` con la misma lógica de "conectar
     cuando aparece el elemento" que ya usa `pistasRef`.
   - `ocultarTodoRemoto` y `mostrarTodoRemoto` hacen lo mismo con `todoOculto`.
   - `enviarAviso`: `room.localParticipant.publishData(bytes, { reliable: true, topic: TOPIC_KILLSWITCH })`.
10. **Limpieza** en `Disconnected`, al finalizar y al desmontar:
    - `clasificadorRef.current?.destruir()`;
    - descartar todos los buffers.
11. La pizarra, el modo texto, compartir pantalla y la grabación de audio del resumen quedan como
    están. La pizarra sigue apagada con menor.

Mensaje del commit 3: `feat(aula): kill-switch en el aula (entrada protegida, ramas, reactivación) (M3C-05, ADR-M3-07)`.

## 5. Verificación

- `npm run lint`, `npm run build` y `npx playwright test tests/aula` verdes.
- Prueba manual local con `npm run dev`, backend local y el modelo falso de M3C-03 inyectado desde la
  consola. Recorrer:
  - rama adultos "No";
  - rama adultos "Sí";
  - vencimiento;
  - caída y reactivación.
  Anotá en el commit qué recorriste. La rama menor de punta a punta se prueba en M3C-06.
- En `docs/specs/Spec_M3_Aula_Virtual.md`:
  - quitá la marca "**NO IMPLEMENTADO (AUD-014/T-M3-06, 2026-09-21)**" de US-6 y agregá
    "Implementado en M3C-04/M3C-05 (commits …)";
  - en FR-AULA-010, completá la referencia al cliente.
- Tildá T-M3-06e en las dos listas de tareas.

## 6. NO tocar

- La decisión de rama (backend).
- El clip y el umbral: ya están fijados en M3C-03 y M3C-04.
- La pizarra.
- La lógica de conectividad y corte <50 % (US-4/US-5), que es la que resuelve el pago tras
  `sala.salir()`.
