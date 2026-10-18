# T11b — Evaluación sin piloto: evaluación técnica + sesiones moderadas con usuarios

**Branch:** `feat/evaluacion-sin-piloto` · **Riesgo:** bajo · **Reemplaza a:** `T11-instrumentacion-piloto.md`
**Fecha:** 2026-10-18 · **Tarea:** T-TES-11b (`Tasks_Tinku_Implementacion.md` y `Tasks_Tinku_Chunks.md`)

## 0. Por qué existe esta spec

**Decisión del dueño (2026-10-18):** el piloto con usuarios reales del 27/10/2026 **no se hace**.
La hipótesis de la tesis se valida con dos fuentes:

1. **Evaluación técnica** reproducible sobre el sistema real (código de `main`, catálogo y Tutores del
   seed).
2. **Sesiones moderadas con usuarios** (5 a 8 personas adultas) sobre `tinku.site`, que es una demo
   pública con **datos de prueba**: sin plata real (pasarela en *bypass*), sin datos reales de
   terceros, sin menores reales.

T11 (instrumentación del piloto) nunca se registró como tarea ni se implementó: no existen
`matching.busquedas_resultados`, `nps_respuestas` ni `provincia` en `Usuario`. Esta spec la
reemplaza con una instrumentación **mínima** (apagada por defecto) que solo sirve para que las
sesiones moderadas dejen datos, y con un protocolo de evaluación que no depende de tráfico real.

Lo que **no** cambia: los indicadores siguen siendo I-01..I-04 (Cap. 2 de la tesis), con el mismo
sentido; cambia **la fuente del dato y el umbral**, que ahora tiene que ser alcanzable con una
muestra chica y declarada como tal (§3).

---

## 1. Evaluación técnica

Cada punto tiene: qué se mide, cómo se reproduce, y el **umbral** que reemplaza al indicador del
piloto. Los números se escriben solo cuando se midieron (nada estimado se presenta como medido).

### 1.a Matching (reemplaza la parte "técnica" de I-01)

**Set etiquetado.** `matching-service/tests/eval_relevancia/consultas_etiquetadas.csv`, 120 búsquedas
inventadas: 85 relevantes, 20 irrelevantes y 15 sin sentido (amplía las 40 de `CALIBRACION_busqueda.md`).
Columnas: `id, texto, grupo, relevantes`. **Resultados medidos: `docs/operacion/EVALUACION_tecnica.md`.**

- `grupo`: `relevante` (hay al menos un Tutor del seed que la cubre), `irrelevante` (pedido con sentido
  que ningún Tutor cubre: tango, cocina...), `sin_sentido` (teclado, números, letras sueltas).
- `relevantes`: DNIs de los Tutores del seed (`TutorSeedRunner.TUTORES`) separados por `|`. Se usa el
  DNI porque los UUID del seed son aleatorios en cada base.

**Criterio de etiquetado (documentado para que otro lo pueda repetir):**

1. Un Tutor es **relevante** para una búsqueda si **alguno de sus temas cargados** (tema puntual o
   trayecto completo del catálogo `docs/catalogo_seed/*.json`) **enseña lo que la búsqueda pide**,
   mirando materia **y nivel**: "fracciones para primaria" no marca a un Tutor que solo da
   secundario aunque tenga la materia; si la búsqueda no dice nivel, cuentan todos los niveles.
2. Pedidos genéricos de materia ("clases de física") marcan a **todos** los Tutores con esa materia.
3. Pedidos de tema puntual marcan solo a quienes tienen ese tema (o el trayecto que lo contiene).
4. No se usa el puntaje del modelo para decidir la etiqueta (sería circular): se etiqueta leyendo el
   catálogo, antes de correr el script.
5. `irrelevante` y `sin_sentido` no tienen relevantes: el resultado correcto es la lista vacía.
6. Las consultas no contienen datos personales (son inventadas).

**Métricas** (script `matching-service/tests/eval_relevancia/evaluar_ranking.py`, §5 de esta spec):

| Métrica | Sobre | Definición |
|---|---|---|
| precision@k (k = 1, 3, 5) | grupo `relevante` | relevantes en el top-k mostrado / k (si se muestran menos de k, sobre lo mostrado) |
| recall@k (k = 1, 3, 5) | grupo `relevante` | relevantes en el top-k / total de relevantes etiquetados |
| MRR | grupo `relevante` | promedio de 1/posición del primer relevante (0 si no aparece) |
| Tasa de "sin resultados" | por grupo | consultas en que **ningún** Tutor supera el umbral vigente (`MATCHING_SCORE_MINIMO`, 0,46) ni el corte relativo (0,25) |

El "top-k mostrado" aplica el mismo corte que el backend (absoluto 0,46 + relativo 0,25 del mejor,
`MatchingOrquestador`), así que la métrica mide lo que ve el usuario, no el ranking crudo.
La validación de texto del backend (menos de 3 letras → sin resultados) también se replica.

**Umbrales (reemplazan "I-01 > 60 % medido en el piloto"):**

| Métrica | Umbral |
|---|---|
| MRR (relevantes) | ≥ 0,80 |
| recall@5 (relevantes) | ≥ 0,60 |
| Relevantes con al menos un resultado | ≥ 90 % (el mismo criterio de `CALIBRACION_busqueda.md`) |
| Irrelevantes en 0 | ≥ 80 % |
| Sin sentido en 0 | 100 % |

Justificación: MRR ≥ 0,8 significa que, en promedio, el primer Tutor correcto aparece entre la
posición 1 y la 2, que es lo que pide la aceptación de sugerencias (si el primero no sirve, el
usuario no acepta). recall@5 se fija más bajo porque hay materias con 5+ Tutores (Matemática
secundario) y la pantalla corta por el margen relativo.

### 1.b Latencias p95 de los endpoints críticos (RNF-01/RNF-04 de T13)

Carga chica y reproducible: `scripts/evaluacion/latencias.py` contra un entorno con el seed
(local con `docker compose` o staging). **Nunca contra producción** con datos reales (tinku.site es
demo, pero igual se corre fuera del horario de las sesiones moderadas).

| Endpoint | Request | Umbral p95 |
|---|---|---|
| Búsqueda | `POST /api/busquedas` (texto libre de la lista del set, rotando) | ≤ 1.500 ms |
| Reserva | `POST /api/reservas` (franja libre de un Tutor del seed) | ≤ 800 ms |
| Pago en bypass | `POST /api/pagos/preferencia` con la pasarela deshabilitada (confirma sin MercadoPago) | ≤ 800 ms |
| Token del aula | `POST /api/sesiones/{id}/token` | ≤ 500 ms |

Perfil de carga: 1 usuario virtual de calentamiento (10 requests, se descartan) y después **5
usuarios concurrentes × 20 requests** por endpoint (100 muestras). Se reporta p50, p95, máximo y
errores. 5 concurrentes es una estimación generosa de la concurrencia real del MVP (1 desarrollador,
cientos de usuarios); el número es parte del reporte, no un supuesto escondido.

Los umbrales de API **los propone esta spec**: la Constitución (NFRs) solo fija la latencia de la
videollamada (< 200 ms, la mide LiveKit, no este script) y la disponibilidad (95 % mensual "durante el
piloto", que pasa a medirse en el período de las sesiones con el panel de Coolify, T13 §5). La
búsqueda tiene el umbral más alto porque incluye el modelo de embeddings en CPU. El dueño los puede
ajustar antes de medir, nunca después.

### 1.c Kill-switch (no hay piloto con menores, ver T10)

Se reportan los resultados **ya hechos** (no se rehacen):

- **E2E real (M3C-06, T-M3-06f):** 9 casos pasados contra backend y LiveKit reales
  (`docker-compose.e2e.yml`, proyecto Playwright `e2e-real`).
- **Benchmark del clasificador (M3C-07):** tabla del anexo 2026-10-16 de `ADR-M3-01.md`: con `wasm` a
  CPU 4× (perfil de gama media de Lighthouse) la detección estimada es 1,8 s con 1 track (≤ 3 s);
  0 turnos salteados y sin pérdida de memoria.

**Umbral:** detección estimada ≤ 3 s con 1 track a 4× (criterio de M3C-07, **cumple**). La fila en un
celular físico de gama media sigue pendiente (O-10): pasa a las sesiones moderadas si algún
participante presta un Android de gama media (§2.4, tarea opcional T8), y si no, a trabajo futuro.
La exactitud con imágenes reales sensibles está **fuera de alcance** (BR-KS-04): no se mide.

### 1.d Calidad del software

| Qué | Fuente | Umbral |
|---|---|---|
| Tests backend | `./mvnw -B test` en CI (`target/surefire-reports`) | 0 fallas, 0 errores |
| Cobertura de líneas | JaCoCo (`target/site/jacoco/index.html`) | ≥ 85 % (baseline AGENTS §5: 85,6 %) |
| Tests frontend | Playwright (proyecto `chromium`) + `tsc --noEmit` | 0 fallas |
| Matching | `pytest` en `matching-service/` | 0 fallas |
| Auditoría | `docs/auditoria/REGISTRO_FINDINGS.md` | 0 hallazgos CRÍTICOS abiertos |

Los valores se copian **del último run de CI de `main`** al cierre de la evaluación, con fecha y
commit, no de memoria.

---

## 2. Sesiones moderadas con usuarios

### 2.1 Participantes

- **5 a 8 personas adultas** (≥ 18). **Ningún menor real**, ni como participante ni como
  "beneficiario" de una cuenta: la cuenta Menor solo la usa el moderador con datos del seed si hace
  falta mostrarla, y con `tinku.menores.sesiones-habilitadas=false` (T-TES-10) igual no se reserva.
- Mezcla buscada (no obligatoria): al menos 2 que hagan de Tutor, al menos 3 de Estudiante/Adulto
  Responsable, y **al menos uno que viva en una provincia del interior** (para I-03, §3).
- Participan con **cuentas creadas en la sesión** y datos de prueba: el moderador entrega un DNI de
  prueba y una foto de DNI de prueba (no se suben DNIs reales). Si el registro con OCR no acepta el
  DNI de prueba, se usa una cuenta del seed (`TutorSeedRunner`, `EstudianteSeedRunner`) y la tarea T1
  se registra como "hecha por el moderador".
- Remota (videollamada con pantalla compartida) o presencial; 45 minutos por persona.

### 2.2 Consentimiento informado

> **A REVISAR POR EL DUEÑO.** Texto borrador, sin cláusulas legales inventadas. No usar hasta que el
> dueño lo apruebe (y, si corresponde, quien supervise la tesis en la universidad).

> Te invitamos a probar Tinku, una plataforma de clases particulares en línea que es parte de un
> trabajo final de carrera. Vas a usar una versión de demostración: los tutores, las clases y los
> pagos son de prueba, y no se cobra dinero real.
>
> Durante unos 45 minutos te vamos a pedir que hagas algunas tareas (registrarte, buscar un tutor,
> reservar, entrar a una clase de prueba, calificar) y que digas en voz alta lo que pensás. Al final
> vas a completar un cuestionario corto.
>
> Qué registramos: si completaste cada tarea, cuánto tardaste, tus respuestas al cuestionario y la
> provincia en la que vivís. La aplicación además guarda, sin tu nombre a la vista, qué tutores
> te sugirió la búsqueda y cuál abriste o reservaste, y tu respuesta a la pregunta de recomendación
> (0 a 10). No guardamos el texto que escribas en la búsqueda. **No grabamos la pantalla ni el audio
> [a definir por el dueño: si se graba, decirlo acá y pedir permiso aparte].**
>
> Para qué: los resultados agregados (sin nombres) se usan en el trabajo final.
>
> Tu participación es voluntaria. Podés dejar de participar en cualquier momento sin dar
> explicaciones, y pedir que borremos tus datos de la sesión escribiendo a [contacto del dueño].
>
> [Espacio para las cláusulas que el dueño defina: responsable de los datos, plazo de
> conservación, contacto.]
>
> ☐ Leí lo anterior y acepto participar. Nombre: ______ Fecha: ______

### 2.3 Rol del moderador

- No ayuda salvo que la persona esté trabada más de **3 minutos** en una tarea o lo pida dos veces;
  en ese caso anota la tarea como "con ayuda" (no cuenta como éxito).
- Lee cada tarea en voz alta tal como está escrita; no nombra botones.
- Antes de la sesión: flag `tinku.evaluacion.habilitada=true` en el entorno (§4), pasarela en
  *bypass* (panel Admin → Salud de Infraestructura, rol Soporte Financiero), Tutores del seed con
  franjas en las próximas horas.
- **Bloqueo a resolver por el dueño:** con el perfil `prod` la pasarela **no se puede apagar**
  (FASE2-07/AUD-018, `BypassNoPermitidoException`), y el 2026-10-14 `tinku.site` estaba en cobro real
  (`bypassPermitido=false`, `HALLAZGOS_pruebas.md` §7). Para que T5 sea "pagar en bypass" sin plata
  real, las sesiones se hacen en un entorno sin `prod` (staging con `seed-demo`) o se decide otra
  cosa; no se cambia el bloqueo del código.

### 2.4 Guion de tareas

**Del lado Estudiante / Adulto Responsable:**

| # | Tarea (como se lee) | Éxito = |
|---|---|---|
| T1 | "Creá una cuenta en Tinku con los datos que te damos." | Llega al panel con la sesión iniciada |
| T2 | "Necesitás ayuda con [tema del sobre, p. ej. funciones cuadráticas]. Buscá un tutor." | Ve una lista de Tutores |
| T3 | "Elegí el tutor que te parezca mejor y mirá su perfil." | Abre el perfil de un Tutor de la lista |
| T4 | "Reservá una clase de 30 minutos para hoy o mañana." | La reserva queda creada |
| T5 | "Pagá la clase." (bypass) | La reserva queda confirmada |
| T6 | "Entrá a la clase de prueba." (el moderador entra como Tutor) | Ve el aula con su cámara |
| T7 | "Terminá la clase y calificá al tutor." | La calificación queda guardada |
| T8 | *(opcional, si presta un Android de gama media)* "Abrí esta dirección y tocá Empezar medición." | Copia el resultado de `/diagnostico/clasificador` |

**Del lado Tutor:**

| # | Tarea | Éxito = |
|---|---|---|
| U1 | "Creá tu cuenta de tutor." (o cuenta del seed, ver §2.1) | Llega al panel de Tutor |
| U2 | "Cargá las materias que podés enseñar." | Guarda al menos un tema |
| U3 | "Publicá tus horarios para esta semana." | Guarda al menos una franja |
| U4 | "Fijá tu tarifa por hora." | Tarifa guardada (respetando el piso) |
| U5 | "Entrá a la clase que te reservaron y terminala." | La sesión queda finalizada |
| U6 | "Calificá al alumno." | Calificación guardada |

Después de la primera clase finalizada o calificada, la app ofrece el **NPS in-app** (§4.2). El
moderador no lo menciona; si la persona lo cierra sin responder, se le pregunta en el cuestionario.

### 2.5 Métricas de las sesiones

| Métrica | Cómo | Umbral |
|---|---|---|
| Tasa de éxito por tarea | éxitos sin ayuda / participantes que intentaron | ≥ 80 % en T2–T7 y U2–U5 |
| Tiempo por tarea | cronómetro del moderador (inicio de la lectura → criterio de éxito) | se reporta mediana y máximo (sin umbral: no hay base de comparación) |
| **SUS** | cuestionario §2.6, puntaje 0–100 | promedio ≥ 68 (promedio de referencia de la escala) |
| **NPS de intención** | in-app (§4.2) o, si no respondió, en el cuestionario | ≥ 0 (positivo), ver §3 |
| I-01 en la app | panel Admin → Evaluación (§4.3) | ≥ 60 % |
| I-02 | calificaciones M7 `estudiante_a_tutor` de esas clases (panel) | ≥ 4,0 |
| I-03 | provincia declarada en el cuestionario | ≥ 1 clase entre provincias distintas, una del interior |

### 2.6 Cuestionario post-sesión

**Datos:** provincia en la que vive (lista de las 24 jurisdicciones), rol que probó, si alguna vez
tomó o dio clases particulares en línea (sí/no).

**SUS (System Usability Scale, 10 ítems, Brooke 1996), en castellano.** Escala 1 (muy en desacuerdo)
a 5 (muy de acuerdo):

1. Creo que me gustaría usar Tinku con frecuencia.
2. Encontré Tinku innecesariamente complejo.
3. Me pareció que Tinku era fácil de usar.
4. Creo que necesitaría ayuda de una persona con conocimientos técnicos para poder usar Tinku.
5. Encontré que las distintas funciones de Tinku estaban bien integradas.
6. Me pareció que había demasiadas inconsistencias en Tinku.
7. Imagino que la mayoría de las personas aprenderían a usar Tinku muy rápido.
8. Encontré Tinku muy incómodo de usar.
9. Me sentí muy seguro/a usando Tinku.
10. Necesité aprender muchas cosas antes de poder empezar a usar Tinku.

Cálculo: ítems impares → (respuesta − 1); ítems pares → (5 − respuesta); suma × 2,5 = SUS (0–100).

**NPS (solo si no lo respondió en la app):** "¿Qué tan probable es que recomiendes Tinku a un amigo
o familiar? (0 = nada probable, 10 = muy probable)".

**Abiertas:** "¿Qué fue lo más difícil?" · "¿Qué cambiarías primero?"

### 2.7 Plantilla de la planilla de resultados

Una fila por participante (CSV o planilla de cálculo). `P01..P08`, sin nombres.

```
participante,rol,provincia,interior(si/no),T1_exito,T1_seg,T2_exito,T2_seg,T3_exito,T3_seg,T4_exito,T4_seg,T5_exito,T5_seg,T6_exito,T6_seg,T7_exito,T7_seg,U1_exito,U1_seg,U2_exito,U2_seg,U3_exito,U3_seg,U4_exito,U4_seg,U5_exito,U5_seg,U6_exito,U6_seg,SUS1,SUS2,SUS3,SUS4,SUS5,SUS6,SUS7,SUS8,SUS9,SUS10,SUS_total,NPS,NPS_fuente(app/cuestionario),clase_con(participante),observaciones
```

Valores de `*_exito`: `si`, `con_ayuda`, `no`, `no_aplica`. `clase_con` sirve para I-03 (la clase de
P03 con P05: provincias de ambos).

Hoja de resumen: tasa de éxito por tarea, mediana/máximo de tiempo por tarea, SUS promedio y
desvío, NPS (= % promotores 9–10 − % detractores 0–6), I-01/I-02 copiados del panel con fecha, I-03.

---

## 3. Cómo se reformulan los indicadores

| ID | Antes (T11, piloto) | Ahora (T11b) | Umbral | Fuente |
|---|---|---|---|---|
| I-01 | Aceptación de sugerencias > 60 % en el piloto | **Doble:** (a) calidad del ranking offline (MRR, recall@5, §1.a) y (b) aceptación en las sesiones moderadas: búsquedas con al menos un resultado en las que el usuario abrió el perfil o reservó un Tutor del **top-5** / búsquedas con al menos un resultado | (a) MRR ≥ 0,80 y recall@5 ≥ 0,60; (b) ≥ 60 % | script §5 + panel §4.3 |
| I-02 | Calidad percibida ≥ 4,0 (M7) | Igual, sobre las calificaciones `estudiante_a_tutor` de las clases de las sesiones moderadas (período de las sesiones) | ≥ 4,0 | panel §4.3 (M7) |
| I-03 | ≥ 1 sesión entre provincias, una del interior (campo `provincia` en `Usuario`) | Igual, con la provincia **declarada en el cuestionario** (no se agrega `provincia` a `Usuario`: minimización, no hay regla de negocio que la necesite) | ≥ 1 | planilla §2.7 |
| I-04 | NPS del piloto, una vez al cierre | **NPS de intención**: una vez por usuario, después de su primera clase finalizada o calificada, in-app (o en el cuestionario) | ≥ 0 | panel §4.3 + planilla |

**Limitación que se declara en la tesis:** n = 5–8 es una muestra chica y no aleatoria; los
indicadores de las sesiones son **evidencia exploratoria**, no una estimación poblacional. Por eso
I-01 se apoya también en la evaluación offline (120 consultas), y el umbral de I-04 es solo "positivo".

### Lo que pasa a trabajo futuro

| ID | Qué era | Por qué no se mide | Disparador para medirlo |
|---|---|---|---|
| S-04 | Adopción del adicional de resumen (40 %) | **Ya no existe en la tesis** (T17:44: el adicional no entra al flujo de fondos). Además el adicional está apagado (`tinku.resumen.adicional.habilitado=false`) hasta el texto legal de la cláusula de grabación. | No aplica salvo que la tesis lo reintroduzca |
| R-02 | Recompra ≥ 50 % | Necesita usuarios que vuelvan semanas después; en una sesión de 45 min no hay recompra | Primeros 3 meses de operación con usuarios reales: reservas de un mismo alumno con el mismo Tutor después de la primera / primeras reservas |
| R-04 | ≥ 5.100 sesiones/año | Es un volumen de operación; no se puede inferir de 8 sesiones de prueba | Primer trimestre de operación real: sesiones finalizadas × 4 |

---

## 4. Instrumentación mínima en la app

Todo detrás de `tinku.evaluacion.habilitada` (env `TINKU_EVALUACION_HABILITADA`), **default
`false`**. Con el flag apagado, el sistema se comporta exactamente como antes: no se registra nada,
no se ofrece el NPS, y los endpoints nuevos de usuario responden 404. **No se enciende en la
configuración persistente de producción**: lo enciende el dueño en el entorno de la demo durante el
período de las sesiones y lo apaga al terminar.

### 4.1 Registro de búsquedas y aceptación (I-01)

- Migración **V58**: `matching.evaluacion_busquedas` (id, usuario_id, longitud del texto, cantidad de
  resultados, `tutor_ids` en orden, fecha) y `matching.evaluacion_aceptaciones` (búsqueda, tutor,
  **posición** calculada en el servidor, tipo `PERFIL`/`RESERVA`, fecha; única por búsqueda+tutor+tipo).
- **Minimización (Art. V):** no se guarda el texto libre ni un hash (un hash de un texto corto se
  revierte por diccionario): solo su longitud. No se registran búsquedas de cuentas Menor (Art. II,
  la opción más restrictiva).
- `POST /api/busquedas` devuelve el id en el header `X-Busqueda-Id` (expuesto por CORS) solo con el
  flag encendido; el cuerpo de la respuesta no cambia.
- `POST /api/busquedas/{id}/aceptaciones` `{tutorId, tipo}`: solo el dueño de la búsqueda; el tutor
  tiene que estar entre los resultados mostrados (si no, 422); idempotente (204).
- El frontend lo manda al abrir el perfil de un Tutor desde los resultados y al crear la reserva con
  ese Tutor (sin bloquear el flujo si falla).

### 4.2 NPS in-app (I-04)

- Migración **V58**: `admin.nps_respuestas` (usuario_id PK, puntaje 0–10, fecha). Una por usuario.
- `GET /api/evaluacion/nps/pendiente` → `{ofrecer}`: `true` solo si el flag está encendido, la cuenta
  no es Menor, no respondió todavía, y tiene **al menos una clase finalizada** (como alumno, pagador o
  Tutor) **o escribió al menos una calificación**.
- `POST /api/evaluacion/nps` `{puntaje}`: 201; 409 si ya respondió; 422 si el puntaje no está entre 0
  y 10; 403 si es Menor o no cumple la condición; 404 con el flag apagado.
- Modal en el frontend (panel del usuario): pregunta 0–10, "Enviar" y "Ahora no" (no vuelve a aparecer
  en esa sesión del navegador).

### 4.3 Panel de Admin "Evaluación"

- `GET /api/admin/evaluacion/indicadores?desde=AAAA-MM-DD&hasta=AAAA-MM-DD`: cualquier Admin activo
  (lectura, transversal a los dos roles, como "Salud de Infraestructura"). Devuelve: flag encendido o
  no; búsquedas registradas, con y sin resultado; I-01 (tasa y aceptaciones por posición y por tipo,
  con k = 5); NPS (respuestas, promotores, pasivos, detractores, valor); I-02 (cantidad y promedio de
  calificaciones `estudiante_a_tutor` del período).
- Pantalla `/admin/evaluacion` con esos números y el rango de fechas.

### 4.4 Retención

Las tablas de evaluación **no tienen plazo de borrado automático** en esta tarea: la Tabla de
Tiempos no tiene una fila para esto y no se inventa un plazo. **Decisión pendiente del dueño**:
cuánto tiempo se conservan después de la evaluación (sugerencia: borrar al aprobarse la tesis; se
borra con `DELETE` manual de las dos tablas de `matching` y de `admin.nps_respuestas`).

---

## 5. Script de evaluación offline del matching

`matching-service/tests/eval_relevancia/evaluar_ranking.py`:

- Lee `consultas_etiquetadas.csv`, arma los Tutores del seed **con sus temas y trayectos** (el
  `evaluar.py` de la calibración solo leía `tema(...)` y dejaba afuera a los 30 Tutores definidos con
  `trayecto(...)`), embebe cada tema como `"{nombre}: {descripcion}"` con el mismo modelo del
  servicio, puntúa cada Tutor con el máximo coseno (igual que `SQL_SCORES`) y aplica el corte del
  backend.
- Emite `reporte_ranking.csv` (una fila por consulta) y `reporte_ranking.md` (métricas §1.a).
- El embebedor es inyectable: los tests (`tests/test_evaluar_ranking.py`) usan uno falso, sin bajar
  el modelo.

Comando:

```bash
cd matching-service
python -I tests/eval_relevancia/evaluar_ranking.py            # modelo real (lo baja de Hugging Face la 1.a vez)
python -m pytest -q tests/test_evaluar_ranking.py              # tests con embebedor falso
```

---

## 6. Tests (RED primero)

Backend (`EvaluacionIntegracionTest`, flag encendido; `EvaluacionApagadaIntegracionTest`, default):

1. `busqueda_conFlag_registraResultadosEnOrden_sinTexto` y `busqueda_sinFlag_noRegistra_niHeader`.
2. `aceptacion_calculaPosicion_esIdempotente`, `aceptacion_tutorFueraDeResultados_422`,
   `aceptacion_busquedaAjena_404`.
3. `busquedaDeMenor_noSeRegistra`.
4. `nps_noSeOfreceSinClase_seOfreceConClaseFinalizada`, `nps_unaRespuestaPorUsuario_409AlRepetir`,
   `nps_puntajeFueraDeRango_422`, `nps_menor_nuncaSeOfrece_403`, `nps_sinFlag_404`.
5. `indicadores_calculanI01NpsI02_comoElCalculoManual`, `indicadores_soloAdmin_403ParaOtros`.

Frontend (Playwright, API mockeada): modal NPS (aparece, envía, "Ahora no") y panel de Admin.

Python: `test_evaluar_ranking.py` (métricas sobre un caso chico calculado a mano, parser del seed
con `trayecto`, corte del backend).

## 7. Criterios de aceptación

- Tests nuevos en verde; con el flag apagado, ningún test existente cambia de resultado.
- `CALIBRACION_busqueda.md` y los ADR que esperaban datos del piloto tienen una nota fechada
  2026-10-18 que dice con qué se reemplaza el dato (§8).
- `docs/operacion/TESIS_sin_piloto.md` con el texto sugerido por capítulo, y
  `DESALINEACIONES_tesis_codigo.md` actualizado (A-07, B-09, B-10, D-01, D-04, C8).

## 8. Calibraciones que esperaban el piloto

| Documento | Dato que esperaba | Reemplazo |
|---|---|---|
| `CALIBRACION_busqueda.md` | Búsquedas reales del piloto | Set etiquetado de 120 (§1.a) + registro de búsquedas en las sesiones moderadas (§4.1) |
| `ADR-M2-02.md`, `Plan_M2_Motor_Matching.md`, `ReputacionSignalProveedorImpl.java` | Peso de la reputación con calificaciones reales | Trabajo futuro: disparador = 50 calificaciones reales `estudiante_a_tutor` |
| `ADR-M3-07.md` | Revisión del kill-switch con datos del piloto | E2E + benchmark (§1.c); revisión con menores = trabajo futuro (al encender menores, M3C-09) |
| `ADR-M3-01.md` | Latencia en un celular real (O-10) | Tarea opcional T8 de las sesiones; si no, trabajo futuro |
| `ADR-M1-01.md` | Tasa de DNI ilegible | Sesiones moderadas (T1) con DNI de prueba: se anota si el OCR falla; tasa real = trabajo futuro |
| `ADR-M5-04.md`, `ADR-M5-05.md`, `Spec_M5` | Revisar la comisión con datos de operación | Trabajo futuro: primer trimestre de operación con cobro real |
| `ADR-000-04.md` | Duración del refresh token según UX real | Sesiones moderadas: se anota si a alguien se le cierra la sesión durante los 45 min |
