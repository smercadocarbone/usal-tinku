# Constitución del Proyecto — Tinku

> Este documento gobierna todas las decisiones posteriores. Ningún Spec, Plan o Task de una feature puntual puede contradecirlo. Se modifica solo mediante enmienda explícita y versionada (ver Artículo XII), nunca implícitamente.
>
> **Distinción importante:** los Artículos son principios durables — cambiarlos requiere una enmienda formal. La sección final ("Registro de Decisiones Técnicas Actuales") son elecciones de proveedor/tecnología concretas — pueden cambiar vía un ADR normal, sin tocar este documento.

**Versión:** 2.0
**Contexto del equipo (informativo, no es una regla en sí misma):** actualmente 1 desarrollador, presupuesto de infraestructura USD 0-100/mes durante desarrollo. Los principios de abajo están pensados para este contexto, pero siguen siendo buena práctica aunque el equipo crezca.

---

## Parte I — Principios de Negocio

### Artículo I — La confianza reemplaza a la verificación estatal ausente
Tinku no tiene acceso a verificación de antecedentes penales. La Credencial Académica del Tutor, la calificación explícita y el kill-switch **son**, en conjunto, el mecanismo de confianza que reemplaza esa ausencia. Ninguna decisión de producto puede debilitar este conjunto para ganar velocidad de desarrollo o fricción de onboarding.

### Artículo II — La seguridad del menor prevalece sobre cualquier feature o métrica de negocio
Ante cualquier conflicto entre una decisión de producto y la seguridad de un usuario menor de edad, prevalece la segunda. Ningún flujo que involucre a un menor puede depender exclusivamente de que el propio menor confirme o niegue una situación de riesgo en tiempo real.

### Artículo III — Transparencia de precio para el Estudiante
El precio que ve el Estudiante/Adulto Responsable es siempre el precio final. La comisión de plataforma se descuenta del lado del Tutor, nunca aparece como un cargo adicional visible — la simplicidad del precio importa más que la transparencia de cómo se reparte internamente.

### Artículo IV — Accesibilidad económica sobre maximización de ingresos de corto plazo
Ante una decisión que mejora el margen de Tinku pero encarece o complica el acceso de familias de bajo poder adquisitivo, se prioriza el acceso. La comisión de plataforma y el precio de referencia regional deben revisarse con esta prioridad, no solo con criterio financiero.

### Artículo V — Minimización de datos
No se persiste ningún dato — en particular video — más allá de lo estrictamente necesario para cumplir una regla de negocio ya aprobada. Toda nueva necesidad de retención de datos debe justificarse explícitamente contra la Ley 25.326 antes de implementarse, nunca incorporarse "por las dudas".

### Artículo VI — Control de alcance del MVP
El alcance del MVP está cerrado en nueve módulos (Identidad y Perfiles, Motor de Matching, Aula Virtual, Reservas y Agenda, Motor de Pagos, Resumen Automático, Calificaciones y Reputación, Panel de Administración, Denuncias y Seguridad). Ninguna funcionalidad nueva se incorpora sin una enmienda explícita a este documento.

---

## Parte II — Principios Técnicos

### Artículo VII — Simplicidad ante todo
Toda decisión técnica se justifica contra el tamaño real del equipo y el presupuesto — no contra "buenas prácticas" genéricas de empresas de otra escala. Ante dos alternativas que cumplen el mismo requisito, se elige la más simple salvo justificación explícita en un ADR.

### Artículo VIII — Monolito modular
El sistema es un monolito modular en el backend elegido, organizado en los nueve módulos del Artículo VI, con límites de dominio claros. Está prohibido crear microservicios propios adicionales salvo justificación técnica explícita documentada como ADR (no una preferencia de estilo).

### Artículo IX — Comunicación entre módulos
Síncrona, dentro del mismo proceso, para flujos que requieren respuesta inmediata. Eventos de dominio en memoria para efectos secundarios de un solo disparo con múltiples reacciones. Prohibido introducir un message broker externo mientras no cambie una premisa fundamental de escala del proyecto.

### Artículo X — Persistencia de estado crítico
Ningún timeout de negocio (escrow, aprobaciones, kill-switch, cancelaciones) puede vivir en memoria volátil. Todo timeout de seguridad o financiero se implementa con un scheduler persistido en base de datos, para sobrevivir a un reinicio o redeploy.

### Artículo XI — Mitigación de riesgo técnico antes que velocidad de feature
El componente de mayor riesgo técnico del proyecto (clasificador on-device del kill-switch) se prototipa (spike) antes de comprometerse a fechas de entrega sobre el resto del sistema.

---

## Artículo XII — Gobernanza de este documento

Esta Constitución se modifica exclusivamente mediante una enmienda explícita, versionada (se incrementa el número en el encabezado) y con su justificación registrada. Ningún Spec ni Plan de una feature puntual puede introducir un cambio a este documento de forma implícita.

---

## Registro de Decisiones Técnicas Actuales

> Esto NO es ley constitucional — son las elecciones vigentes hoy. Cambiar una fila es un ADR normal, no requiere enmendar la Constitución.

| Capa | Elección actual | Estado |
|---|---|---|
| Backend | Java + Spring Boot | Decidido, evaluado contra Go y Node.js |
| Base de datos | PostgreSQL, un solo motor, schemas por módulo | Decidido |
| Cache / tiempo real propio | Redis (Upstash) | Decidido |
| Videollamada | LiveKit Cloud | Decidido |
| Pagos | MercadoPago (Marketplace / escrow), con sandbox de test users para desarrollo | Decidido |
| Motor de matching | Python (sentence-transformers + FAISS), proceso separado | Decidido |
| Transcripción + resumen de sesión | **Revisado en esta sesión:** unificar en una sola llamada al LLM elegido (GPT-4o o Gemini 2.0 Flash, ambos aceptan audio como input directo) — se elimina Whisper como dependencia separada | **Actualizado — reemplaza la fila "Whisper"** |
| LLM (resumen y transcripción) | GPT-4o vs. Gemini 2.0 Flash | Pendiente — ADR |
| Frontend | Next.js + React (justificado por SEO en páginas públicas de Tutores) | Decidido, con alternativa más liviana (Vite + React) documentada si el SEO deja de importar |
| Scheduler de jobs | Persistido en base de datos (ej. Quartz sobre Spring Boot) | Decidido |

---

## NFRs no negociables

- **Disponibilidad:** 95% mensual durante el piloto.
- **Seguridad:** TLS 1.2+; hashing bcrypt/argon2; log de auditoría de toda acción de Admin.
- **Privacidad:** cumplimiento de la Ley 25.326, con especial cuidado en datos de menores.
- **Rendimiento:** latencia de videollamada <200ms; video con ajuste automático y continuo de calidad, sin "modo degradado" visible.
- **Mantenibilidad:** sostenible por 1-2 desarrolladores, sin conocimiento tácito no documentado.

---

*Fin de la Constitución v2.0. Próximo paso: Spec de M1 — Identidad y Perfiles.*
