# ADR-M4-01 — Pedido previo a la clase con un adjunto que se borra a las 24 hs

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-26; D-6 de
`docs/superpowers/plans/2026-09-26-mejoras-tutor-alumno.md`).

## Contexto
- El Tutor llega a la clase sin saber qué necesita el alumno, y el alumno pierde los primeros
  minutos explicándolo.
- No hay chat entre las partes, y con Menores no debe haber un canal privado Tutor↔Menor
  (Art. II).

## Decisión
1. **Qué es.** Un texto de hasta 1000 caracteres ("qué querés ver, para cuándo es el examen") y,
   opcional, **un** archivo:
   - JPG, PNG o PDF, hasta 5 MB;
   - el tipo se valida por los primeros bytes.
2. **Quién.**
   - Lo escribe el **pagador**, que con un Menor es siempre su Adulto Responsable. El Menor no
     escribe ni sube nada.
   - Lo ven el pagador y el Tutor.
   - Es un mensaje en un solo sentido, no un chat.
3. **Filtro.** El texto pasa por la misma anonimización que las calificaciones (FR-REP-011): los
   teléfonos, emails y documentos se reemplazan.
4. **Retención (Art. V).**
   - El archivo solo sirve para preparar esa clase. Se borra 24 hs después del fin agendado, con
     un job de Quartz persistido, o en el acto si la reserva se cancela.
   - El texto queda con la reserva, como el horario, porque es parte de lo que se contrató.

## Consecuencias
- Un job persistido más por Reserva con adjunto.
- El archivo se sirve solo por un endpoint autenticado que verifica que quien lo pide es el Tutor o
  el pagador. Nunca por una URL pública.
