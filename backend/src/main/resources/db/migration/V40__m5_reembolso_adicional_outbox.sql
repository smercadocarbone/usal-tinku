-- R4 (BR-PAG-11): el reembolso del adicional de resumen deja de ser "loguear y seguir". Queda
-- persistido como outbox con reintentos Quartz (3, backoff 5/15/60 min, Tabla de Tiempos) y, si
-- se agotan, en la cola de Soporte Financiero. adicional_reembolsado_at se conserva.
ALTER TABLE pagos.transacciones
    ADD COLUMN adicional_reembolso_estado VARCHAR(20)
        CHECK (adicional_reembolso_estado IN ('PENDIENTE', 'FALLIDO', 'HECHO', 'RESUELTO_MANUAL')),
    ADD COLUMN adicional_reembolso_intentos INT NOT NULL DEFAULT 0,
    ADD COLUMN adicional_reembolso_error VARCHAR(300),
    ADD COLUMN adicional_reembolso_nota VARCHAR(300);

-- Los ya reembolsados antes de esta migración quedan como HECHO.
UPDATE pagos.transacciones SET adicional_reembolso_estado = 'HECHO' WHERE adicional_reembolsado_at IS NOT NULL;
