-- Sala de espera: cuándo el Tutor dejó pasar al beneficiario a la clase. Si el alumno sale
-- (vuelve atrás, recarga, se le corta) y entra de nuevo, va directo a la clase sin volver a
-- esperar. Antes la admisión vivía solo en la memoria del navegador del Tutor y se perdía si
-- él también recargaba. Columna nueva: no se edita V8/V26 (AGENTS.md §7).
ALTER TABLE aula.sesiones_aprendizaje
    ADD COLUMN estudiante_admitido_at TIMESTAMPTZ;
