# FASE2-06 — Baja de menor por anonimización, no por DELETE (AUD-017)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** medio (datos de menores, Ley 25.326) ·
**Finding:** AUD-017 · **Decisión:** D7 (anonimización).

## 1. Problema (verificado al 2026-09-22)

`UsuarioService.darDeBajaMenor` borra `autorizaciones_tutor` y `consentimientos_menor` y después
hace `usuarioRepository.delete(menor)`. Hay FKs que no se limpian y **hacen fallar el DELETE con
un 500** en cuanto el menor tuvo actividad real:

- `reservas.reservas` (`beneficiario_id`), `reservas.solicitudes_sesion` (`menor_id`)
- `seguridad.denuncias`, `seguridad.sanciones`, `reputacion.calificaciones` (`autor_id`)
- `aula.alertas_seguridad` (`detectado_id`, si el menor fue el detectado)

Verificá la lista real con:
```sql
SELECT conrelid::regclass, conname FROM pg_constraint
WHERE contype = 'f' AND confrelid = 'identidad.usuarios'::regclass;
```
El test actual (`UsuarioServiceDarDeBajaTest`) es unitario con mocks: nunca ejecutó el DELETE
contra el esquema real, y por eso el bug sobrevivió.

## 2. Decisión (D7) y fundamento

La fila del menor **sobrevive**; los datos personales **se borran**. Con transacciones de
MercadoPago y un posible historial de seguridad de por medio, borrar la evidencia no es
defendible: la Ley 25.326 da derecho de supresión con excepciones cuando la retención es
legalmente exigible. La anonimización conserva la integridad contable y de auditoría y cumple la
supresión de los datos identificatorios.

## 3. Archivos

- **Nuevo:** `docs/adr/ADR-M1-04.md` — **escribilo primero**, antes del código (AGENTS §2:
  desviación del comportamiento documentado en FR-ID-014).
- **Nuevo:** `V<siguiente>__m1_estado_cuenta_baja.sql`
- `identidad/model/EstadoCuenta.java`, `identidad/service/UsuarioService.java`
- `identidad/repository/UsuarioRepository.java` (filtro de `listarMenores`)
- Test de integración nuevo (Testcontainers) en `backend/src/test/java/com/tinku/identidad/`.

## 4. Pasos

1. **ADR-M1-04:** decisión, fundamento legal (arriba), qué se borra, qué se conserva y por qué,
   y el riesgo aceptado: el `id` del menor sigue vinculado a sus reservas y a su Adulto Responsable.
2. **Migración:** el CHECK de `estado_cuenta` de V2 es anónimo (esperado
   `usuarios_estado_cuenta_check`; verificalo con `pg_constraint` como en FASE2-10). La migración
   lo reemplaza por uno con nombre que admite `'ACTIVA', 'SUSPENDIDA', 'BAJA'`. **No editar V2.**
3. `EstadoCuenta`: agregar `BAJA`.
4. **`darDeBajaMenor`** conserva todas sus validaciones actuales (pertenencia, FR-ID-020 y
   reservas futuras con confirmación) y reemplaza el `delete` por:
   - `dni` → `"BAJA-" + <primeros 14 caracteres del UUID sin guiones>`, que es **determinístico y
     único** y respeta `VARCHAR(20) UNIQUE` de V2. Nunca un valor fijo ni un random.
   - `nombre` → `"Perfil"`, `apellido` → `"dado de baja"`, `email` → `null`.
   - `fechaNacimiento`: **revisá los CHECK de la tabla antes de elegir el valor.** Si la columna es
     `NOT NULL` y hay un CHECK de edad, usá un valor fijo documentado en el ADR que lo cumpla. Si no
     hay CHECK, el valor fijo igual se documenta.
   - `passwordHash` → el hash de un secreto aleatorio de 32 bytes que se descarta (así ningún
     password puede matchear).
   - `estadoCuenta` → `BAJA`, `activoParaMatching` → `false`.
   - Se siguen borrando `autorizaciones_tutor` y `consentimientos_menor` (ya se hace).
   - Las reservas **futuras** confirmadas (la baja se permite con `confirmarBaja`): cancelalas por
     el camino normal de `ReservaService` si existe un método de cancelación por sistema; si no
     existe, **PARAR y reportar**: no inventes una cancelación que saltee el reembolso de M5.
5. **`listarMenores`** no devuelve menores en `BAJA` (derived query con `EstadoCuentaNot` o
   `@Query`). Revisá con `rg -n "findByAdultoResponsable" backend/src/main` que no haya otro
   listado de menores que los muestre.
6. El login ya rechaza todo lo que no es `ACTIVA` (`UsuarioDetailsService`): no hace falta tocarlo.
   Solo cubrilo con un test.

## 5. Tests obligatorios (de INTEGRACIÓN, con Testcontainers)

1. `bajaDeMenorConReservaFinalizada_noFallaYAnonimiza` — menor con una Reserva `FINALIZADA` y una
   Transacción → `darDeBajaMenor` responde 2xx (vía HTTP, el endpoint real del Adulto Responsable),
   la Reserva sigue existiendo apuntando al mismo `beneficiario_id`, y el usuario tiene los campos
   anonimizados. **Antes del fix, esto da 500 por la FK: ese es el RED.**
2. `menorDadoDeBaja_noPuedeLoguear` — login con el DNI original → 401.
3. `menorDadoDeBaja_noApareceEnListarMenores`.
4. `bajaDeMenor_dniAnonimoEsUnico` — dos bajas seguidas no violan el UNIQUE de V2.
5. Regresión: `UsuarioServiceDarDeBajaTest` (unit) se ajusta al nuevo contrato (no más `delete`).
   Justificalo en el commit: el test codificaba el comportamiento que el finding declara roto.

## 6. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-017 → `CERRADO`.
- `Spec_M1` FR-ID-014: nota de que la baja es por anonimización, con referencia a ADR-M1-04.
- Tasks: T-AUD-016 tildada en los dos archivos.

## 7. NO tocar

- La baja de adultos (fuera de alcance: no hay FR que la defina).
- Los datos de las Reservas, Transacciones, Sanciones y Calificaciones: son registros contables y
  de seguridad, se conservan tal cual.
