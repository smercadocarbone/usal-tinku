-- FASE2-10 (AUD-005, riesgo abierto aceptado en ADR-M3-02): el kill-switch pasa a
-- pausar el escrow en `pausado_alerta` — la Alerta de seguridad (track del menor,
-- Artículo II) manda sobre la Denuncia. La pausa por Denuncia sigue en
-- `pausado_denuncia`. No se edita V11 (A1): se reemplaza el CHECK con una migración nueva.
ALTER TABLE pagos.transacciones DROP CONSTRAINT transacciones_estado_check;
ALTER TABLE pagos.transacciones ADD CONSTRAINT transacciones_estado_check
    CHECK (estado IN ('retenido_escrow', 'liberado', 'reembolsado',
                      'pausado_denuncia', 'pausado_alerta'));