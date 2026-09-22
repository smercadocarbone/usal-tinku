# ADR-M6-02 — Anonimización de transcript por regex + diccionario como puente

**Estado:** Aceptado — documentado retroactivamente el 2026-09-21 (decisión ya tomada en el código, recién formalizada)

## Contexto
FR-SUM-005 (Plan M6 §2, paso 4) exige anonimizar el transcript de una sesión **antes** de que
cualquier dato salga hacia el proveedor de LLM — un paso no-opcional y anterior a toda llamada de
red saliente. `AnonimizadorTranscript`
(`backend/src/main/java/com/tinku/resumen/anonimizacion/AnonimizadorTranscript.java`) implementa
ese paso hoy, y su javadoc ya describe la estrategia con precisión: "ADR-M6-01 pendiente" (el NER
real) queda como referencia futura, y el propio componente se declara explícitamente un puente. Lo
que falta es formalizar esa estrategia como decisión de ingeniería, no describirla solo en un
comentario de código.

**Por qué importa ahora mismo, aunque M6 no esté activo:** AUD-024 confirma que M6 no puede
generar ningún resumen hoy — no hay proveedor de transcript (`TranscriptSesionProveedorNoDisponible`
devuelve `null` siempre) ni proveedor de LLM elegido. `AnonimizadorTranscript` es, por ahora, código
sin ningún dato real que procesar. Eso hace este ADR más fácil de escribir con honestidad — no hay
presión de "ya está en producción, hay que justificarlo" — y más importante de escribir ahora: el
día que M6 se active, este componente deja de ser una pieza de infraestructura inerte y pasa a ser
el único control de privacidad entre el audio de una sesión con un Menor y un proveedor de LLM
externo.

## Decisión
Anonimización por **regex + diccionario cerrado**, sin modelo de NER, aplicada en un pipeline de
pasos secuenciales sobre el texto del transcript. Estrategia deliberada bajo el Artículo VII (1
desarrollador, USD 0-100/mes): no se integra un modelo de NER (que agregaría una dependencia de
inferencia, latencia y superficie de fallo) para un módulo que hoy no tiene datos reales que
procesar.

**Estrategia fail-safe explícita: ante la duda, enmascarar de más.** El javadoc del componente lo
declara así: un falso positivo enmascara una palabra del transcript que no era sensible (costo:
pérdida de legibilidad); un falso negativo filtra un dato personal de un Menor hacia un proveedor
externo de LLM (costo: incumplimiento del Artículo II). Frente a esa asimetría, cada patrón está
escrito para capturar de más, no de menos — la calidad del resumen final es secundaria frente a la
privacidad del dato.

### Orden de aplicación de los patrones, y por qué ese orden
El pipeline (`anonimizar(String)`) aplica los reemplazos en esta secuencia exacta, y el orden no es
arbitrario — cada paso protege al siguiente de una captura incorrecta:

1. **Pagos numéricos** (CBU de 22 dígitos, CUIT/CUIL, tarjeta de 16 dígitos) → `[pago]`. Van
   primero porque sus patrones son los más específicos por longitud/formato exacto — si corrieran
   después de teléfono, un CBU de 22 dígitos o una tarjeta de 16 quedarían parcialmente capturados
   por los patrones de teléfono (que aceptan 8-10 dígitos con separadores), fragmentando el
   marcador y dejando dígitos sueltos sin enmascarar.
2. **Email** → `[email]`. Corre antes que el alias de MercadoPago porque un alias de MP tiene el
   mismo formato de tres segmentos separados por punto que la parte local de muchos emails
   (`nombre.apellido.algo`) — si el alias corriera primero, "nombre@gmail.com" podría capturarse
   parcialmente como alias antes de que el patrón de email lo viera completo.
3. **URL** → `[url]`.
4. **Alias de MercadoPago** (tres segmentos en minúsculas) → `[pago]`. Corre después del email
   exactamente por la razón del punto 2 — el email ya se consumió, así que lo que queda con formato
   de tres segmentos es alias real, no la parte local de un email.
5. **DNI/documento** (formato `xx.xxx.xxx`, con la palabra "DNI" o "documento" como ancla
   obligatoria hasta 15 caracteres no numéricos antes, lazy) → `[dni]`. Corre antes que teléfono por
   especificidad: exigir el ancla literal `dni|documento` lo hace el patrón más restrictivo de los
   dos (solo dispara con esa palabra cerca, mientras que los patrones de teléfono no exigen
   ninguna palabra ancla), y la regla general de este pipeline es que el patrón más restrictivo va
   antes que el más permisivo. **Corrección sobre la primera versión de este ADR:** se afirmaba
   que si teléfono corriera primero "capturaría el DNI como número de teléfono" — se verificó
   corriendo los 4 patrones de `NO_PAGO_TELEFONO` contra el formato exacto que documenta el patrón
   DNI (`"mi DNI es 30.123.456"`, `"documento 12.345.678"`, `"DNI 7.654.321"`) y **ninguno
   matchea**: la agrupación de dígitos `\d{1,3}\.\d{3}\.\d{3}` (con dos puntos, tres grupos) no
   coincide con ninguna de las agrupaciones que aceptan los patrones de teléfono. El orden
   específico-antes-que-permisivo sigue siendo el criterio correcto del pipeline, pero en este par
   puntual (DNI vs. teléfono) la posición relativa es indiferente en la práctica — no hay colisión
   real entre ambos formatos, a diferencia de los pares de los puntos 1 y 2/4, donde sí se
   verificó una colisión real de formato.
6. **Teléfonos argentinos** (móviles con/sin `54`/`0`/`9`, fijos con área, locales de 8 dígitos) →
   `[telefono]`. Corren después de pagos y DNI precisamente porque sus patrones son los más
   permisivos (aceptan rangos de 8 a 10-11 dígitos con separadores variables) — si corrieran
   primero, se comerían números de pago y de documento antes de que esos patrones más específicos
   los vieran.
7. **Nombres por presentación** ("me llamo X", "soy X", "habla X"...) → `[nombre]`, conservando la
   frase introductoria y reemplazando solo el nombre capturado.
8. **Nombres por diccionario** (lista cerrada de nombres propios hispanos frecuentes) → `[nombre]`,
   como última pasada — corre al final porque es el patrón más amplio (cualquier coincidencia
   exacta de una palabra en la lista, sin contexto), y aplicarlo antes arriesgaría enmascarar un
   nombre que en realidad forma parte de un patrón más específico ya cubierto (por ejemplo, un
   alias de MercadoPago que contuviera un nombre de pila como segmento).

El principio general del orden: **de lo más específico (formato numérico exacto) a lo más amplio
(coincidencia de diccionario)**, para que un patrón permisivo nunca consuma texto que un patrón
más preciso más adelante en la lista necesitaba ver completo.

## Límite conocido
Hay dos límites estructurales, del mismo peso (Artículo II), y ninguno se corrige agregando más
entradas a una lista — son consecuencia del diseño por diccionario/ancla, no bugs puntuales:

1. **Diccionario cerrado de nombres.** `NOMBRE_DIC` es una lista cerrada de nombres propios
   hispanos frecuentes en Argentina. **Un nombre que no está en esa lista, y que tampoco aparece en
   un patrón de presentación reconocido** ("me llamo X", "soy X", etc.), **no se enmascara.**
   Nombres poco frecuentes, diminutivos no listados, nombres de origen no hispano, o un nombre
   mencionado sin una frase de presentación reconocible ("che, decile a Rodrigo que...") pasan sin
   marcar.
2. **El patrón de DNI exige el ancla literal `dni|documento`.** Un número con el formato exacto
   `xx.xxx.xxx` mencionado **sin** esa palabra cerca (hasta 15 caracteres no numéricos antes, lazy) no lo
   captura el patrón de DNI — y se verificó (punto 5 arriba) que tampoco lo captura ningún patrón
   de teléfono, porque la agrupación de dígitos no coincide. Un Menor que dice su número de
   documento sin la palabra "DNI" o "documento" en la misma frase ("che anotate el
   30.123.456") sale del pipeline sin ningún marcador — ni `[dni]` ni `[telefono]` ni ninguno.

Ambos son limitaciones estructurales de un enfoque de diccionario/ancla cerrado, no bugs puntuales
corregibles con una lista más grande o un ancla más permisiva sin costo — un ancla más laxa (por
ejemplo, cualquier secuencia `\d{1,3}\.\d{3}\.\d{3}` sin exigir la palabra "DNI" cerca) capturaría
más casos reales pero también más falsos positivos sobre otros números con ese formato (montos,
códigos), lo cual es aceptable bajo la estrategia fail-safe de este mismo ADR — pero es un cambio
de diseño, no algo que este documento decida por su cuenta.

## Riesgo aceptado
- **Hoy, el riesgo real es cero, pero no porque el componente esté inactivo.**
  `AnonimizadorTranscript` está cableado en el pipeline de producción (`ResumenService` lo inyecta
  y lo invoca; `ResumenProveedor` lo referencia en su contrato) — no es código que solo corra en
  tests. El riesgo es cero porque **nunca recibe datos**: AUD-024 confirma que
  `TranscriptSesionProveedorNoDisponible` devuelve `null` siempre, así que el pipeline nunca llega
  a ejecutar `anonimizar(String)` sobre un transcript real. No hay dato real de ningún Menor
  expuesto por esta limitación mientras esa condición (sin proveedor de transcript) se mantenga.
- **Cuando M6 se active (proveedor de transcript + proveedor de LLM resueltos), este componente
  deja de ser inofensivo y pasa a ser un control de privacidad de datos de Menores en producción.**
  En ese momento, el límite del diccionario cerrado es un riesgo real y activo: un nombre fuera de
  la lista, dicho sin una frase de presentación reconocible, llega al proveedor de LLM externo sin
  enmascarar. **A partir de ese momento, `ADR-M6-01` (NER real) deja de ser una mejora opcional y
  pasa a ser un requisito de M6**, no una tarea de backlog — este ADR lo declara explícitamente
  para que la activación de M6 no reabra esta pregunta como si fuera nueva.
- El puerto está diseñado para ese reemplazo sin fricción: el contrato de `anonimizar(String)` no
  cambia cuando `NOMBRE_DIC` y su patrón se reemplacen por el modelo que `ADR-M6-01` elija — el
  javadoc del componente ya lo declara así.

## Implementación
Ya aplicada: `backend/src/main/java/com/tinku/resumen/anonimizacion/AnonimizadorTranscript.java`,
cubierta por
`backend/src/test/java/com/tinku/resumen/anonimizacion/AnonimizadorTranscriptTest.java`. Este ADR
no agrega código nuevo — formaliza la estrategia ya implementada.

## Consecuencias
- La tarea de activar M6 (proveedor de LLM + proveedor de transcript) debe incluir, como
  prerequisito y no como mejora posterior, la decisión de `ADR-M6-01` — este documento fija esa
  dependencia explícitamente.
- Cualquier ampliación del diccionario `NOMBRE_DIC` es un parche legítimo de corto plazo, pero no
  cierra el límite estructural descrito arriba — no se debe presentar como solución del problema
  de cobertura, solo como mitigación parcial.
- El orden de los patrones documentado acá es parte del contrato del componente: si se agrega un
  patrón nuevo, su posición en el pipeline debe justificarse contra los mismos criterios
  (especificidad de formato, riesgo de consumir texto que otro patrón necesita) antes de
  insertarlo.

## Registro de Decisiones Técnicas (Constitución)
No aplica una fila del Registro (no es una elección de proveedor/tecnología — es la estrategia de
un componente interno). Queda registrada únicamente como este ADR.
