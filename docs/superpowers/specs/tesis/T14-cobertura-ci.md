# T14 — Medición de cobertura de código en CI

**Branch:** `tesis/cobertura` · **Riesgo:** bajo · **Bloqueada por:** ADR previo (A5)

## 1. Contexto

La metodología de la tesis (Cap. 2) fija una **cobertura mínima del 70 %** para las pruebas
unitarias, y el Cap. 7 necesita el valor medido. Hoy no se mide: no hay JaCoCo ni otro plugin de
cobertura en `backend/pom.xml`.

## 2. Implementación

1. **ADR-000-05** — plugin `jacoco-maven-plugin` (dependencia de build, A5). Alternativa
   descartada: no medir y declararlo como limitación en la tesis.
2. Configurar JaCoCo para que genere el reporte en `mvnw test`, **sin** umbral que corte el
   build en esta primera etapa (primero se mide, después se decide el umbral).
3. `ci-backend.yml`: publicar el reporte como artefacto y el porcentaje de líneas en el resumen
   del job.
4. Registrar el primer valor medido en `docs/Tasks_Tinku_Implementacion.md` (con fecha) para
   citarlo en el Cap. 7 de la tesis.

## 3. Criterios de aceptación

- La suite sigue verde y el tiempo del build no crece más de un 10 %.
- El CI muestra el porcentaje de cobertura del backend.
