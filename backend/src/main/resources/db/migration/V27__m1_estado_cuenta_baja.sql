-- FASE2-06 (AUD-017): el CHECK de estado_cuenta de V2 solo admite
-- ACTIVA/SUSPENDIDA. Para anonimizar en vez de DELETE (ADR-M1-05) hace falta
-- admitir BAJA. No se edita V2 (regla del repo): se dropea el CHECK anónimo
-- que V2 creó (auto-nombrado `usuarios_estado_cuenta_check`) y se recrea uno
-- con nombre propio.

ALTER TABLE identidad.usuarios
    DROP CONSTRAINT usuarios_estado_cuenta_check;

ALTER TABLE identidad.usuarios
    ADD CONSTRAINT chk_usuarios_estado_cuenta
        CHECK (estado_cuenta IN ('ACTIVA', 'SUSPENDIDA', 'BAJA'));