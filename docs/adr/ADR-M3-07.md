# ADR-M3-07 — Kill-switch en el cliente: qué se clasifica, cuándo se confirma y cómo falla

## Estado

Aceptado (2026-10-14; plazos ajustados por el dueño el mismo día: 5 min de rama adultos, 10 s sin
respuesta, 5 reintentos cada 5 s; decisiones del dueño del producto en la sesión de cierre de T-M3-06). Completa
ADR-M3-01 (modelo y framework) sin cambiarlo. Implementa FR-AULA-003, FR-AULA-004 y FR-AULA-010 de
`Spec_M3` a través de las specs `docs/superpowers/specs/m3c/M3C-02` a `M3C-05`.

## Contexto

ADR-M3-01 eligió NSFWJS (MobileNetV2, 5 clases: `Drawing`, `Hentai`, `Neutral`, `Porn`, `Sexy`) sobre
TensorFlow.js, en el navegador. No dijo:

- qué video mira cada cliente;
- cuándo una detección cuenta;
- qué hacer si el clasificador falla.

ADR-M3-02 ya aceptó que el disparo es una afirmación del cliente: el backend no ve video
(Artículo V) y no puede verificar nada. Este ADR no cambia eso.

## Decisión

### 1. Cada cliente clasifica el video que recibe

Cada navegador clasifica los tracks de video **remotos**: cámara y pantalla compartida del otro
participante. El `detectadoId` que manda al backend es el dueño de ese track, o sea su `identity` en
LiveKit, que ya es el UUID del usuario (`SesionService.obtenerToken`).

- **Por qué:** el clasificador que protege al menor corre en el dispositivo del menor. Un Tutor con
  un cliente modificado no puede apagarlo.
- **Descartada:** que cada uno clasifique su propio video. Se anula apagándolo.

### 2. La pantalla compartida también se clasifica

Mismas reglas que la cámara. Si no, compartir pantalla saltea el kill-switch.

### 3. Frecuencia

- Un solo ciclo de clasificación por cliente, que cada `intervalo de clasificación` (Tabla de Tiempos)
  toma **un** track remoto, rotando entre los que haya (round-robin).
- Si la inferencia anterior todavía no terminó, ese turno se saltea: nunca se encolan inferencias.
- Con cámara y pantalla a la vez, cada track se clasifica cada dos intervalos. El costo de CPU queda
  acotado, que es lo que importa en gama media.

### 4. Qué cuenta como detección

El puntaje de un frame depende de si en la clase hay un menor. El cliente lo sabe por el backend
(`hayMenor`), nunca por algo que elija el usuario.

| Clase | Puntaje del frame | Umbral |
|---|---|---|
| **Con un menor** (estricto) | `Porn + Hentai + Sexy` | ≥ 0,85 |
| **Entre adultos** | `Porn + Hentai` | ≥ 0,85 |

Una detección está **confirmada** con **3 muestras consecutivas** del mismo track sobre el umbral. Una
muestra debajo reinicia la cuenta de ese track.

Con un menor se cuenta `Sexy` a propósito. NSFWJS pone ahí torsos desnudos, ropa interior y trajes de
baño, así que va a haber más falsos positivos: una pileta de fondo o una musculosa pueden cortar una
clase.

- **Se acepta.** Con un menor, el costo de no cortar es mayor que el de cortar de más (Artículo II).
  El corte lo revisa un admin dentro de las 12 hs (ADR-M3-02) y la familia no pierde la plata.
- **Entre adultos** no se cuenta `Sexy`: ahí la rama pregunta antes de cortar (US-7) y no tiene
  sentido ocultarle el video a alguien por una musculosa.
- Sin calibración en el MVP (BR-KS-04): los números se revisan con los datos de alertas del piloto.
  **Nota 2026-10-18 (T-TES-11b, sin piloto):** no hay piloto con menores (T-TES-10). Lo que existe para revisar
  estos números es el E2E con LiveKit real (M3C-06, 9 casos) y el benchmark del clasificador (M3C-07, anexo de
  ADR-M3-01). La revisión con datos de alertas reales pasa a **trabajo futuro**, con disparador: el encendido de
  `tinku.menores.sesiones-habilitadas` (M3C-09) y sus primeros 3 meses de alertas. La decisión no cambia.

### 5. Clip de evidencia

- Solo video, sin audio (Artículo V: ninguna regla necesita el audio).
- `videoBitsPerSecond = 1_200_000` (1,2 Mbps). En 30 s da unos 4,5 MB, debajo del límite de 5 MB de
  `spring.servlet.multipart.max-file-size`.
- Si el navegador se pasa del bitrate y el clip supera 5 MB, se recortan las partes más viejas antes
  de subir.
- Vive solo en memoria y se descarta si no hay disparo (FR-AULA-004, BR-KS-01).

### 6. Falla del clasificador: reintentar antes de cortar

"Clasificador caído" significa una de dos cosas:
- tiró un error de runtime;
- no completó ninguna clasificación durante el `tiempo sin respuesta del clasificador` (Tabla de
  Tiempos).

**Antes de entrar a la sala:**
- **Con menor:** si el clasificador no carga dentro del `tiempo máximo de carga del clasificador`, no
  se pide token y no se entra (fail-closed, US-6bis).
- **Entre adultos:** se entra igual y se registra.

**Demora de una clasificación con un menor:** si una clasificación tarda más que la `demora que oculta
el video con un menor` (Tabla de Tiempos), el video remoto se oculta con el cartel "Verificando la
protección…" hasta que termine. No cuenta como caída: así una demora larga nunca deja al menor viendo
video sin clasificar, y la caída se declara recién al `tiempo sin respuesta del clasificador`, que es
más largo para no reintentar por un celular lento.

**Con la clase en curso y un menor:**
1. **No se corta la llamada.** El audio sigue. El video remoto se oculta de inmediato con el cartel
   "Reactivando la protección…". El menor nunca ve video sin clasificar (Artículo II, `Spec_M3` US-6bis).
2. Se intenta revivir el clasificador: se descarta la instancia, se vuelve a cargar y se hace una
   inferencia de prueba. Son hasta `reintentos para reactivar el clasificador` intentos, separados por
   el intervalo de la Tabla de Tiempos.
3. Si revive, vuelve el video y se reinicia la cuenta de muestras.
4. Si no revive, el cliente sale de la sala (`room.disconnect()`), lo avisa al backend y muestra
   "Se cortó la clase porque no se pudo reactivar la protección". El efecto de negocio es el de un
   corte por conectividad (US-4/US-5) y lo resuelven los jobs persistidos que ya existen.

**Con la clase en curso entre adultos:** se reintenta igual, sin ocultar el video ni cortar, y se
registra.

**No hay vigilante en el backend.** Un cliente modificado puede mentir un "sigo vivo" igual que puede
no disparar nunca (ADR-M3-02). El vigilante no agregaría garantía y sí costo.

El temporizador de reintentos es del cliente. No viola AGENTS §1.3 porque no decide plata ni
seguridad del lado del servidor: solo corta la conexión propia.

### 7. Plazo de la pregunta en la rama adultos

Si nadie responde dentro del `plazo de respuesta de la rama adultos`, se resuelve como "No":
- se restaura el video;
- no hay Alerta;
- queda registrado como vencida.

El vencimiento lo ejecuta un job de Quartz persistido (AGENTS §1.3).

## Riesgos aceptados

- **Un cliente modificado puede no clasificar nunca.** Es el mismo límite que ADR-M3-02 acepta para
  el disparo falso, y el motivo por el que el clasificador que importa corre del lado del menor.
- **Falsos positivos con menor** por contar `Sexy` (punto 4).
- **Latencia en gama media.** Medición aproximada del 2026-10-14 (anexo de ADR-M3-01): con el backend
  `wasm` y la CPU frenada a 4× (perfil de gama media de Lighthouse), una inferencia tarda unos 370 ms
  (p50) y 510 ms (p95). Sin GPU, el intervalo efectivo por track queda en 0,5–1 s y la confirmación
  en unos 1,5–3 s. Con `webgl` y GPU real debería ser bastante menos. No hay medición en un dispositivo
  físico.
- **Hasta unos 35 s sin video** con un menor entre que se detecta la caída y se agotan los reintentos
  (10 s sin respuesta + 5 intentos cada 5 s, punto 6). El audio sigue. Es la alternativa elegida a
  cortar la clase al primer tropiezo.
- **Hasta 5 minutos con el video del detectado oculto** en la rama adultos mientras se espera la
  respuesta (punto 7). Entre adultos se acepta: la clase sigue con audio.

## Alternativas descartadas

- **Mostrar el video durante los reintentos:** viola US-6bis ("nunca se continúa la sesión con el menor
  sin monitoreo activo").
- **Cortar al primer fallo:** el dueño lo descartó, porque corta clases por un tropiezo de segundos.
- **Vigilante de latidos en el backend:** ver el punto 6.
- **Mismo umbral para las dos ramas:** contar `Sexy` entre adultos ocultaría video por motivos
  triviales; no contarlo con menor dejaría pasar torsos desnudos.
