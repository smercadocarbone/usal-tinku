# FASE3-05 — Una calificación pública por sesión: califica solo el pagador (AUD-028)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** bajo · **Finding:** AUD-028 · **Decisión:** D9.

## 1. Problema (verificado al 2026-09-22)

`reputacion/service/CalificacionService.derivarDireccion(reserva, autor)` asigna
`DIR_ESTUDIANTE_A_TUTOR` si el autor es el **beneficiario o el pagador**. En una sesión con menor
(el Adulto Responsable paga, el menor recibe la clase) **califican los dos**: la sesión pesa el
doble en el promedio del Tutor, y el umbral de 5 calificaciones públicas (FR-REP-007) se alcanza con
la mitad de sesiones. El promedio deja de ser comparable entre Tutores.

## 2. Decisión (D9)

Califica **solo el pagador**. Para un Estudiante adulto que reserva para sí mismo
(`pagador == beneficiario`) no cambia nada. Es coherente con el Artículo II: el menor no paga, no
autoriza Tutores ni denuncia, y tampoco califica; lo hace su Adulto Responsable.
`tutor_a_estudiante` (la calificación oculta para moderación) **no cambia**.

## 3. Pasos

1. **Test RED:** `beneficiarioMenorQueNoEsPagador_noPuedeCalificar_403` (hoy da 2xx) y regresiones
   `pagadorAdultoResponsable_califica_201` y `estudianteAdultoQueEsPagadorYBeneficiario_califica_201`.
2. `derivarDireccion`: la rama `DIR_ESTUDIANTE_A_TUTOR` solo si
   `reserva.getPagador().getId().equals(id)`. El beneficiario que no es pagador cae en
   `CalificacionNoPermitidaException` (ya mapeada a 403).
3. **Frontend:** `frontend/src/app/cuenta/reservas/[id]/page.tsx` y
   `frontend/src/components/FormularioCalificacion.tsx`: no ofrecer calificar a quien no es el
   pagador (un menor logueado no ve el formulario). Verificá qué dato del usuario/reserva tiene la
   pantalla para decidirlo; si no lo tiene, que el backend lo exponga en la respuesta de la reserva
   (`puedeCalificar: boolean`) en vez de replicar la regla en el cliente.
4. **Datos existentes:** si hay calificaciones duplicadas (beneficiario menor + pagador en la misma
   sesión), **no las borres**: reportá cuántas hay (`SELECT …`). Recalcular promedios es una decisión
   del usuario.

## 4. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-028 → `CERRADO`.
- `Spec_M7`: escribir quién califica cuando pagador ≠ beneficiario (hoy el Spec no lo dice).
