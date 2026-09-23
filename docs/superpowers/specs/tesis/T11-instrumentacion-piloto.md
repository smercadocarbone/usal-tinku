# T11 — Instrumentación de los indicadores del piloto

**Branch:** `tesis/instrumentacion-piloto` · **Riesgo:** bajo · **Bloqueada por:** — (PT8 ya resuelta)

## 1. Problema (verificado al 2026-09-23)

La hipótesis de la tesis se responde con cuatro indicadores medidos en el piloto (Cap. 2,
tabla de indicadores; Cap. 7), y el sistema **no mide ninguno** de forma completa. Además, el
análisis de riesgos definió disparadores que se miden mensualmente en operación.

| ID | Indicador | Umbral | Qué hay hoy |
|----|-----------|--------|-------------|
| I-01 | Aceptación de las sugerencias del matching | > 60 % | `BusquedaController` devuelve resultados, pero no se registra qué se mostró ni qué se eligió |
| I-02 | Calidad percibida de la sesión (1 a 5) | ≥ 4,0 | **Existe:** estrellas 1–5 en M7 (`CalificacionService`) |
| I-03 | Sesiones entre provincias distintas, con una del interior | ≥ 1 | `Usuario` **no tiene provincia** |
| I-04 | NPS del piloto | positivo | No existe |
| S-04 | Adopción del adicional de resumen | 40 % (supuesto) | Depende de T09 |
| R-02 | Recompra dentro de la plataforma | ≥ 50 % | No existe |
| R-04 | Sesiones anualizadas | ≥ 5.100 / año | Derivable de reservas |

## 2. Decisión resuelta (PT8 — 2026-09-23)

Una vez por usuario al cierre del piloto, in-app, pregunta 0–10.

## 3. Implementación

1. **I-01:** tabla `matching.busquedas_resultados` (búsqueda, tutores mostrados en orden). Al
   crear una Reserva desde una búsqueda, el frontend manda el `busquedaId`; la Reserva lo guarda.
   Aceptación = reservas con tutor dentro de los resultados mostrados / búsquedas con al menos
   un resultado. **Minimización:** no guardar el texto libre de la búsqueda más allá del
   piloto — plazo en la Tabla de Tiempos (A3, **PARAR** si no hay fila).
2. **I-03:** campo `provincia` en `Usuario` (lista cerrada de las 24 jurisdicciones), pedido en
   el registro; opcional para cuentas existentes, que lo completan en su perfil.
3. **I-04:** tabla `nps_respuestas` (usuario, puntaje 0–10, fecha), un endpoint y un modal
   in-app según PT8. Una respuesta por usuario.
4. **Panel de M8 — "Indicadores del piloto":** I-01 a I-04, adopción del adicional (S-04),
   recompra (reservas de un mismo alumno con el mismo tutor después de la primera / primeras
   reservas) y sesiones anualizadas. Solo lectura, rol Admin (gate existente).

## 4. Tests (RED primero)

1. `reservaDesdeBusqueda_registraAceptacion` y `reservaSinBusqueda_noCuenta`.
2. `indicadorI03_cuentaSesionesInterprovinciales`.
3. `nps_unaRespuestaPorUsuario_409AlRepetir`.
4. `panelIndicadores_soloAdmin_403ParaOtrosRoles`.

## 5. Criterios de aceptación

- Suite verde. Los valores del panel coinciden con un cálculo manual sobre datos de prueba.
- Specs de M2, M1 y M8 actualizados (campos y panel nuevos).
