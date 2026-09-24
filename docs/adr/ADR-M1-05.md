# ADR-M1-05 — Baja de menor por anonimización (D7), no por DELETE

## Estado
Aceptado, 2026-09-24. Cierra el hallazgo AUD-017 de la auditoría independiente
(2026-09-21). Documenta la decisión D7 ya acordada en el plan de remediación y
formaliza una desviación del comportamiento documentado en FR-ID-014 (que decía
"se elimina el perfil"): la supresión es por anonimización.

## Contexto
`UsuarioService.darDeBajaMenor` borraba `autorizaciones_tutor` y
`consentimientos_menor` y después hacía `usuarioRepository.delete(menor)`. En
cuanto un menor tenía actividad real, el DELETE fallaba con un 500 por las FKs
que apuntan a `identidad.usuarios` y no se limpian: `reservas.reservas`
(`beneficiario_id`), `reservas.solicitudes_sesion` (`menor_id`),
`seguridad.denuncias`, `seguridad.sanciones`, `reputacion.calificaciones`
(`autor_id`), `aula.alertas_seguridad` (`detectado_id`). El test unitario
existente (Mockito) nunca ejecutó el DELETE contra el esquema real, y por eso el
bug sobrevivió.

Borrar la evidencia no es defendible: con transacciones de MercadoPago y un
posible historial de seguridad de por medio, la integridad contable y de
auditoría exige conservar esos registros. La Ley 25.326 (Arts. 26, 27, 29)
regula el derecho de supresión con excepciones cuando la retención es
legalmente exigible — acá lo es.

## Decisión
**La fila del menor sobrevive; los datos personales se borran.** La baja
reemplaza el `delete` por una anonimización determinística:

Se borra:
- `dni` → `'BAJA-' + <primeros 14 caracteres del UUID sin guiones>` (5 + 14 = 19
  caracteres, respeta `VARCHAR(20) UNIQUE` de V2). Determinístico y único por
  menor — nunca un valor fijo ni un random. El DNI original queda irrecuperable.
- `nombre` → `'Perfil'`, `apellido` → `'dado de baja'`, `email` → `null`.
- `fecha_nacimiento` → `'1900-01-01'`. La tabla no tiene CHECK de edad sobre esta
  columna (solo `NOT NULL` en V2), así que el valor fijo es válido; se documenta
  acá igual (spec FASE2-06 §4).
- `password_hash` → hash de un secreto aleatorio de 32 bytes que se descarta
  dentro del mismo método: ningún password puede volver a matchear.
- `estado_cuenta` → `BAJA`, `activo_para_matching` → `false` (el login ya rechaza
  todo lo que no es `ACTIVA`). `tipo` y `adulto_responsable_id` se conservan para
  que las consultas de pertenencia sigan valiendo.
- Se siguen borrando `autorizaciones_tutor` y `consentimientos_menor` del menor
  (vínculos de confianza ya operativos; son los que tenían FK limpia).

Se conserva:
- La fila `usuarios` del menor, su `id` y `tipo`.
- Todas las Reservas, Transacciones, Denuncias, Sanciones y Calificaciones que lo
  referencian: son registros contables y de seguridad (spec FASE2-06 §7).

## Riesgo aceptado
- El `id` del menor queda vinculado a sus Reservas y a su Adulto Responsable: es
  el intercambio deliberado por la integridad contable/auditoría. Un `id` UUID de
  tipo 4 no es información personal identificatoria per se.
- Un `dni` anonimizado (`BAJA-…`) podría en teoría ser "reclamado": la columna es
  `UNIQUE`, el valor es determinístico y único, imposible que dos menores lo
  compartan.

## Consecuencias
- **Spec_M1_Identidad_Perfiles.md:** FR-ID-014 pasa de "se elimina el perfil" a
  "se anonimiza" (baja por anonimización, referencia a este ADR).
- **Migración:** V27 reemplaza el CHECK anónimo de `estado_cuenta` de V2
  (`usuarios_estado_cuenta_check`) por uno con nombre que admite `BAJA`. No se
  edita V2.
- **`listarMenores`:** excluye menores en `BAJA` (derived query con
  `EstadoCuentaNot`).
- **AUD-017:** se cierra con el test de integración que falla ANTES del fix
  (500 por FK) y pasa después.