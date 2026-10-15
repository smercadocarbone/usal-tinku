# ADR-M1-10 — Verificación de la firma digital del CAP con el validador oficial, por una cola espaciada

**Estado:** Aceptado (2026-10-15, decisión del dueño del producto). Retoma T-TES-04, que había
quedado diferida por PT2 ("manual en el MVP"), y elige una alternativa distinta de la que preveía
su spec (`docs/superpowers/specs/tesis/T04-cap-firma-digital.md`: PDFBox + BouncyCastle local).

## Contexto
- El CAP es un PDF con firma digital del Registro Nacional de Reincidencia (ADR-M1-04). Hasta
  ahora Moderación lo revisaba a ojo: nada impedía aprobar un certificado adulterado o una foto
  sin firma (riesgo R-10 de la tesis).
- El Estado publica un validador de firma digital: <https://validadordefirmas.gob.ar>
  (Secretaría de Innovación Pública). Su página usa estos pedidos, que se relevaron leyendo la
  propia página el 2026-10-15 (no hay API documentada):
  - `GET /api/PdfSignature/verificacion/countRequestsQueue` → pedidos en espera. La página no
    deja entrar con 300 o más.
  - `POST /api/PdfSignature/verificacion/async` (multipart, campo `pdf`) → `idRequest`.
  - `GET /api/PdfSignature/verificacion/{id}` → `state` (`WAITING_PROCESS`, `FINISHIED`
    —escrito así—, `FINISHIED_PROTECTED`…) y `verificationAsyncResult` (`valid`, `message`,
    `sha256Digest`, `signatures[]` con `validSign`, `commonName`, `organization`,
    `organizationalUnit`).
  - Límite de archivo: 25 MB (`/api/v1/util/config`).

## Opciones
1. **Validador oficial, llamado desde el backend con una cola espaciada.** Elegida por el dueño.
2. **Verificación local con PDFBox + BouncyCastle** (spec T04). El PDF no sale del servidor,
   pero suma dos dependencias criptográficas y hay que mantener la cadena de confianza de la
   AC raíz. Más trabajo para un desarrollador.
3. **Seguir manual**, con el validador como paso obligatorio de la revisión. No garantiza que
   se haga.

## Decisión
1. **Cola persistida, un pedido a la vez.**
   - Al cargar un CAP queda con `firma_estado = PENDIENTE` (migración V50).
   - Un job de Quartz persistido (`CapFirmaColaJob`, sin ejecuciones concurrentes) da un paso
     cada `tinku.cap.validador.intervalo-segundos` (30 s, Tabla de Tiempos).
   - Cada paso hace una sola cosa: consulta el pedido en curso o manda el próximo. Tinku nunca
     tiene más de un pedido abierto en el validador, y como máximo hace dos llamadas por paso
     (la cola del sitio y el envío).
   - No manda si el validador tiene 300 pedidos o más en espera (la misma cota que su página).
   - Si un paso se pierde (servidor apagado), no se recuperan los atrasados: se sigue con el
     próximo, para no mandar pedidos en ráfaga.
2. **Reintentos con espera creciente.** Si el validador no responde o no termina en 10 minutos:
   reintento a 1, 5 y 15 minutos y a 1 y 3 horas; al sexto fallo queda en `ERROR`. Moderación
   puede pedir verificarlo de nuevo (`POST …/antecedentes-penales/{id}/verificar-firma`).
3. **Qué cuenta como firma válida.** El resultado tiene que cumplir todo esto:
   - `valid` es verdadero y todas las firmas tienen `validSign` verdadero;
   - el `sha256Digest` informado es el del archivo enviado;
   - alguna firma menciona `REINCIDENCIA` en el nombre, la organización o la unidad
     (`tinku.cap.validador.firmante-esperado`, sin tildes ni mayúsculas).
   - Si la firma es válida pero el firmante es otro, el CAP queda en `FIRMANTE_NO_RECONOCIDO`.
     Sin firmas, con una firma inválida o con el PDF protegido, queda en `INVALIDA`.
4. **Fail-closed (Constitución Art. II).**
   - Con el validador encendido, **solo se aprueba un CAP con `firma_estado = VALIDA`**
     (409 en otro caso).
   - Rechazar o mandar a revisión legal sigue siempre disponible.
   - La firma válida no aprueba nada sola: Moderación sigue revisando el contenido.
   - Con el validador encendido, el CAP tiene que ser el PDF original: una foto o captura no
     tiene firma (422 al subirla).
5. **Minimización (Art. V).**
   - Al validador solo viaja el PDF, sin ningún otro dato del Tutor.
   - Se guarda el firmante, un detalle corto y el hash, no la respuesta completa.
   - El servicio es del Estado nacional, el mismo que emite el certificado.
6. **Configuración** (`tinku.cap.validador.*`). Encendido por defecto; apagado en el perfil
   `dev` y en la suite, que lo prueba con un validador simulado. Una prueba opcional contra el
   validador real corre con `CAP_VALIDADOR_REAL=1`.

## Consecuencias y riesgos
- **API no documentada.** Si el validador cambia sus pedidos, los CAP quedan en `ERROR` y no se
  pueden aprobar (fail-closed) hasta corregir el cliente (`ValidadorFirmasGobAr`). Mientras
  tanto se puede apagar con `CAP_VALIDADOR_HABILITADO=false`, que vuelve a la revisión manual de
  ADR-M1-04.
- **Uso de un servicio público.** El espaciado (un pedido abierto por vez, cada 30 s, y nunca
  con la cola del sitio llena) mantiene el uso en el nivel de una persona que valida a mano. Si
  el volumen crece, hay que pedir un acceso formal o pasar a la opción 2.
- **Firmante esperado sin una muestra real.** El texto `REINCIDENCIA` se eligió porque el
  certificado lo emite ese registro, pero falta validarlo con un CAP real. Si la firma real
  dice otra cosa, se ajusta `CAP_VALIDADOR_FIRMANTE` sin tocar código.
- Reemplaza la decisión PT2 ("manual en el MVP") solo en lo que respecta a la firma. La revisión
  del contenido sigue siendo manual (ADR-M1-04).
