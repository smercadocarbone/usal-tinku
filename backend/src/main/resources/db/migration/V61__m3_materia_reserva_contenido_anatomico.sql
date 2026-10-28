-- ADR-M3-07 §4bis (modo salud del kill-switch): la Reserva guarda la materia de la clase y el
-- catálogo marca las materias donde mostrar el cuerpo humano es parte de la clase (Medicina y
-- Anatomía). Columnas nuevas: no se editan V9/V19/V20 (AGENTS.md §7).
ALTER TABLE matching.trayectos
    ADD COLUMN contenido_anatomico BOOLEAN NOT NULL DEFAULT false;

UPDATE matching.trayectos
   SET contenido_anatomico = true
 WHERE nivel = 'universitario'
   AND (anio_o_carrera = 'Medicina'
        OR materia ILIKE 'Anatom%'
        OR materia = 'Estructura y Función del Cuerpo Humano');

-- Opcional: las reservas viejas y las que salen de una solicitud de un Menor no la tienen.
ALTER TABLE reservas.reservas
    ADD COLUMN trayecto_id UUID REFERENCES matching.trayectos(id);
