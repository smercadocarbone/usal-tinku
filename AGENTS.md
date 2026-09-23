# AGENTS.md — Tinku

> Este archivo se lee automáticamente en cada sesión de opencode (agente primario o subagente). No repitas estas reglas en el prompt de cada tarea — ya están acá. Si una tarea puntual entra en conflicto con algo de este archivo, el archivo gana; se detiene y se pregunta, no se decide en silencio.

## 0. Qué es este proyecto

Tinku es un marketplace de tutorías académicas online (Argentina). Monolito modular en Java + Spring Boot, 9 módulos de dominio, 1 desarrollador, presupuesto de infra USD 0-100/mes durante desarrollo. Hay menores de edad como usuarios reales del sistema — esto no es un detalle de producto, es la restricción más importante de todo el proyecto.

Documentos fuente (no dupliques su contenido en código ni en comentarios — referencialos):

- `/docs/Constitucion_Tinku.md` — principios que gobiernan toda decisión. Este AGENTS.md es un resumen operativo de esa Constitución, no la reemplaza.
- `/docs/specs/Spec_M{1-9}_*.md` — el spec funcional del módulo activo. Cargá SOLO el spec del módulo en el que estás trabajando, más los FR/BR puntuales de otros módulos que ese spec referencia — no los 9 completos.
- `/docs/Tabla_Tiempos_Tinku.md` — única fuente de verdad para CUALQUIER plazo (15min, 24hs, 48hs, etc.). Si necesitás un número de tiempo y no está en esta tabla, no lo inventes: preguntá.
- `/docs/Tasks_Tinku_Implementacion.md` — checklist de tareas, tildá cada `[ ]` → `[x]` a medida que la completás y verificás. Es la memoria persistente entre sesiones — actualizala siempre, incluso si la sesión se corta a mitad de camino.

## 1. Reglas de arquitectura — no negociables sin ADR explícito

1. **Monolito modular.** Un paquete Java por módulo (`identidad`, `matching`, `aula`, `reservas`, `pagos`, `resumen`, `reputacion`, `admin`, `seguridad`). Prohibido crear un microservicio propio nuevo. Única excepción ya decidida: el motor de matching (Python, proceso separado) — no repitas ese patrón en otro módulo sin ADR.
2. **Comunicación entre módulos:** llamada síncrona in-process para flujos que necesitan respuesta inmediata; evento de dominio en memoria (`ApplicationEventPublisher` o equivalente) para efectos secundarios de un solo disparo con múltiples reacciones. Prohibido introducir un message broker externo (Kafka, RabbitMQ, SQS...).
3. **Ningún timeout de negocio en memoria.** Escrow, kill-switch, cancelaciones, aprobaciones: todos con Quartz (u otro scheduler) persistido en la misma base. Si escribís un `Thread.sleep`, un `@Scheduled` sin persistencia, o un timer en memoria para algo que involucra plata o seguridad, parate y corregilo.
4. **Ante dos soluciones que cumplen el mismo requisito, la más simple.** No justifiques una decisión técnica con "buena práctica genérica" — justificala contra 1 desarrollador y USD 0-100/mes, o no la tomes.
5. **Minimización de datos.** No persistas video ni ningún dato más allá de lo que una regla de negocio ya aprobada necesita. El buffer del kill-switch es rotativo de 30s y se descarta salvo disparo — nunca grabación continua, bajo ninguna circunstancia ni como "feature futura".
6. **Alcance cerrado a 9 módulos.** Si una tarea te lleva a considerar una función que no está en ningún Spec, no la agregues — señalalo y seguí.

## 2. Cuándo un ADR es obligatorio (no seguir sin uno)

- Elegir o cambiar: proveedor de OCR, backend del índice semántico (pgvector vs. en memoria), proveedor de LLM (GPT-4o vs. Gemini 2.0 Flash), cualquier fila de la "Registro de Decisiones Técnicas" de la Constitución.
- Cualquier desviación de las reglas de la sección 1.
- El ADR se documenta como archivo (`/docs/adr/ADR-M{n}-{seq}.md`), nunca como comentario en código ni como decisión implícita en un PR.

## 3. Seguridad del menor (Artículo II) — se revisa en cada PR que la toque

- Ningún flujo con un menor puede depender exclusivamente de que el propio menor confirme o niegue algo en tiempo real.
- El menor tiene cuenta y sesión propias, pero no puede pagar, autorizar Tutores nuevos, ni presentar Denuncias — eso lo hace su Adulto Responsable en su nombre. Es una restricción de permisos a nivel de endpoint/autorización, no una limitación de cuenta.
- La rama de kill-switch con menor presente **nunca** continúa la sesión ni pregunta al menor si vio algo — corta directo. La rama de "ambos adultos" sí pregunta y puede continuar.
- Si una tarea te deja en duda sobre a cuál de estos dos casos aplica, tratala como si hubiera un menor — nunca al revés.

## 4. Convención de eventos de dominio

- Nombrá los eventos exactamente como aparecen en los Specs (`sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_estudiante`, `sesion.no_show_tutor`, `sesion.no_show_doble`, `sesion.killswitch_menor`, `sesion.killswitch_adultos`, `denuncia.registrada`, `denuncia.resuelta`). No los renombres "para que sea más claro" — otro módulo ya los espera con ese nombre exacto.
- Antes de emitir un evento nuevo que no esté en ningún Spec, o de cambiar el payload de uno existente, revisá qué otros módulos lo consumen (grep en el repo + en `/docs/specs/`) y documentá el cambio.

## 5. Testing

- Cada Historia de Usuario del Spec activo necesita al menos un test de integración que la ejercite de punta a punta — no alcanza con unit tests de la lógica interna.
- Los tests de "caso borde" que cada Spec ya resolvió en su tabla (ver sección "Casos Borde") son casos de test obligatorios, no opcionales.
- No mezclés la tarea de implementación con la tarea de test en el mismo commit si el chunk las separa explícitamente en `Tasks_Tinku_Implementacion.md`.
- **La suite corre SOLO con JDK 21.** El `maven-enforcer-plugin` exige `[21,22)`.
  Comando canónico: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test`
  desde `backend/`. Con otra JVM el build falla en el enforcer y **no corre ni un test** —
  y si pipeás la salida, el exit code puede dar 0 y parecer éxito. Siempre leer la línea
  `Tests run:` antes de afirmar que algo pasa.
- Baseline al 2026-09-23 (FASE 1 de la auditoría y fix de doble pago mergeados): **426 tests,
  0 failures, 0 errors, 0 skipped**. Si tu cambio baja ese número, borraste un test.

## 6. Tamaño y disciplina de las tareas

- Trabajá sobre las tareas tal como están agrupadas en el chunk que se te indique en el prompt — no adelantes tareas de otro módulo aunque las veas fáciles.
- Si una tarea depende de un ADR pendiente (marcado explícitamente en `Tasks_Tinku_Implementacion.md`), no avances con una elección implícita — pará y señalalo.
- Al terminar un chunk: tildar las tareas correspondientes en `Tasks_Tinku_Implementacion.md`, correr los tests, y dejar un resumen corto de qué se hizo y qué queda pendiente del módulo.

## 7. Disciplina de control de versiones

- Todo chunk se trabaja en su propio branch (`chunk/{id}`, ej. `chunk/m1-a`), nunca directo sobre `main`.
- Un chunk termina con al menos un commit que deja el repo en estado funcional (compila, tests del chunk pasan) — no dejes el branch a medio terminar sin comunicarlo explícitamente en el resumen.
- No asumas que código sin commitear "ya existía" — si encontrás algo en el working directory que no está en el historial de git, señalalo explícitamente en el resumen antes de construir encima. No lo adoptes en silencio como si fuera un chunk ya cerrado.
- Nunca hagas `git commit --amend` ni reescribas historia de un branch que ya se compartió/mergeó.
- **Una migración de Flyway/Liquibase ya aplicada nunca se edita.** Si algo de una migración anterior está mal o incompleto, se agrega una migración nueva que lo corrige — nunca se modifica el archivo `V{n}__*.sql` existente, aunque parezca más prolijo.
- Si al arrancar una tarea encontrás una decisión de código que debería tener un ADR (sección 2) y no lo tiene, no la "adoptes" en silencio: documentá el ADR retroactivamente antes de construir más encima, marcándolo explícitamente como decisión ya tomada en el código pero recién formalizada.

## 8. Gobernanza de este archivo

Este archivo refleja la Constitución v2.3. Si la Constitución se enmienda, este archivo se actualiza en el mismo commit que la enmienda — nunca de forma independiente ni implícita.

`docs/Tasks_Tinku_Implementacion.md` y `docs/Tasks_Tinku_Chunks.md` se actualizan juntos o no se actualiza ninguno — la divergencia entre los dos fue AUD-030.

## 9. Auditoría vigente (2026-09-21)

Hay una auditoría técnica independiente con 36 hallazgos (7 CRÍTICA, 1 ya cerrado) — el estado
vigente, fila por fila, está en `REGISTRO_FINDINGS.md`; no repitas estos números de memoria en
otro documento, citá esa tabla. Antes de trabajar sobre M3 (Aula), M5 (Pagos), M9 (Seguridad) o
el registro de identidad, leé el finding que corresponda.

- Informe: `docs/auditoria/2026-09-21-auditoria-independiente.md`
- Estado por finding: `docs/auditoria/REGISTRO_FINDINGS.md` — **se actualiza en el mismo
  commit que cierra un finding.**
- Plan de remediación: `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`

**Reglas que esta auditoría agrega:**
- Un finding se cierra con un commit + un test de regresión que falla ANTES del fix. No se
  cierra "por análisis".
- Si encontrás un `FIXME AUD-XXX` en el código, ese comentario describe un problema conocido
  y su fase de corrección. No lo borres sin cerrar el finding.
- Un javadoc puede estar describiendo el comportamiento DESEADO y no el real. Verificá contra
  el código antes de confiar en un comentario.
- De las decisiones de §4.4 del informe que necesitaban ADR, 2 quedaron deliberadamente sin
  escribir (DNI como `sub` del JWT; notificador como log) porque FASE 1/2 las revierte —
  documentar un ADR de una decisión que se va a deshacer sería peor que no tener ADR. Están
  trackeadas como `T-AUD-023` en el plan de remediación, no perdidas.
- Los inserts de comentario `FIXME AUD-XXX` de FASE 0 corrieron los números de línea que el
  informe y el plan citaban (ej. `Spec_M3` o `SesionService.java`). Si vas a localizar algo por
  una cita de línea de un documento anterior a esta fase, ubicalo por nombre de símbolo/método
  y confirmá con `rg`, no confíes en el número tal cual — puede estar corrido.
