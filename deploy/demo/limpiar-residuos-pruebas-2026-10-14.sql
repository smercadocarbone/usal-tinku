-- Limpieza de los datos que dejaron las pruebas exploratorias del 2026-10-14
-- (docs/operacion/HALLAZGOS_pruebas.md §8 y HALLAZGOS_aula_en_vivo.md §6).
--
-- Cómo correrlo (lo hace el dueño, no el agente):
--   1. Backup antes de tocar nada (RUNBOOK_produccion.md §7).
--   2. psql -f limpiar-residuos-pruebas-2026-10-14.sql  → termina en ROLLBACK a propósito: mirá el
--      conteo de cada paso, y si todo da lo esperado cambiá la última línea a COMMIT y volvé a correr.
--   3. Cada UPDATE/DELETE lleva WHERE por id o por DNI de las cuentas del seed: no toca nada más.
--
-- Todas las tablas y columnas de abajo están verificadas contra las migraciones V2, V7, V9 y V13.

BEGIN;

-- 1) T3-01: Benjamín (50000002) quedó con capacidad_estudiante = true por un PATCH que no debía
--    aceptarse. Originalmente estaba en false. (El arreglo de código es M3C-10 / R-A.)
UPDATE identidad.usuarios
   SET capacidad_estudiante = FALSE
 WHERE dni = '50000002' AND tipo = 'MENOR' AND capacidad_estudiante = TRUE;
-- esperado: UPDATE 1 (o 0 si ya se corrigió)

-- 2) T2-H2: franja puntual del 2020-01-01 creada en el Tutor 31000030; no había forma de borrarla.
DELETE FROM reservas.franjas_disponibilidad
 WHERE id = 'd2015987-09a5-44e5-9be2-07c5cd66a913'
   AND fecha_especifica = DATE '2020-01-01';
-- esperado: DELETE 1

-- 3) T4-H3: dos denuncias que quedaron EN_REVISION y que nadie puede resolver porque el único admin de
--    Moderación es parte de ambas. Las crearon las pruebas. Se cierran como infundadas.
--    (Si ya hay un segundo admin de Moderación, es preferible que las resuelva él desde el panel.)
UPDATE seguridad.denuncias
   SET estado = 'RESUELTA_INFUNDADA', resuelta_at = now()
 WHERE id IN ('154ab2d0-f4fb-436f-9661-9d8f2bd91e3d', '9422a2c7-a2fc-4246-af6c-31b64cf3e695')
   AND estado = 'EN_REVISION';
-- esperado: UPDATE 2

-- 4) T1-6: 3 búsquedas guardadas en 40000001 que no se podían borrar (no existe DELETE).
DELETE FROM matching.busquedas_guardadas
 WHERE usuario_id = (SELECT id FROM identidad.usuarios WHERE dni = '40000001');
-- esperado: DELETE 3 (o más si alguien guardó otras a mano: revisá antes de confirmar)

-- Lo que NO se limpia (a propósito):
--  * Las reservas de prueba: están canceladas o finalizadas, en bypass, sin dinero real.
--  * Las advertencias y credenciales de T4 sobre 31000029 y 31000030: son datos del seed de pruebas.
--  * El CAP aprobado de 31000028 (Lorena): lo usa el escenario de menores.

ROLLBACK;   -- ← cambiar a COMMIT cuando los conteos coincidan
