# Plan — Mejoras para el Tutor y el Alumno (2026-09-26)

> Pedido del dueño del producto, 2026-09-26, después de revisar la plataforma "en el rol del
> Tutor" y "en el rol del Alumno". Las decisiones de negocio de este plan las tomó él (preguntas
> y respuestas del 2026-09-26, citadas como **D-n**). Todo cae dentro de los 9 módulos, pero son
> funcionalidades nuevas: se incorporan con la **enmienda v2.5 de la Constitución** (Art. VI) y
> los datos nuevos que se guardan se justifican contra el Art. V en sus ADR.

## Decisiones del dueño

| #   | Tema                                     | Decisión                                                                                                                                                                                                                                                         |
| --- | ---------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D-1 | Paquete mensual: cómo se paga            | Pago único por el mes. Sin devolución parcial por decisión del alumno.                                                                                                                                                                                             |
| D-2 | Paquete: falta del Tutor                 | Si el Tutor cancela o falta a una clase del paquete y no se recupera, se devuelve **exactamente lo de esa clase** (devolución parcial, solo en este caso). Enmienda FR-PAG-009 con ADR-M5-03.                                                                     |
| D-3 | Paquete: cancelación del alumno          | Con 24 hs o más, la clase se mueve a otro horario libre dentro del paquete; con menos, la clase se da por tomada y el Tutor cobra. Arrepentirse del paquete entero, solo antes de la primera clase (devolución total).                                              |
| D-4 | Pedido de reprogramación del Tutor       | El alumno (o el Adulto Responsable si es menor) acepta o cancela con devolución total hasta T-60 de la clase original. Sin respuesta a T-60: se cancela con devolución total y cuenta como cancelación del Tutor.                                                    |
| D-5 | Pizarra compartida                       | Solo en clases entre adultos. Con un Menor se habilita recién cuando el kill-switch del cliente (T-M3-06) también la analice.                                                                                                                                      |
| D-6 | Pedido previo ("qué querés ver")         | Texto (filtrado como las calificaciones) + 1 archivo (foto o PDF), borrado 24 hs después del fin de la clase. Lo escribe quien reserva; con un menor lo escribe y lo ve el Adulto Responsable.                                                                      |
| D-7 | Presentación del Tutor                   | Video corto subido a Tinku (no links externos), revisado por el Admin antes de publicarse.                                                                                                                                                                         |
| D-8 | Control de agenda                        | El descanso entre clases lo decide el Tutor con sus franjas; el máximo de clases por día no hace falta. Sí: pedido de reprogramación del Tutor (D-4).                                                                                                               |
| D-9 | Lo demás                                 | Precio neto visible al Tutor; export mensual de cobros para ARCA; próximo horario libre y filtro por precio en la búsqueda; nota del Tutor al Adulto Responsable en clases con menores; prueba en pantalla chica.                                                   |

**Una aclaración sobre "el Tutor afronta los costos de MercadoPago" (D-4):** hoy la devolución
de una clase es **total** y con el cuerpo vacío. En ese caso MercadoPago devuelve también su propia
comisión (FR-PAG-009), así que no queda un costo de transferencia que cobrarle a nadie: el alumno
recibe el 100 % y el Tutor no cobra esa clase, que es la consecuencia. En la devolución **parcial**
del paquete (D-2) la plata sale de la cuenta de MercadoPago del Tutor (modelo A, ADR-M5-02). Lo
que MercadoPago no devuelva de su comisión en un parcial queda del lado del Tutor. Esto es lo
opuesto a FR-PAG-010 (disputas manuales), donde la diferencia la absorbe Tinku. Así se cumple la
regla del dueño sin inventar un cargo nuevo.

## Orden de implementación

De lo más simple a lo más complejo. Cada punto es un commit (o más) con sus tests y deja la
suite en verde.

1. **P1: Precio neto** (M5). Backend y frontend.
2. **P2: Export de cobros por mes** (M5).
3. **P3: Búsqueda: próximo horario libre y filtro por precio** (M2/M4).
4. **P4: Nota del Tutor al Adulto Responsable** (M4).
5. **P5: Pedido previo con adjunto** (M4).
6. **P6: Pedido de reprogramación del Tutor** (M4/M5).
7. **P7: Video de presentación** (M1/M8).
8. **P8: Pizarra compartida** (M3).
9. **P9: Paquete mensual** (M4/M5).
10. **P10: Prueba en pantalla chica** (transversal).

## Diseño por punto

### P1 — Precio neto (FR-PAG-019)

- `GET /api/pagos/tarifa` suma `comisionPorcentaje` (el mismo valor que usa `ComisionPlataforma`,
  nunca un número repetido en el frontend).
- La pantalla "Precio" muestra, al lado del precio por hora: **"Te quedan $X por hora"**. Con un
  desglose de una línea: "Tinku se queda con el 27 %. MercadoPago te cobra aparte su comisión
  según el plazo de acreditación que elegiste en tu cuenta".
- El alumno sigue viendo solo el precio final (Art. III): el neto aparece únicamente en la
  pantalla del Tutor.

### P2 — Export de cobros por mes (FR-PAG-020)

- `GET /api/pagos/cobros/export?mes=AAAA-MM` → `text/csv; charset=UTF-8` con BOM, separador `;`
  (Excel en español) y nombre `tinku-cobros-AAAA-MM.csv`.
- El mes es el **mes del cobro** (`transacciones.created_at`, hora de Argentina): es la fecha en
  que el Tutor factura.
- Columnas:
  - fecha del cobro y fecha de la clase;
  - quién pagó: nombre, apellido y DNI. El DNI solo si es un adulto; es el receptor de la factura;
  - precio de la clase, comisión de Tinku y neto;
  - estado (retenido, en revisión, liberado, reembolsado);
  - número de operación de MercadoPago.
- Las transacciones en modo prueba (bypass) no salen. Lo reembolsado sale con su estado, para
  que el Tutor lo anule si ya lo facturó.
- **Dato nuevo expuesto:** el DNI del pagador adulto. Se justifica en FR-PAG-020, porque el
  Tutor lo necesita para facturar a nombre de quien pagó. Nunca sale el DNI de un menor, que
  nunca es el pagador.
- Frontend: en "Mis cobros", un selector de mes y el botón "Descargar para ARCA".

### P3 — Próximo horario libre y filtro por precio (FR-MATCH-013/014)

- `BusquedaResponse` suma `precioHora` (tarifa vigente) y `proximoHorario` (el primer bloque
  libre desde ahora + la ventana mínima, dentro de los próximos 14 días; `null` si no hay).
  Reusa `HorariosDisponiblesService`: no hay una segunda lógica de disponibilidad.
- `BusquedaRequest` suma `precio_max_hora` (opcional). El filtro corre en Java, después del
  ranking y antes de devolver: no toca el matching-service.
- Frontend `/buscar`:
  - un control "Precio máximo por hora";
  - cada tarjeta muestra "Próximo horario: jue 18:00" o "Sin horarios en las próximas 2 semanas".
- Tabla de Tiempos: fila "Horizonte del próximo horario libre: 14 días".

### P4 — Nota del Tutor al Adulto Responsable (FR-RES-026)

- Solo en Reservas cuyo beneficiario es **Menor**, en estado `finalizada`. La escribe el Tutor
  una vez, hasta 1000 caracteres, y puede corregirla dentro de las 48 hs (igual que la
  calificación).
- Pasa por el mismo filtro de anonimización que las calificaciones (FR-REP-011). No es un
  resumen automático: no se graba nada (Art. II y V).
- La ven el Adulto Responsable y el Tutor en el detalle de la reserva. El Menor no la ve: es un
  canal Tutor→AR.
- Aviso al AR: `NOTA_CLASE` (in-app + email).
- Tabla `reservas.notas_clase` (reserva_id PK, texto, created_at, updated_at).

### P5 — Pedido previo con adjunto (FR-RES-027/028)

- Tabla `reservas.pedidos_previos`:
  - `reserva_id` PK y `texto` (hasta 1000 caracteres, filtrado);
  - `archivo_ref`, `archivo_nombre` y `archivo_tipo` (null si no hay archivo);
  - `created_at`, `updated_at`.
- Lo carga o edita el **pagador**, mientras la reserva está `pendiente_pago` o `confirmada` y
  antes del inicio. Con un menor, el pagador es siempre el AR (Art. II): el Menor no escribe ni
  sube nada.
- Archivo: JPG, PNG o PDF de hasta 5 MB, uno solo. Se valida el tipo por los primeros bytes,
  no por la extensión.
- Lo ven el Tutor y el pagador. El beneficiario adulto es el mismo pagador.
- **Borrado:**
  - un job de Quartz persistido borra el archivo 24 hs después del fin agendado de la clase;
  - la cancelación lo borra en el acto;
  - el texto queda: es parte de la reserva, como el horario.
  - Tabla de Tiempos: fila "Retención del adjunto del pedido previo".
- Endpoints: `PUT /api/reservas/{id}/pedido` (multipart: `texto` + `archivo` opcional),
  `DELETE /api/reservas/{id}/pedido/archivo` y `GET /api/reservas/{id}/pedido/archivo`.
- Frontend:
  - un paso opcional en `/reservar`: "¿Qué querés ver?", texto y archivo;
  - una tarjeta en el detalle de la reserva, editable hasta el inicio.

### P6 — Pedido de reprogramación del Tutor (FR-RES-029..031)

- Tabla `reservas.pedidos_reprogramacion`:
  - `id`, `reserva_id` (un solo pedido `pendiente` por reserva, índice parcial único);
  - `horario_propuesto` y `motivo` (hasta 300 caracteres, filtrado);
  - `estado` (`pendiente|aceptado|rechazado|vencido|retirado`);
  - `created_at` y `resuelto_at`.
- El Tutor lo pide sobre una reserva `confirmada`, con más de 1 hora a la clase original
  (`LIMITE_REPROGRAMACION`). El horario propuesto tiene que:
  - entrar en una de sus franjas;
  - estar libre;
  - estar a más de la ventana mínima.

  No hay límite de pedidos, pero hay uno solo abierto a la vez.
- Mientras está pendiente, la clase sigue en el horario original. El alumno (pagador; con un
  menor, el AR) ve:
  - **Aceptar**: la reserva pasa al horario nuevo, con el mismo flujo que `reprogramar`: evento
    `reserva.reprogramada`, re-agenda la Sesión, conserva el precio;
  - **Cancelar y recibir la devolución**: cancela la reserva con el Tutor como quien cancela.
    `PoliticaCancelacion` da reembolso total; en un paquete (P9) es la devolución de esa clase.
- **Vencimiento:** job Quartz persistido a T-60 de la clase original. Si sigue `pendiente`, el
  pedido pasa a `vencido` y la reserva se cancela como en "Cancelar", con el Tutor como quien
  cancela.
- El Tutor puede retirar el pedido mientras está pendiente.
- Avisos:
  - `REPROGRAMACION_PEDIDA` al pagador (y al beneficiario si es otro adulto);
  - `REPROGRAMACION_ACEPTADA` o `REPROGRAMACION_RECHAZADA` al Tutor;
  - al vencer, `CLASE_CANCELADA` a los dos.
- El Menor no acepta ni rechaza nada: el pedido va al AR (Art. II).

### P7 — Video de presentación (FR-ID-034..036, FR-ADM-011)

- `identidad.videos_presentacion`:
  - `tutor_id` PK, `archivo_ref`, `tipo` y `duracion_segundos`;
  - `estado` (`pendiente|aprobado|rechazado`) y `motivo_rechazo`;
  - `created_at` y `revisado_at`.

  Un video por Tutor: subir otro reemplaza al anterior y vuelve a `pendiente`.
- Límites:
  - MP4 o WebM, hasta 90 segundos (lo mide el navegador al elegirlo y lo manda) y hasta 30 MB;
  - el backend valida el tipo por los primeros bytes y el tamaño.
  - Para subirlo se sube el límite global de multipart a 30 MB. Las subidas existentes
    (credencial, CAP, foto, evidencia) pasan a validar su propio límite de 5 MB en código: hoy
    dependen del límite global.
- Moderación: nueva cola en `/admin` (Moderación y Seguridad) con reproductor, Aprobar y
  Rechazar con motivo. Aviso al Tutor: `VIDEO_REVISADO`.
- Perfil público: el video aparece solo `aprobado`. `GET /api/tutores/{id}/video` sirve los
  bytes con `Range` para que el navegador pueda adelantar.
- Borrado: al reemplazarlo, al borrarlo el Tutor o con la baja de la cuenta. Sin retención extra.
- Justificación Art. V en **ADR-M1-09**: el video lo publica el propio Tutor adulto, con
  finalidad explícita y consentida (su presentación comercial). No es un video de una clase: la
  prohibición de grabar clases no cambia. Se descartan los links externos (YouTube, Drive)
  porque abren un canal de contacto fuera de la plataforma sin moderación.

### P8 — Pizarra compartida (FR-AULA-011..013)

- Pizarra propia sobre `<canvas>`, sin dependencias nuevas, sincronizada por el canal de datos de
  LiveKit (`publishData`, reliable, `topic: "pizarra"`).
  - Herramientas: lápiz, 4 colores, goma, deshacer y borrar todo.
  - Los trazos viajan como segmentos normalizados 0..1, para que se vean igual en pantallas de
    distinto tamaño.
  - Quien entra tarde pide el estado (`pizarra:sync`) y el otro participante se lo manda.
- **Nada se persiste:** ni en el servidor ni en la base. Al terminar la clase desaparece. Se
  puede descargar como PNG localmente; eso no sale de la máquina de quien la descarga.
- **Solo adultos (D-5):** `TokenResponse` de la sala suma `pizarraHabilitada`, que el backend
  calcula como "ningún participante es Menor". Si es `false`:
  - el botón no aparece;
  - los mensajes `pizarra` que lleguen se descartan en el cliente.
- Justificación de la elección técnica en **ADR-M3-06**: Excalidraw y tldraw se descartan por
  peso y licencia (tldraw no es libre para uso comercial). Un servidor de colaboración (Yjs) se
  descarta porque LiveKit ya da el canal.

### P9 — Paquete mensual (FR-RES-032..037, FR-PAG-021..023, ADR-M5-03)

**Modelo:**

- `pagos.config_paquete_tutor`: `tutor_id` PK, `habilitado` y `descuento_porcentaje` (0 a 30).
  El Tutor lo activa en "Precio". El descuento no puede llevar el precio por hora por debajo del
  piso (T06).
- `reservas.paquetes`:
  - `id`, `pagador_id`, `beneficiario_id` y `tutor_id`;
  - `cantidad_clases` (4) y `duracion_minutos`;
  - `precio_total`: suma de las clases con el descuento, congelado;
  - `estado` (`pendiente_pago|confirmado|cancelado|finalizado`);
  - `vigente_hasta`: 4 semanas desde la primera clase;
  - `created_at`.
- `reservas.reservas.paquete_id` (nullable). Cada clase del paquete es una Reserva normal con su
  `precio` (la parte de la clase, con el descuento). Así el aula, los no-show, las calificaciones
  y los avisos siguen iguales.

**Reserva y pago:**

1. El alumno elige "Paquete del mes" en `/reservar`: un horario y la duración. El sistema genera
   las 4 clases semanales (mismo día y hora, 4 semanas seguidas) y valida cada una como una
   reserva suelta:
   - franja, superposición y ventana mínima;
   - Tutor reservable, Menor autorizado y CAP.

   Si alguna no entra, responde 422 con la lista de fechas que chocan.
2. Se crean el `paquete` y sus 4 reservas en `pendiente_pago`, con un solo timeout de 15 min
   (el del paquete: vence todo junto).
3. `POST /api/pagos/paquetes/{id}/preferencia`:
   - una preferencia por el total, `external_reference = "paquete:{id}"`;
   - `marketplace_fee` = 27 % del total;
   - con el token del Tutor.
4. Webhook, retorno y conciliación reconocen el prefijo `paquete:`. Con el pago aprobado y el
   monto igual al total:
   - una `pagos.pagos_paquete` (`paquete_id` PK, `mp_payment_id` único, monto) como ancla de
     idempotencia;
   - una `Transaccion` por clase con `paquete_id` y el mismo `mp_payment_id`. La unicidad de
     `mp_payment_id` pasa a un índice parcial `WHERE paquete_id IS NULL`, en una migración nueva;
   - confirma las 4 reservas, lo que dispara el evento de cada una y M3 crea cada Sesión.

**Cancelaciones y devoluciones:**

| Caso                                                                                   | Qué pasa                                                                                                                         |
| -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| El pagador cancela **el paquete** con ≥24 hs a la primera clase                         | Devolución **total** del pago; las 4 reservas `cancelada`.                                                                       |
| El pagador cancela **una clase**                                                        | Sin devolución (D-1). Con ≥24 hs la UI ofrece moverla (P6-like, pero la decide él); si igual cancela, el Tutor cobra esa clase. |
| El pagador mueve una clase (≥24 hs)                                                     | `reprogramar`, con el horario nuevo **dentro de la vigencia** del paquete.                                                       |
| El Tutor cancela una clase, falta (no-show), el pedido de reprogramación vence o se rechaza, o la clase se interrumpe (<50 %) | Devolución **parcial** del precio de esa clase (D-2), con clave de idempotencia por transacción.                                  |
| Sanción, CAP vencido o baja del menor                                                   | Cada clase futura sigue la regla de su motivo: las que M5 devolvería se devuelven por clase.                                     |
| Kill-switch o denuncia                                                                  | Igual que hoy (pausa y resolución), por clase. La devolución, si corresponde, es parcial.                                         |

- Regla única en `EscrowService.reembolsarSiRetenida`: si la transacción es de un paquete,
  llama a `ReembolsoParcialProveedor` por `montoBruto` de esa clase. Si el paquete entero está
  sin consumir y todo lo retenido es el total, llama a `reembolsarTotal`.
- Si MercadoPago rechaza el parcial (por ejemplo, el Tutor retiró la plata), no se aborta la
  cancelación: la transacción queda `reembolso_fallido` en la cola de Soporte Financiero. Ya
  existe para el adicional (R4); se reusa el mismo outbox con reintentos 5/15/60.
- **Liberación:** sin cambios, por clase (fin + 24 hs). Con el modelo A la plata ya está en la
  cuenta del Tutor. La "liberación" es la ventana lógica en la que Tinku puede devolver.
- Tabla de Tiempos: "Vigencia del paquete mensual: 4 semanas desde la primera clase",
  "Arrepentimiento del paquete: hasta 24 hs antes de la primera clase" y "Mover una clase del
  paquete: hasta 24 hs antes, dentro de la vigencia".
- Paquete con un menor: lo arma y lo paga el AR, con las mismas validaciones (autorización, CAP
  y flag T-TES-10). El Menor no puede pedir un paquete, solo Solicitudes sueltas.
- El adicional de resumen no se ofrece en paquetes en esta versión (FR fuera de alcance).

### P10 — Prueba en pantalla chica

- Recorrido con Playwright a 360×740 y 390×844 de:
  - landing, login y registro en pasos;
  - buscar, perfil, reservar (suelta y paquete) y pagar;
  - Mis clases, detalle, pedido previo y nota;
  - "Mi cuenta" del Tutor: precio, cobros, horarios y video;
  - aula: lobby, llamada, pizarra y finalizar;
  - Admin: colas.
- Criterio de "roto":
  - scroll horizontal de la página;
  - un botón o texto cortado o tapado;
  - un objetivo táctil de menos de 44 px;
  - un modal que no entra en la pantalla.
- Se corrige y se agrega un spec e2e `@mobile` que verifica que no haya scroll horizontal en
  esas rutas.

## Estudio del control de agenda (D-8)

| Pedido posible                         | Conclusión                                                                                                                                                                                  |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Descanso entre clases                  | **No se construye.** El Tutor ya lo controla publicando franjas con huecos: una franja 18:00-19:00 y otra 19:30-21:00 dejan media hora libre. Es más simple y es lo que el dueño eligió.       |
| Máximo de clases por día               | **No se construye** (D-8). Mismo argumento: la cantidad de horas publicadas ya es el máximo.                                                                                                 |
| Pedido de reprogramación del Tutor     | **Se construye** (P6). Es lo que faltaba: hoy el Tutor solo puede cancelar, y eso castiga al alumno con una clase perdida aunque se le devuelva la plata.                                      |
| Bloquear días (vacaciones)             | **Recomendado a futuro, no incluido.** Hoy se hace desactivando franjas, que es tedioso si son semanales. Una "fecha bloqueada" que tape las franjas de ese día sería chica. Queda anotada, no se hace sin pedido. |
| Anticipación mínima propia del Tutor   | **Recomendado a futuro, no incluido.** Hoy es 30 min para todos (Tabla). Si los Tutores se quejan de reservas encima de la hora, se agrega un mínimo por Tutor (30 min a 24 hs).                        |

## Lo que queda afuera a propósito

- Chat libre Tutor↔alumno: el pedido previo y la nota cubren la necesidad sin abrir un canal
  privado con menores.
- Pizarra con menores: hasta T-M3-06 (D-5).
- Adicional de resumen dentro de un paquete.
- Paquetes de otra duración que 4 semanas o con más de una clase por semana.

## Documentos que cambian

- **Constitución:** enmienda v2.5, Art. VI. AGENTS.md se actualiza en el mismo commit.
- **Specs:**
  - M1: US-8, video;
  - M2: US-9, próximo horario y precio;
  - M3: US-9, pizarra;
  - M4: US-11 a US-14, pedido previo, nota, pedido de reprogramación y paquete;
  - M5: US-10 a US-12, precio neto, export y pago del paquete. Enmienda a FR-PAG-009;
  - M8: US-9, cola de videos.
- **ADRs:**
  - ADR-M1-09: video de presentación;
  - ADR-M3-06: pizarra;
  - ADR-M4-01: pedido previo con adjunto y su retención;
  - ADR-M5-03: paquete mensual y devolución parcial por falta del Tutor.
- **Tabla de Tiempos:** 7 filas nuevas (horizonte del próximo horario, retención del adjunto,
  vencimiento del pedido de reprogramación, vigencia del paquete, arrepentimiento del paquete,
  mover una clase del paquete y corrección de la nota del Tutor).
- **Tasks:** `Tasks_Tinku_Implementacion.md` y `Tasks_Tinku_Chunks.md`, juntos: sección
  "MEJORAS-TA".
