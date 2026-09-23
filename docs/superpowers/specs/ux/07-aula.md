# UX-07 — Aula virtual: lobby y sala

**Branch:** `ux/aula` · **Precondición:** UX-01 y UX-02 mergeadas.
Captura "antes": `capturas-antes/estudiante__aula_ID__desktop.png`.

> **Seguridad primero.** El aula es donde un menor está en videollamada con un adulto. Cualquier
> cambio acá respeta el Artículo II y **no toca** la lógica del kill-switch, del corte ni de los
> tokens de LiveKit (FASE 1: `ADR-M3-02`, `ADR-M3-03`). Esto es presentación.

## 1. Lobby (antes de entrar)

**Hoy:** fondo oscuro, preview de cámara, un aviso "No pudimos acceder a la cámara o al micrófono",
botones de cámara/micrófono y "Unirme a la clase". **No dice qué clase es.** El error de permisos no
explica cómo darlos. Para una sesión **ya terminada** ofrece "Unirme" igual (el backend ahora
responde 422 al pedir el token de una sesión cortada o finalizada).

**Propuesta:**
- **Qué clase es:** tutor/alumno, materia, `FechaHora`, duración y el reloj ("Empieza en 3 min" /
  "En curso desde las 18:02").
- **Dispositivos:** selector de cámara, micrófono y parlante, medidor de nivel de micrófono y botón
  "Probar sonido".
- **Permisos denegados:** instrucciones específicas por navegador (el candado de la barra de
  direcciones → Permitir cámara), y botón "Volver a intentar".
- **Estados del lobby** según la sesión: todavía no se abrió la sala (antes de T-5: "La sala se abre
  5 minutos antes"), lista, en curso, **terminada** ("Esta clase terminó" + ir al detalle/calificar)
  y **cortada por seguridad** (texto neutro, sin detalles del motivo, y qué pasa después: "La clase se
  cortó. Si sos el adulto responsable, vas a ver el estado del caso en tu cuenta").
- **Aviso de monitoreo** visible y honesto para los dos participantes: la clase tiene protecciones
  automáticas para chicos (cuando T-M3-06 exista). **No** afirmes que hay un clasificador activo
  mientras no esté implementado.

## 2. Sala (durante la clase)

Revisar la pantalla actual en uso real (con dos navegadores y una sesión en curso en el stack local)
antes de rediseñar, y aplicar el sistema visual:
- Video principal del otro participante con su **nombre de pila** (claim `name`, FASE 1; nunca el
  DNI ni el UUID) y la propia cámara en miniatura, reubicable.
- Controles grandes y claros: micrófono, cámara, compartir pantalla, **Finalizar clase** (con
  confirmación: finalizar es irreversible).
- Indicador de calidad de conexión (US-4, degradación) y aviso cuando el otro participante se
  desconecta ("Jorge se desconectó. Esperando que vuelva…").
- Tiempo transcurrido y tiempo restante.
- En mobile: vertical, controles en la parte inferior, y aviso de "rotá el teléfono para compartir
  pantalla" si hace falta.

## Criterios de aceptación

- Probado con dos usuarios reales en el stack local (tutor y alumno), incluyendo: permisos
  denegados, sesión terminada, desconexión de uno y reconexión.
- Capturas "después" de cada estado del lobby y de la sala, desktop y mobile.
- **Cero cambios** en `SesionService`, `LiveKitService`, `CierreSalaService` y en los endpoints del
  aula (verificable con `git diff --stat` del PR).
