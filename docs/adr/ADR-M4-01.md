# ADR-M4-01 — Pedido previo a la clase, solo texto

**Estado:** Aceptado (decisión del dueño del producto). Primera versión el 2026-09-26 (D-6 de
`docs/superpowers/plans/2026-09-26-mejoras-tutor-alumno.md`): texto y un archivo que se borraba a las
24 hs. **Enmendado el 2026-09-27:** el dueño pidió que sea **solo texto**, sin archivos.

## Contexto
- El Tutor llega a la clase sin saber qué necesita el alumno, y el alumno pierde los primeros
  minutos explicándolo.
- No hay chat entre las partes, y con Menores no debe haber un canal privado Tutor↔Menor
  (Art. II).

## Decisión
1. **Qué es.** Un texto de hasta 1000 caracteres ("qué querés ver, para cuándo es el examen").
   Sin archivos: el ejercicio se muestra en la clase compartiendo pantalla.
2. **Quién.**
   - Lo escribe el **pagador**, que con un Menor es siempre su Adulto Responsable. El Menor no
     escribe nada.
   - Lo ven el pagador y el Tutor.
   - Es un mensaje en un solo sentido, no un chat.
3. **Filtro.** El texto pasa por la misma anonimización que M6: los teléfonos, emails y documentos
   se reemplazan.
4. **Cuándo.** Se escribe y se corrige con la reserva pendiente de pago o confirmada, hasta que
   empieza la clase.
5. **Retención (Art. V).** El texto queda con la reserva, como el horario: es parte de lo que se
   contrató. No hay archivos que guardar ni borrar.

## Consecuencias
- Sin almacenamiento de archivos ni jobs de borrado para esta función.
- La migración V44 crea `reservas.pedidos_previos` solo con el texto. Se corrigió antes de llegar a
  `main`: nunca se aplicó con las columnas del archivo en una base real.
