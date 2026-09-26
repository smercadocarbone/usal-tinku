# ADR-M5-03 — Paquete mensual: un pago por el mes y devolución parcial solo por falta del Tutor

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-26; D-1, D-2 y D-3 de
`docs/superpowers/plans/2026-09-26-mejoras-tutor-alumno.md`). Enmienda FR-PAG-009 de Spec_M5.

## Contexto
- El Tutor quiere alumnos que vuelvan y el alumno quiere resolver el mes de una vez.
- Hoy cada clase se reserva y se paga por separado.
- Con el modelo A (ADR-M5-02), la plata entra a la cuenta de MercadoPago del Tutor al pagar y el
  escrow es una ventana lógica.
- FR-PAG-009 prohíbe toda devolución parcial automática: la única excepción es la del adicional de
  resumen (BR-PAG-11).

## Opciones
1. **Pago único, devolución por clase.** El caso general sería parcial, lo que choca de lleno con
   FR-PAG-009.
2. **Clases fijas, pago por clase.** No toca las reglas, pero no es "pagar el mes".
3. **Pago único, sin devolución parcial.** Las cancelaciones del alumno se resuelven moviendo la
   clase. Es la elegida, con una excepción (D-2).

## Decisión
1. **Qué es un paquete.**
   - 4 clases semanales: mismo día, hora y duración, 4 semanas seguidas.
   - Lo arma y lo paga el pagador; con un Menor, su Adulto Responsable.
   - Cada clase es una `Reserva` normal con `paquete_id` y su parte del precio. Así, el aula, los
     no-show, las calificaciones y los avisos no cambian.
   - La **vigencia** es de 4 semanas desde la primera clase (Tabla de Tiempos).
2. **Precio.**
   - El Tutor lo habilita y puede fijar un descuento de 0 a 30 % sobre su precio por hora.
   - El precio por hora con descuento no puede quedar por debajo del piso (T06).
   - El total se congela en el paquete (FR-PAG-013).
   - La comisión (27 %) se calcula sobre el total.
3. **Un solo pago.**
   - Una preferencia por el total, con `external_reference = "paquete:{id}"` y el token del Tutor.
   - Con el pago aprobado se crea `pagos.pagos_paquete` como ancla de idempotencia (con
     `mp_payment_id` único) y una `Transaccion` por clase con `paquete_id`.
   - La unicidad de `transacciones.mp_payment_id` pasa a un índice parcial para las transacciones
     sin paquete (migración nueva; la V24 no se edita).
4. **Cancelaciones del alumno: sin devolución parcial (D-1, D-3).**
   - El paquete entero se puede cancelar hasta 24 hs antes de la primera clase, con devolución
     **total**.
   - Después, una clase con 24 hs o más se **mueve** a otro horario libre dentro de la vigencia.
   - Una clase cancelada por el alumno se da por tomada: el Tutor la cobra.
5. **Falta del Tutor: devolución parcial (D-2).** Se devuelve exactamente el precio de esa clase
   cuando es una de las situaciones en que una clase suelta se devolvería entera:
   - el Tutor cancela;
   - el Tutor no se presenta;
   - su pedido de reprogramación vence o el alumno lo rechaza;
   - la clase se interrumpe antes del 50 %;
   - una denuncia escalada o una alerta resuelta.

   Mismo criterio que M5 para una clase suelta: el alumno no pierde plata por algo que no decidió.
   Sigue ejecutándose por el camino automático, pero **solo** para transacciones de un paquete.
6. **Quién absorbe la comisión de MercadoPago en el parcial.**
   - El parcial se ejecuta con el token del Tutor y sale de su cuenta.
   - Lo que MercadoPago no devuelva de su comisión en un parcial queda del lado del Tutor. Esta es
     la regla del dueño: "el Tutor afronta los gastos de MercadoPago".
   - Es lo contrario de FR-PAG-010 (disputas manuales), donde lo absorbe Tinku.
7. **Si el parcial falla.** Por ejemplo, porque el Tutor retiró la plata:
   - la cancelación no se aborta;
   - la transacción entra al mismo outbox con reintentos de R4 (5/15/60 min);
   - agotados los reintentos, pasa a la cola de Soporte Financiero.

   Esto evita que un error de MercadoPago deje una clase "confirmada" que nadie va a dar.

## Consecuencias
- FR-PAG-009 pasa a decir: "prohibido el reembolso parcial automático, salvo BR-PAG-11 (adicional)
  y ADR-M5-03 (clase de un paquete por falta del Tutor)".
- Riesgo aceptado: el Tutor puede retirar la plata del paquete antes de dar las clases. La
  mitigación es la cola de Soporte Financiero y la sanción de cuenta (M9) si no devuelve. Tinku no
  retiene fondos: el modelo A no lo permite sin volver a recaudar todo (modelo B, descartado en
  ADR-M5-02).
- El adicional de resumen no se ofrece dentro de un paquete (queda para una versión posterior).
