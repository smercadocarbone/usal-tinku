# Chunk M3-C — Cierre de T-M3-06 (kill-switch en el cliente) y habilitación de menores

**Branch:** `chunk/m3-c` · **Fecha:** 2026-10-14 · **Bloquea a:** `tinku.menores.sesiones-habilitadas=true` (AGENTS §3)

## 1. Por qué existe este chunk

El backend del kill-switch está completo y testeado (T-M3-07 a T-M3-11, ADR-M3-02, ADR-M3-03), pero
**ningún cliente lo dispara**: no hay NSFWJS, TensorFlow.js ni MediaRecorder en `frontend/`
(`Tasks_Tinku_Chunks.md`, Chunk M3-C). Sin esto, el Artículo II no tiene protección real en una clase
con un menor, y por eso el piloto corre con la flag de menores en `false` (T-TES-10).

T02 (CAP) ya está cerrado (T-TES-02/03). **T-M3-06 es lo único que falta** para que la flag se pueda
encender, más los hallazgos de las pruebas en producción que tocan a menores
(`docs/operacion/HALLAZGOS_pruebas.md`, rama `claude/admiring-gates-8ul9n2`).

## 2. Specs y orden

| # | Spec | Qué cierra | Depende de | Quién |
|---|------|-----------|------------|-------|
| 01 | [`M3C-01-adr-y-tiempos.md`](M3C-01-adr-y-tiempos.md) | ADR-M3-07 + filas nuevas en la Tabla de Tiempos | **Decisiones D1–D8 (§4)** | agente, con tu respuesta |
| 02 | [`M3C-02-clasificador-cliente.md`](M3C-02-clasificador-cliente.md) | Clasificador NSFWJS sobre el video remoto (FR-AULA-003) | 01 | agente |
| 03 | [`M3C-03-fail-closed.md`](M3C-03-fail-closed.md) | Falla del clasificador con menor presente (US-6bis, FR-AULA-010) | 02 | agente |
| 04 | [`M3C-04-buffer-evidencia.md`](M3C-04-buffer-evidencia.md) | Buffer rotativo de 30 s y subida del clip (FR-AULA-004, BR-KS-01) | 02 | agente |
| 05 | [`M3C-05-disparo-y-ramas.md`](M3C-05-disparo-y-ramas.md) | Disparo, rama menor y rama adultos en la UI (US-6, US-7) | 02, 04 | agente |
| 06 | [`M3C-06-tests.md`](M3C-06-tests.md) | Tests de integración y E2E por historia y caso borde | 02–05 | agente |
| 07 | [`M3C-07-benchmark-dispositivo.md`](M3C-07-benchmark-dispositivo.md) | Latencia real en gama media (pendiente de ADR-M3-01) | 02 | **vos** (dispositivo físico) |
| 08 | [`M3C-08-entorno-pruebas.md`](M3C-08-entorno-pruebas.md) | Entorno de pruebas con bypass y menores encendidos | — | **vos** (Coolify) + agente |
| 09 | [`M3C-09-habilitar-menores.md`](M3C-09-habilitar-menores.md) | Precondiciones y encendido de la flag en producción | 01–08 + hallazgos | **vos** decidís; agente documenta |

01 va primero y solo. 02 abre el resto. 03, 04 y 07 pueden ir en paralelo después de 02.
08 se puede preparar en paralelo con todo. 09 es lo último.

## 3. Reglas que aplican a todo el chunk

- Un commit por spec como mínimo. Implementación y test en commits separados si la spec los separa
  (AGENTS §5).
- Cada historia (US-6, US-6bis, US-7) con al menos un test de integración de punta a punta; los casos
  borde 4 y 8 de `Spec_M3` §5 son tests obligatorios.
- Ningún tiempo sale de memoria: si no está en `Tabla_Tiempos_Tinku.md`, se espera a 01.
- Ninguna migración ya aplicada se edita (AGENTS §7).
- Al cerrar cada spec: tildar en `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md` en el
  mismo commit (AGENTS §8).
- Video nunca se persiste salvo el clip de un disparo (Artículo V). El buffer vive solo en memoria.

## 4. Decisiones abiertas — necesito tu respuesta antes de 01

Cada una trae una recomendación. Ninguna está en un Spec ni en la Tabla de Tiempos; por eso no se
deciden en silencio.

| # | Decisión | Recomendación | Por qué |
|---|----------|---------------|---------|
| D1 | **Qué video clasifica cada cliente** | Cada cliente clasifica **el video remoto que recibe** (cámara y pantalla compartida del otro). El `detectadoId` es el dueño de ese track. | Protege al menor aunque el Tutor use un cliente modificado: el clasificador que importa corre en el dispositivo del menor. Clasificar solo el video propio se anula apagándolo. |
| D2 | **¿Se clasifica también la pantalla compartida?** | Sí, con el mismo umbral. | Si no, compartir pantalla es un bypass trivial del kill-switch. |
| D3 | **Intervalo entre clasificaciones** | 500 ms por track (ADR-M3-01 dice 250–500 ms). Va a la Tabla de Tiempos. | El extremo lento del rango cuida al gama media; 07 lo ajusta con datos. |
| D4 | **Cuándo una detección está "confirmada"** | `Porn + Hentai ≥ 0,85` en **3 muestras consecutivas** del mismo track (≈1,5 s con D3). `Sexy` no cuenta. | Un frame aislado no corta una clase. BR-KS-04 excluye calibrar en el MVP: el número es fijo y se revisa con datos del piloto. |
| D5 | **Plazo para responder la pregunta de la rama adultos** | 60 s. Sin respuesta, se trata como "No": se restaura el video y queda registrado. | Hoy no hay plazo: la confirmación queda pendiente para siempre. Entre adultos no aplica el fail-closed del Art. II. |
| D6 | **El clip de evidencia lleva audio** | **No**, solo video. | Artículo V: BR-KS-01 habla de video; el audio no lo necesita ninguna regla. |
| D7 | **Tope de bitrate del clip** | 1 Mbps (≈3,75 MB en 30 s), bajo el límite de 5 MB de subida. | Evita tocar `max-file-size`, que protege a las demás subidas. |
| D8 | **Falla del clasificador a mitad de clase con menor** | El cliente que lo perdió sale de la sala y avisa al backend; entra por el camino de conectividad que ya existe (US-4/US-5). Sin vigilante en el backend. | US-6bis ya lo define así. Un vigilante en el backend no agrega garantía: un cliente modificado puede fingir el latido igual (ADR-M3-02). |

## 5. Lo que este chunk NO hace

- Pizarra con menor: sigue apagada (ADR-M3-06, enmienda v2.5). Analizarla con el clasificador es otro
  trabajo, fuera de T-M3-06.
- Botón de pánico manual: excluido del MVP (`Spec_M3` §6).
- Calibrar falsos positivos (BR-KS-04).
- Los hallazgos de las pruebas que no tocan a menores: van por su cuenta.
