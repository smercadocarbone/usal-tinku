# ADR-M1-03 — Storage de archivos: filesystem local tras el puerto `Almacenamiento`

**Estado:** Aceptado — documentado retroactivamente el 2026-09-21 (decisión ya tomada en el código, recién formalizada)

## Contexto
US-4 (credencial académica del Tutor) requiere persistir un archivo subido por el usuario.
`NOTAS_VERIFICACION.md:100-103` lo menciona como pendiente de ADR desde que se implementó y nunca
se escribió — esta es esa formalización. La decisión de fondo (filesystem local detrás de un
puerto) es correcta para el alcance del piloto y permanece; lo que hay que declarar sin
eufemismo es su limitación conocida, que es la causa raíz de un finding de severidad crítica de la
auditoría (AUD-007).

## Decisión
El almacenamiento de archivos vive detrás del puerto `Almacenamiento`
(`backend/src/main/java/com/tinku/identidad/port/Almacenamiento.java`), con un único método:
`guardar(byte[] contenido, String nombreOriginal): String`. La implementación actual,
`AlmacenamientoLocal`, escribe el archivo al filesystem del servidor bajo un directorio
configurable (`tinku.almacenamiento.directorio`, default `${java.io.tmpdir}/tinku`) y devuelve la
URI `file:` resultante.

Elegido por ser la opción más simple que cumple el requisito de US-4 durante el piloto: sin
proveedor externo, sin credenciales de un servicio de terceros que gestionar, sin costo adicional
— coherente con el Artículo VII (1 desarrollador, USD 0-100/mes) para un volumen de archivos que
hoy son solo credenciales de Tutores en etapa de piloto.

**Por qué el puerto hace que el reemplazo por S3 no toque llamadores:** todo el código que
persiste un archivo (`CredencialService`, y cualquier flujo futuro que suba un documento) depende
únicamente de la interfaz `Almacenamiento.guardar(...)`, nunca de `AlmacenamientoLocal`
directamente — Spring inyecta la implementación por tipo de puerto. Reemplazar el filesystem local
por S3 (u otro object storage) es agregar una segunda implementación (`AlmacenamientoS3`) y
cambiar el bean activo por perfil/configuración; ningún llamador cambia una línea. Es exactamente
el patrón que ya anticipa `NOTAS_VERIFICACION.md:100-103` ("el ADR de storage real [...] reemplazará
el bean sin tocar el puerto") y que el propio javadoc de `AlmacenamientoLocal` describe; este ADR
confirma que es una decisión de diseño deliberada, no casualidad.

## La limitación que hay que declarar
`AlmacenamientoLocal.guardar()` devuelve `destino.toUri().toString()` — una URI con esquema
`file:` (`backend/src/main/java/com/tinku/identidad/port/AlmacenamientoLocal.java:45`). **Una URI
`file:` no es servible por HTTP.** No hay ningún navegador, ni ningún cliente externo al proceso
del backend, que pueda resolver `file:///tmp/tinku/....jpg` contra algo — es una ruta del
filesystem local del servidor, no una URL.

**Esta es la causa raíz de AUD-007 (severidad crítica):** el panel de Admin de Moderación aprueba
o rechaza la Credencial Académica de un Tutor sin haber visto nunca el archivo. La cola de
credenciales (`CredencialColaResponse`) excluye deliberadamente `archivoUrl` por minimización de
datos (correcto: no filtrar una ruta interna del filesystem al frontend), pero **no existe ningún
endpoint que sirva el contenido del archivo** — ni al Admin ni a nadie — porque la única URL que el
sistema tiene es una que ningún cliente HTTP puede usar. El Admin decide con nombre, apellido y
tipo de documento únicamente. El mecanismo de confianza que el Artículo I (v2.2) declara vigente
tras el retiro del CAP — la Credencial Académica — no se cumple en la práctica: se aprueba a
ciegas.

**El endpoint de lectura que corrige esto es FASE 1 de este plan de remediación (Task 1.9), no
este ADR.** La corrección tiene que servir **bytes** (`Almacenamiento.leer(url): byte[]`, con
validación de que la ruta resuelta cae dentro del directorio configurado, contra path traversal),
nunca la URI `file:` cruda — exponer esa URI al frontend no la vuelve servible, solo agrega una
ruta interna del servidor a una respuesta HTTP. El finding AUD-007 señala este mismo punto desde
el lado de la credencial (el Admin la aprueba sin verla); este ADR lo señala desde el lado del
storage que lo causa.

## Alternativas descartadas
- **S3 / object storage desde el día uno:** correcto a mayor escala, pero agrega una dependencia
  externa (cuenta, credenciales, SDK, costo variable) sin beneficio real durante un piloto con
  volumen bajo de archivos y un solo desarrollador operando la infraestructura. Se pospone hasta
  que haya volumen o requisito de durabilidad que lo justifique — el puerto ya está listo para ese
  reemplazo sin refactor de los llamadores.
- **Servir la URI `file:` reescrita a una ruta HTTP propia sin endpoint dedicado** (ej. exponer el
  directorio de almacenamiento como estático): descartado por seguridad — expondría cualquier
  archivo del directorio sin control de autorización, deshaciendo la minimización que
  `CredencialColaResponse` ya aplica correctamente.

## Riesgo aceptado
- **Sin backup ni replicación del directorio de almacenamiento.** Si el filesystem del servidor se
  pierde (falla de disco, error operativo), las credenciales subidas se pierden sin posibilidad de
  recuperación — no hay redundancia. Se acepta para el piloto: el volumen es bajo (credenciales de
  Tutores, no el dato transaccional de pagos) y el costo de un backup automatizado o de mover a
  object storage con durabilidad garantizada no se justifica todavía contra el Artículo VII.
- **Instancia única (ver `ADR-000-04`):** el directorio de almacenamiento vive en el filesystem de
  una sola instancia de backend. Escalar a 2+ instancias sin volumen compartido rompe este diseño
  — cada instancia vería solo los archivos que ella misma escribió. Es otro punto donde ambas
  decisiones de escala (ADR-000-04 y este ADR) convergen en el mismo umbral: N≥2 instancias.

## Implementación
Ya aplicada: `backend/src/main/java/com/tinku/identidad/port/Almacenamiento.java` (puerto),
`backend/src/main/java/com/tinku/identidad/port/AlmacenamientoLocal.java` (implementación),
`tinku.almacenamiento.directorio` en `application.yml`. Este ADR no agrega código nuevo — formaliza
la elección ya deployada en `main`.

## Consecuencias
- El endpoint de lectura de FASE 1 (Task 1.9) debe agregar `Almacenamiento.leer(String): byte[]`
  al puerto, no un endpoint que reexponga la URI `file:`.
- Si el proyecto migra a S3 en el futuro, ese cambio requiere su propio ADR (bajo AGENTS.md §2 —
  cambiar una fila del Registro de Decisiones Técnicas) pero no reabre este documento: la decisión
  de aislar el storage detrás de un puerto es la que permanece.
- La ausencia de backup queda como deuda explícita a revisar si el volumen de Tutores activos
  crece más allá del piloto — mismo criterio de revisión que `ADR-M1-02` aplica al CAP.

## Registro de Decisiones Técnicas (Constitución)
No aplica una fila del Registro (el storage de archivos no tiene una fila propia hoy). Queda
registrada únicamente como este ADR.
