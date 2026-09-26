# ADR-M2-04 — Catálogo vivo: recomendación por área y temas sugeridos agregados

**Estado:** Aceptado (2026-09-26, pedido del dueño del producto). Agrega FR-MATCH-011/012
(Spec M2) y FR-ADM-009 (Spec M8). Migraciones V43 (`matching.temas_sugeridos`).

## Contexto
- El catálogo de temas es cerrado (FR-MATCH-006): el Tutor solo elige temas de ahí. Un
  alumno puede buscar cualquier cosa en texto libre, y si nadie da exactamente eso, hasta
  ahora no recibía nada.
- El catálogo **se va a actualizar siempre**: para eso hace falta saber qué se busca y no
  está, sin guardar las búsquedas de la gente (Constitución, minimización de datos) y sin
  cargar el sistema.

## Decisión
1. **Recomendación por área (FR-MATCH-011).** Si con texto libre ningún Tutor alcanza el
   mínimo de relevancia (ADR-M2-02), Java le pide al servicio Python los temas del catálogo
   más parecidos (`POST /temas-cercanos`, pgvector sobre `matching.temas.embedding`). Si el
   primero pasa `tinku.matching.score-minimo-area` (0,25), su materia y nivel son el área:
   se recomiendan los candidatos (ya filtrados por autorización, suspensión, menores) que
   dan temas de esa materia y nivel, o de esa materia en otro nivel si no hay. La respuesta
   lo marca (`porArea`, `area`) y el frontend lo aclara.
2. **Todo el catálogo embebido.** El recompute embebe los ~1.400 temas (~2 MB en pgvector),
   una sola vez; después solo los que cambien de texto. Se dispara también al arrancar.
3. **Temas sugeridos agregados (FR-MATCH-012 / FR-ADM-009).** Por cada búsqueda con texto
   sin Tutor directo, un `UPSERT` suma uno al contador de su texto normalizado (minúsculas,
   sin tildes, números de 4+ dígitos como `#`, 80 caracteres), con el área reconocida.
   - Sin usuario, sin fila por búsqueda, sin búsquedas de menores.
   - Tope de 500 filas: al pasarlo se descartan las menos pedidas.
   - El Admin solo ve lo pedido al menos `tinku.matching.temas-sugeridos.minimo-veces` (3)
     veces, agrupado por área; "Resuelto" lo borra cuando el catálogo se actualizó.
4. **Actualizar el catálogo** sigue siendo una migración nueva (`V{n}__..._temas.sql`), que
   el recompute embebe sola. No hay edición del catálogo desde el panel (fuera de alcance).

## Alternativas descartadas
- **Guardar cada búsqueda** (texto + usuario + fecha): más datos personales y más
  almacenamiento de lo necesario para saber qué temas faltan.
- **Agrupar búsquedas parecidas con embeddings:** más procesamiento por búsqueda; la
  normalización más el área reconocida alcanzan para el piloto.
- **Que el Tutor escriba temas libres:** rompe el catálogo cerrado (FR-MATCH-006).

## Consecuencias
- Costo por búsqueda sin resultado: una llamada más al servicio Python y un `UPSERT`.
- Un texto con datos personales tendría que repetirse 3 veces para que el Admin lo vea, y
  los números largos (DNI, teléfono) nunca se guardan.
- Tests: `CatalogoTemasIntegracionTest` (recomendación por área, fallback de nivel, tema no
  reconocido, contador y vista del Admin, menores no se registran, 403),
  `TemasSugeridosNormalizacionTest`, `test_main.py` (`/temas-cercanos`).
