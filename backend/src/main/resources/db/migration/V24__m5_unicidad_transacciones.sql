-- AUD-010 (auditoria 2026-09-21): pagos.transacciones no tenia indice unico ni
-- sobre mp_payment_id ni sobre reserva_id. La idempotencia del webhook de
-- MercadoPago dependia solo de un check-then-act en aplicacion
-- (EscrowService.procesarPagoAprobado: findByMpPaymentId().isPresent()), sin
-- proteccion de base. MP reintenta agresivamente los webhooks no-2xx y los
-- reintentos pueden solaparse: dos hilos pasan el guard antes de que el
-- primero commitee -> dos filas para la misma reserva -> findByReservaId
-- (usado en 6 puntos de EscrowService + DenunciaService.tieneEscrowActivo +
-- ResumenService) tira IncorrectResultSizeDataAccessException para siempre en
-- esa reserva: liberacion, reembolso, pausa por denuncia y resumen quedan
-- rotos, con el dinero atascado (ver javadoc de EscrowService.reembolsarPagoTardio,
-- que ya anticipaba esta migracion).
--
-- Verificado en dev (docker compose up -d db) antes de escribir esta migracion:
--   SELECT mp_payment_id, count(*) FROM pagos.transacciones GROUP BY 1 HAVING count(*)>1; -- 0 filas
--   SELECT reserva_id,     count(*) FROM pagos.transacciones GROUP BY 1 HAVING count(*)>1; -- 0 filas
-- Una base de dev vacia no prueba nada sobre otros entornos.
--
-- NUNCA editar V11 (guardrail A1): una migracion ya aplicada no se toca, ni
-- para agregar un indice — rompe el checksum de Flyway en cualquier entorno
-- que ya la corrio.
SET search_path TO pagos, public;

CREATE UNIQUE INDEX uq_transacciones_mp_payment ON pagos.transacciones(mp_payment_id);
CREATE UNIQUE INDEX uq_transacciones_reserva ON pagos.transacciones(reserva_id);
