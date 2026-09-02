# Plan Técnico: M3 — Aula Virtual

**Basado en:** Spec_M3_Aula_Virtual.md (aprobado)
**Stack (Constitución):** LiveKit Cloud (WebRTC), clasificador de contenido **on-device** (cliente, no backend), Quartz para timeouts.

---

## 1. Arquitectura del Componente

**Punto crítico de diseño (ya establecido en la Constitución, se reafirma acá porque es el que más fácil es de romper por accidente):** el clasificador de contenido del kill-switch corre **en el dispositivo del cliente** (navegador/app), no en el backend. El backend nunca recibe video en tiempo real — solo recibe: (a) tokens de acceso a LiveKit, (b) webhooks de eventos de sala, y (c) el clip de 30s únicamente cuando el kill-switch se dispara.

```
Cliente (video + clasificador local + buffer de 30s)
   │ genera token
   ▼
Backend (Java) ──solicita room──▶ LiveKit Cloud
   ▲ webhooks (join/leave/room_finished)
   │
   └── recibe el clip de evidencia SOLO si se dispara el kill-switch
```

## 2. Modelo de Datos (lógico)

### `sesiones_aprendizaje`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| reserva_id | UUID (FK → reservas.id) | 1 a 1 con la Reserva que la originó. |
| livekit_room_id | varchar, nullable | Null hasta T-5 (creación diferida). |
| estado | enum(`no_iniciada`, `en_curso`, `finalizada`, `finalizada_anticipada`, `interrumpida`) | |
| inicio_real, fin_real | timestamp, nullable | Para calcular el % de duración efectiva (regla del 50%). |
| duracion_efectiva_segundos | int, calculado | Usado por M6 para el umbral de 10 minutos. |

### `alertas_seguridad` (entidad separada de Denuncia, BR-KS-03)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| sesion_id | UUID (FK) | |
| rama | enum(`menor`, `adultos`) | |
| detectado_id | UUID (FK → usuarios.id) | Quién generó la detección. |
| clip_url | varchar | Referencia a storage — subida vía backend (no presigned URL, decisión ya tomada por consistencia de tráfico excepcional vs. continuo). |
| clip_retencion_hasta | timestamp | 30 días desde la resolución (BR-KS-02), extensible si hay apelación activa. |
| estado | enum(`pendiente_revision`, `resuelta_reactivacion`, `resuelta_baja`) | |

## 3. Flujos Técnicos Clave

### 3.1 Creación diferida de sala (US-1)
1. Job de Quartz programado a `horario_reserva - 5min` (ya definido en M4 al confirmar la Reserva).
2. Al ejecutarse: llama a la API de LiveKit para crear la room, genera tokens de acceso para Tutor y Estudiante, y notifica al frontend (vía WebSocket/polling) que el botón de unirse está habilitado.

### 3.2 No-show automático (US-2)
1. Job de Quartz a `horario_reserva + 10min`.
2. Al ejecutarse, consulta el estado de participantes vía la API de LiveKit (o el registro propio de eventos `participant_joined` recibidos por webhook hasta ese momento).
3. Según quién se haya unido o no, emite el evento correspondiente (`sesion.no_show_estudiante`, `_tutor`, o `_doble`) hacia M5.
4. **Cancelación del job si llega antes:** si ambos participantes se unen antes de T+10, el job se cancela explícitamente al recibir el segundo `participant_joined` — no se deja correr y descartar su resultado, se remueve del scheduler.

### 3.3 Kill-switch — recepción del evento del cliente
1. El clasificador corre en el cliente; el backend **no implementa ninguna lógica de visión por computadora**. El contrato entre cliente y backend es un único endpoint que el cliente llama cuando el clasificador local dispara.
2. El backend, al recibir esa llamada, determina la rama (`menor` si hay un perfil de menor en la sesión, `adultos` si no) **usando datos de M1**, no confiando en un flag que mande el cliente (evita que el cliente pueda mentir sobre qué rama aplica).
3. Rama `menor`: corta la sala vía API de LiveKit para ambos participantes, marca la Reserva como afectada, emite `sesion.killswitch_menor`, crea la fila en `alertas_seguridad`, dispara notificación al Adulto Responsable con ventana de 12hs (job de Quartz).
4. Rama `adultos`: no corta la sala — envía al cliente del detectado la instrucción de mostrar el placeholder estático (vía WebRTC track replacement o simplemente dejar de publicar el track de video), y al otro cliente la pregunta de confirmación. La respuesta del otro cliente vuelve por el mismo endpoint con un parámetro adicional (`confirmado: true/false`).

### 3.4 Buffer de evidencia (30s)
- **Vive enteramente en el cliente.** El backend no participa hasta que el clip ya está armado y se sube. No hay ninguna tabla ni proceso en el backend que "mantenga" el buffer — de existir, violaría la minimización de datos (Artículo V).
- Implementación en el cliente: `MediaRecorder` con `timeslice` corto (2-3s), cola circular en memoria de los últimos ~10-15 chunks, descartando los más viejos. Al dispararse el kill-switch, se concatenan los chunks retenidos en un solo blob y se sube al endpoint del backend.

### 3.5 Finalización (US-8)
1. Botón "Finalizar" → llamada directa que marca `sesiones_aprendizaje.estado = finalizada`, `fin_real = now()`, emite `sesion.finalizada`.
2. Si nadie lo presiona: job de Quartz a `horario_fin_agendado + 5min` ejecuta lo mismo automáticamente.
3. Corte de conexión sin "Finalizar": al recibir el evento de LiveKit de que ambos participantes se desconectaron, se espera la tolerancia de 5 minutos por si reconectan; si no, se marca `finalizada_anticipada` y se calcula el % de duración para decidir si aplica reembolso (US-5) o se trata como cierre normal (>50%, emite `sesion.finalizada` igual, según lo ya resuelto en el Spec).

## 4. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/sesiones/{id}/token` | Devuelve el token de LiveKit, solo si faltan ≤5min. |
| `POST` | `/api/sesiones/{id}/killswitch` | El cliente reporta la detección. Body: no incluye el clip todavía. |
| `POST` | `/api/sesiones/{id}/killswitch/evidencia` | Sube el clip (multipart), asociado a la Alerta ya creada en el paso anterior. |
| `POST` | `/api/sesiones/{id}/killswitch/confirmar` | Rama adultos: respuesta sí/no del otro participante. |
| `POST` | `/api/sesiones/{id}/finalizar` | Botón manual. |
| `POST` | `/api/webhooks/livekit` | Eventos de sala (join/leave/room_finished). |

## 5. ADRs de este Módulo

- **ADR-M3-01 (la más importante de todo el proyecto, Artículo XI de la Constitución):** modelo y framework de inferencia del clasificador on-device. Requiere un spike dedicado antes de comprometer fechas — candidatos a evaluar: modelos livianos pre-entrenados de clasificación NSFW ejecutables en el navegador (ej. vía TensorFlow.js) vs. entrenar uno propio (descartado por tiempo/presupuesto salvo que el spike demuestre que los pre-entrenados no alcanzan). El resultado de este spike puede obligar a revisar el timing (T+X de latencia de detección) documentado implícitamente en este Plan.

## 6. Trazabilidad con el Spec

FR-AULA-001 a 009 cubiertos. La dependencia más delicada de implementar correctamente es 3.3, punto 2 (la rama la decide el backend con datos propios, no el cliente) — es la única defensa real contra que alguien manipule el cliente para forzar la rama "adultos" (más permisiva) en una sesión donde en realidad hay un menor.

---

**Estado: Borrador de Plan técnico.** Bloqueado por ADR-M3-01 (spike del clasificador) antes de comprometer cronograma de implementación de este módulo.
