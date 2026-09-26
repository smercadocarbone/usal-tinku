# Mi cuenta del Tutor: estructura, materias y próximos pasos (2026-09-26)

Acompaña a T-UX-CUENTA (FR-ID-033, FR-MATCH-010, ADR-M3-05). Recoge lo que se hizo y lo que
se propone discutir.

## Estructura elegida
"Mi perfil" y "Mi cuenta" estaban separados: el Tutor no sabía dónde buscar cada cosa. Ahora todo
vive en **Mi cuenta**, con el menú de ajustes (sidebar en desktop, lista en celular).

**Tu cuenta**
- Perfil: datos y, para el Tutor, el checklist "qué te falta".
- Seguridad y acceso.
- Casos (solo si hay alguno).

**Como tutor**
- Presentación: foto y "Sobre mí", con **vista previa en vivo** "En la búsqueda" / "En tu perfil".
- Materias: asistente y catálogo.
- Precio.
- Credenciales: título o certificado.
- Clases con menores: interruptor "Doy clases a menores" más el CAP.

**Para aprender** (solo el Tutor que además toma clases o tiene chicos a cargo, ADR-M1-07)
- Buscar tutores.
- Mis chicos.

La barra principal del Tutor queda en 4 ítems: Mi agenda, Mis clases, Cobros y Mi cuenta. Agenda y
Cobros siguen afuera porque son destinos de uso diario, no ajustes.

## Materias: por qué el asistente usa el modelo de matching y no un chat con LLM
- **Sin costo.** Reusa el modelo de embeddings que ya corre para la búsqueda (sin LLM, USD 0).
- **Sin temas inventados.** Solo sugiere temas del **catálogo cerrado** (FR-MATCH-006), así que el
  matching sigue funcionando igual: nunca aparece un tema que no existe.
- **Parece un chat, pero es un solo paso.** El Tutor cuenta qué enseña, recibe temas sugeridos, los
  marca, y puede seguir contando.
- **Hay alternativa manual.** El catálogo completo sigue abajo para quien prefiere elegir a mano.
- **Un LLM conversacional** (preguntas de seguimiento: nivel, años, enfoque) se puede sumar después
  sobre la misma pantalla. Cuesta por uso y necesita ADR (proveedor de LLM).

## Propuestas para discutir (no implementadas)
1. **Nivel por tema.** Hoy el tema es de un curso. Se podría declarar "hasta qué nivel" por materia
   (lo pide también ADR-M1-06, especialidades).
2. **Modalidad y enfoque.** Por ejemplo: apoyo escolar, preparación de examen de ingreso, finales
   universitarios. Se muestran como etiquetas en la tarjeta y suman al texto del embedding.
3. **Plantillas de "Sobre mí".** Tres esqueletos para empezar ("Cómo doy clases", "A quién ayudo",
   "Mi experiencia") y un contador de calidad (largo, sin datos de contacto).
4. **Checklist con porcentaje.** "Tu perfil está al 80 %", con el próximo paso destacado arriba de
   Mi cuenta.
5. **Vista previa también en Materias y Precio.** Cómo queda la tarjeta con los temas y el precio
   elegidos.
6. **Disponibilidad rápida.** Plantillas de horarios ("tardes de semana", "sábados a la mañana") en
   Mi agenda.
7. **Selector de fecha en más lugares.** El componente ya soporta `min`/`max`. Se puede usar para
   reprogramar una clase o filtrar "Mis cobros" por mes (hoy no hay filtro de fechas).
