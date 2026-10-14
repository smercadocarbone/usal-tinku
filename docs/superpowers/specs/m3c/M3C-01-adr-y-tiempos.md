# M3C-01 — ADR-M3-07 y tiempos del kill-switch en el cliente

**Branch:** `chunk/m3-c` · **Riesgo:** bajo (solo documentos) · **Bloqueada por:** respuesta a D1–D8 de `00-LEEME-m3c.md`

## 1. Problema

ADR-M3-01 decidió el modelo (NSFWJS sobre TF.js, on-device) pero no qué video se clasifica, cuándo una
detección cuenta, ni qué pasa en los bordes. Esas decisiones tocan el Artículo II y no están en ningún
Spec. AGENTS §2 y §7 piden que una decisión así viva en un ADR antes de construir encima.

Además, ningún tiempo del clasificador está en `Tabla_Tiempos_Tinku.md`, que es la única fuente de
verdad para plazos.

## 2. Implementación

1. `docs/adr/ADR-M3-07.md` — "Kill-switch en el cliente: qué se clasifica, cuándo se confirma y cómo
   falla". Estado **Aceptado** con la fecha de tu respuesta. Contenido, con lo que decidas en D1–D8:
   - Contexto: ADR-M3-01, ADR-M3-02 (el disparo es una afirmación del cliente), US-6/US-6bis/US-7.
   - Decisión por cada D, con la alternativa descartada.
   - Consecuencias: el clasificador que protege al menor corre en el dispositivo del menor (D1), y por
     eso US-6bis aplica sobre todo a ese dispositivo.
   - Riesgos aceptados: un cliente modificado puede no disparar nunca (el mismo límite que ADR-M3-02
     acepta para el disparo falso); falsos positivos sin calibración (BR-KS-04).
2. `docs/Tabla_Tiempos_Tinku.md`, filas nuevas (módulo M3):
   - Intervalo de clasificación por track (D3).
   - Ventana de confirmación de una detección (D4, en muestras y en segundos).
   - Plazo de respuesta de la rama adultos (D5) y qué pasa al vencer.
   - Tolerancia sin latido del clasificador antes de tratarlo como caído (M3C-03 propone 2 intervalos
     de D3; se confirma con D8).
3. `docs/Constitucion_Tinku.md`, Registro de Decisiones Técnicas: ampliar la fila del clasificador con
   "ver ADR-M3-07". No es una enmienda: no cambia ningún artículo. Si al escribirlo resulta que sí lo
   cambia, parar y preguntar (AGENTS §8 exige actualizar este archivo en el mismo commit).
4. `docs/specs/Spec_M3_Aula_Virtual.md`: en FR-AULA-003, FR-AULA-004 y FR-AULA-010, una línea que
   remita a ADR-M3-07. Sin reescribir las historias.

## 3. Tests

No aplica (documentos). Criterio de revisión: cada número del ADR aparece también en la Tabla de
Tiempos o es un umbral sin unidad de tiempo (D4, D7).

## 4. Criterios de aceptación

- ADR-M3-07 en `Aceptado`, con D1–D8 resueltas.
- Tabla de Tiempos con las filas nuevas.
- Ningún número de tiempo de las specs 02–05 queda fuera de la Tabla.

## 5. NO tocar

- ADR-M3-01, ADR-M3-02 y ADR-M3-03: se referencian, no se editan.
- El backend.
