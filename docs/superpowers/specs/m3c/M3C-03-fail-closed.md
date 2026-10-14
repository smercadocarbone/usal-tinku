# M3C-03 — Falla del clasificador con un menor presente (US-6bis, FR-AULA-010)

**Branch:** `chunk/m3-c` · **Riesgo:** alto (Artículo II) · **Bloqueada por:** M3C-02

## 1. Problema

`Spec_M3` FR-AULA-010 figura **NO IMPLEMENTADO**: `SesionService.obtenerToken()` no consulta ningún
estado del clasificador y la sala se habilita siempre. US-6bis exige:

- con un menor, si el clasificador no carga, **no se entra**;
- si deja de responder ya iniciada la clase, se trata como **corte por conectividad** del lado de quien
  lo perdió;
- entre adultos, no bloquea y solo se registra.

## 2. Implementación

### Backend

1. `TokenSesionResponse` gana `hayMenor` (boolean). Hoy el cliente lo deduce de
   `pizarraHabilitada`, que es una regla distinta y puede cambiar sola. El aula no debe depender de
   esa coincidencia.
2. `POST /api/sesiones/{id}/token` gana el parámetro `clasificadorListo` (boolean, default `false`).
   Si la sesión tiene un menor y llega en `false`, responde **409** `CLASIFICADOR_NO_LISTO`, sin
   emitir token.
   - Es una afirmación del cliente, igual que el disparo (ADR-M3-02): un cliente modificado puede
     mentir. Sirve para que el cliente legítimo nunca entre sin protección, y deja un registro.
   - El orden en el cliente es: pedir datos de la sesión (`hayMenor`) → cargar el clasificador →
     pedir el token. Para eso `GET /api/sesiones/por-reserva/{reservaId}` (que ya existe) devuelve
     también `hayMenor`.
3. `POST /api/sesiones/{id}/clasificador/caida`: el participante avisa que perdió el clasificador.
   Solo registra el evento: quién, cuándo y si había menor. No corta la sesión: el corte lo produce
   el cliente al salir de la sala (punto 6), por el camino de conectividad que ya existe (D8).
   - Va a una tabla nueva solo si no hay dónde registrarlo. Antes, revisar si alcanza con la auditoría
     de M8 o con un log estructurado. Si hace falta tabla: migración nueva, nunca editar una existente.
4. Tests de integración en `backend/` (ver §3).

### Frontend (`aula/[id]/page.tsx`)

5. **Antes de unirse:** si `hayMenor`, se carga el clasificador (M3C-02) antes de habilitar
   "Unirse". Si falla, pantalla de error clara ("Tu dispositivo no puede activar la protección de la
   clase. Probá con otro navegador o dispositivo.") y **no se pide token**. Sin menor, se intenta
   cargar igual. Si falla, se entra y se llama a `/clasificador/caida` para dejar registro.
6. **A mitad de clase:** si no hay `latido` del clasificador durante **2 intervalos de D3** seguidos
   (el número final sale de la Tabla de Tiempos, M3C-01), o si el runtime de TF.js tira un error:
   - con menor: `room.disconnect()`, `POST /clasificador/caida` y pantalla "Se cortó la clase porque
     se perdió la protección". Nunca se sigue mirando video del otro sin clasificar.
   - sin menor: solo `POST /clasificador/caida` y se sigue.
7. El temporizador del punto 6 es del cliente y no decide plata: corta la conexión propia. El efecto
   de negocio (interrupción, reembolso si es antes del 50 %) lo resuelven los jobs persistidos que ya
   existen. No viola AGENTS §1.3.

## 3. Tests (RED primero)

Backend:
1. `token_conMenor_sinClasificadorListo_409` (Spec_M3 caso borde 8).
2. `token_conMenor_conClasificadorListo_ok`.
3. `token_sinMenor_sinClasificadorListo_ok` (US-6bis, adultos).
4. `clasificadorCaida_tercero_403`, `clasificadorCaida_participante_registra`.
5. Regresión: los tests de pizarra y de grabación de audio siguen verdes.

Frontend (Playwright, API mockeada como en `tests/aula/pizarra.spec.ts`):
6. Con `hayMenor:true` y el modelo devolviendo 404 → no se llama a `/token`, se ve el error.
7. Con `hayMenor:true`, clasificador que deja de latir → se desconecta y se llama a `/clasificador/caida`.
8. Con `hayMenor:false` y el modelo en 404 → entra y llama a `/clasificador/caida`.

## 4. Criterios de aceptación

- FR-AULA-010 en `Spec_M3` pasa de "NO IMPLEMENTADO" a implementado, con referencia al commit.
- Caso borde 8 de `Spec_M3` cubierto por los tests 1, 6 y 7.

## 5. NO tocar

- La lógica de conectividad y corte <50 % (US-4/US-5): se reutiliza tal cual.
- `pizarraHabilitada`: sigue con su regla.
