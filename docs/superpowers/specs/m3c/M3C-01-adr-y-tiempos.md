# M3C-01 — ADR-M3-07 y tiempos del kill-switch en el cliente

**Estado:** HECHA (2026-10-14) · **Tarea:** T-M3-06a

Se dejó escrito:

- `docs/adr/ADR-M3-07.md`, en estado Aceptado. Cubre:
  - cada cliente clasifica el video remoto (cámara y pantalla);
  - el umbral 0,85 con `Porn+Hentai+Sexy` cuando hay un menor y `Porn+Hentai` entre adultos;
  - la confirmación con 3 muestras consecutivas;
  - el clip de solo video a 1,2 Mbps;
  - los reintentos con el video oculto si hay un menor;
  - el plazo de 60 s de la rama adultos.
- `docs/Tabla_Tiempos_Tinku.md`: 8 filas marcadas `ADR-M3-07`. Dos están como "propuesto 2026-10-14"
  (carga máxima de 20 s y 3 s sin respuesta) y las puede ajustar el dueño. Si cambian, solo se toca la
  Tabla y la constante en `frontend/src/lib/killswitch/config.ts`.
- `docs/Constitucion_Tinku.md`, Registro de Decisiones Técnicas: la fila del clasificador remite a
  ADR-M3-07.
- `docs/specs/Spec_M3_Aula_Virtual.md`: FR-AULA-003, 004 y 010 remiten a ADR-M3-07.
- `docs/adr/ADR-M3-01.md`: anexo con la latencia medida en Chromium con la CPU frenada.

No queda nada por hacer en esta spec.
