# Spec: M2 — Motor de Matching Semántico

**Módulo:** M2 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M1 (Usuarios con capacidad Estudiante/Adulto Responsable, Tutores habilitados, Autorización de Tutor), M7 (BR-MATCH-01: sombra temporal tras calificación de 1-2 estrellas), M9 (exclusión de Tutores suspendidos del matching)
**Alimenta a:** M4 (resultados de búsqueda disponibles para reservar)

---

## 1. Resumen

Este módulo conecta a un Estudiante (o un Usuario con capacidad Adulto Responsable buscando para un menor a cargo) con los Tutores más relevantes, combinando búsqueda semántica por IA con las restricciones de autorización que protegen a los menores.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Búsqueda de Tutores
*Como* Estudiante, *quiero* buscar Tutores describiendo lo que necesito, *para* encontrar rápido a alguien que me pueda ayudar sin conocer la jerga exacta del catálogo.

- **Dado** que ingrese una búsqueda en lenguaje natural, **cuando** el sistema la procese, **entonces** devuelve Tutores ordenados por relevancia semántica, no solo por coincidencia exacta de palabras.
- **Dado** que la búsqueda no tenga resultados relevantes, **cuando** eso ocurra, **entonces** el sistema lo comunica claramente, en vez de forzar resultados de baja calidad.

### US-2 — Búsqueda restringida para un perfil de menor
*Como* Usuario con capacidad Adulto Responsable, *quiero* que la búsqueda de mi hijo/a solo muestre Tutores que ya autoricé, *para* tener control total sobre quién puede aparecer como opción.

- **Dado** que la búsqueda la haga un menor logueado con su propia cuenta (creada por su Adulto Responsable, ver Spec de M1 — no autorregistrada), **cuando** se ejecute, **entonces** se filtra exclusivamente a los Tutores de la lista de Autorización de ese Adulto Responsable (FR-MATCH-004).
- **Dado** que no haya ningún Tutor autorizado todavía, **cuando** el menor busque desde su propia cuenta, **entonces** puede ver y buscar Tutores igual que cualquier perfil — sin distinción por edad —, y cada resultado no autorizado muestra un botón **"Solicitar autorización"** que notifica al Adulto Responsable (FR-MATCH-005).
- **Dado** que un Tutor esté en la lista de Autorización, **cuando** un Adulto Responsable lo marque como "no confiable" (M1, FR-ID-009), **entonces** deja de aparecer en las búsquedas de esa cuenta específicamente — este filtro aplica solo cuando busca la capacidad Adulto Responsable, no cuando la misma cuenta busca con su capacidad Estudiante (FR-MATCH-009).
- **Dado** que un Adulto Responsable revoque la autorización de un Tutor que tiene sesiones futuras agendadas con ese menor, **cuando** eso ocurra, **entonces** esas reservas se cancelan y reembolsan automáticamente (ver FR-RES-006 de M4) — este módulo deja de mostrar a ese Tutor de inmediato, sin esperar a que M4 procese la cancelación.

### US-3 — Salvaguarda temporal ante mala calificación
*Como* Tinku, *quiero* que un Tutor recién calificado mal no se siga sugiriendo activamente mientras el caso se asienta, *para* reducir el riesgo de una mala experiencia repetida en las horas siguientes, sin que esto sea una sanción.

- **Dado** que un Tutor reciba una calificación de 1 o 2 estrellas, **cuando** eso ocurra, **entonces** el sistema no lo expone ni lo sugiere en resultados de búsqueda durante las **24 horas siguientes** (BR-MATCH-01). Es un efecto automático y temporal — no constituye una sanción (eso es territorio de M9) y no afecta su Credencial ni su cuenta.

### US-4 — Ordenamiento influenciado por reputación implícita
*Como* Tinku, *quiero* que los Tutores más confiables aparezcan mejor posicionados, *para* que la calidad del match no dependa solo de la similitud semántica del texto.

- **Dado** dos Tutores con relevancia semántica equivalente, **cuando** se ordenen los resultados, **entonces** el que tenga mejores señales implícitas de reputación aparece primero (FR-MATCH-003), sin mostrar esas señales al usuario.
- **Dado** que un Tutor esté suspendido (Alerta de Seguridad activa, M9) o marcado "no confiable" para esa cuenta, **cuando** se ejecute la búsqueda, **entonces** se filtra **antes** del cálculo semántico, no después (FR-MATCH-007) — más eficiente, evita gastar cómputo en candidatos que de todas formas no se van a mostrar.

### US-5 — Catálogo cerrado de materias y niveles
*Como* equipo de Tinku, *quiero* que la búsqueda opere sobre un catálogo curado y cerrado, *para* que el matching semántico tenga categorías consistentes.

- **Dado** que un Tutor configure su perfil, **cuando** seleccione sus materias/niveles, **entonces** solo puede elegir de un catálogo cerrado basado en los niveles educativos oficiales de Argentina (primario desde los 6 años, secundario, universitario) y sus materias curriculares correspondientes (FR-MATCH-006).

### US-6 — Búsquedas guardadas
*Como* Estudiante, *quiero* guardar una búsqueda que ya usé, *para* no redactarla de nuevo cada vez.

- **Dado** que ejecute una búsqueda, **cuando** decida guardarla, **entonces** puede volver a ejecutarla más adelante con un clic, obteniendo resultados actualizados, no una lista congelada (FR-MATCH-008).

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-MATCH-001 | Búsqueda de Tutores por materia, nivel y disponibilidad, sobre catálogo cerrado y curado. |
| FR-MATCH-002 | Ordenamiento de resultados mediante motor de matching semántico basado en IA. |
| FR-MATCH-003 | El orden pondera señales implícitas de reputación del Tutor, sin exponerlas. |
| FR-MATCH-004 | Para un perfil de menor, solo se muestran Tutores de la lista de Autorización de su Adulto Responsable. |
| FR-MATCH-005 | Búsqueda disponible para todos los perfiles de menor sin distinción de edad; botón "Solicitar autorización" en resultados no autorizados. |
| FR-MATCH-006 | Catálogo cerrado de materias/niveles basado en niveles educativos oficiales de Argentina. |
| FR-MATCH-007 | Tutores suspendidos o marcados "no confiables" se filtran antes del cálculo semántico. |
| FR-MATCH-008 | Búsquedas guardadas, re-ejecutables con resultados actualizados. |
| FR-MATCH-009 | El filtro de "no confiable" es específico de la capacidad Adulto Responsable de la cuenta, no afecta su capacidad Estudiante. |

## 4. Reglas de Negocio Aplicables

- **BR-REP-01:** las señales implícitas influyen solo en el orden, nunca se exponen ni se fusionan con la calificación explícita.
- **BR-AUTH-01:** la lista de Tutores autorizados es persistente, no vence salvo revocación.
- **BR-MATCH-01 (nueva):** un Tutor calificado con 1-2 estrellas no se expone ni se sugiere durante las 24hs siguientes. Efecto automático, temporal, no es sanción.

## 5. Fuera de Alcance de este Spec

- El detalle de infraestructura del motor semántico (proceso Python separado) — Plan técnico.
- El flujo de Autorización de Tutor (ver Spec de M1).
- Las sanciones formales por mala conducta — M9.

## 6. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Todas las preguntas fueron resueltas.
- [x] Revisado contra la Constitución (Artículo II, Artículo IV — accesibilidad).

---

**Estado: APROBADO.** Listo para pasar al Plan técnico de M2.
