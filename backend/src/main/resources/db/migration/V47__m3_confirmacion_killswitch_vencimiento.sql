-- ADR-M3-07 §7: plazo de respuesta de la rama adultos (Tabla de Tiempos). Sin respuesta = "No".
ALTER TABLE aula.confirmaciones_killswitch
    ADD COLUMN vence_at TIMESTAMPTZ,
    ADD COLUMN vencida  BOOLEAN NOT NULL DEFAULT FALSE;
