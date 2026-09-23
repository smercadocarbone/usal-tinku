# FASE3-03 — JWT con UUID como subject e invalidación al cambiar la contraseña (AUD-027 + limpieza de AUD-003)

**Branch:** sub-branch `aud/fase3-jwt` → `aud/fase3-p2-calidad` · **Riesgo: ALTO** (toca la
autenticación de todo el sistema) · **Findings:** AUD-027, limpieza pendiente de AUD-003 (Task 3.12
y 4.5 del plan viejo, que son la misma).

## 1. Problema (verificado al 2026-09-22)

1. **El DNI es el `sub` del JWT.** `config/security/JwtUtil.generateToken(dni, …)` hace
   `.subject(dni)`. El JWT viaja en `localStorage` y en una cookie legible por JS: cualquiera que
   decodifique el token (base64, sin secreto) lee el DNI. Con menores, es dato sensible (Ley 25.326).
2. **Cambiar o resetear la contraseña no invalida las sesiones abiertas.**
   `identidad/service/PasswordResetService.resetearPassword` y `UsuarioService.cambiarPassword` solo
   cambian el hash: un token robado sigue valiendo hasta que expire.
3. **Limpieza de AUD-003:** `aula/LiveKitWebhookService.mismaPersona` todavía acepta
   `usuario.getDni().equals(identity)` para tokens de LiveKit emitidos antes de FASE 1. Esos tokens
   tienen un TTL de 1h (`tinku.livekit.token-ttl-segundos`) y FASE 1 se deployó el 2026-09-22: la
   rama ya no tiene uso legítimo.

**Quién depende hoy del DNI como subject** (verificalo con `rg`, puede haber más):
- `config/security/JwtAuthenticationFilter`: `jwtUtil.extractDni(token)` →
  `userDetailsService.loadUserByUsername(dni)`.
- `config/security/UsuarioDetailsService.loadUserByUsername`.
- `shared/UsuarioActual.obtener`: `usuarioRepository.findByDni(authentication.getName())`.
- `AdminModeracionGate` (en `shared/` o en `admin/` si FASE3-01 ya lo movió): resuelve el Admin
  cruzando por el DNI del principal.
- 6 archivos de test que acuñan tokens con `jwtUtil.generateToken(u.getDni(), …)`.

## 2. Decisiones

- `sub` = **UUID del usuario**. El **login sigue siendo por DNI** (es la credencial, no cambia);
  lo que cambia es el contenido del token.
- **`credentials_version`:** columna `INT NOT NULL DEFAULT 0` en `identidad.usuarios`. El token lleva
  el claim `cv`. En cada request, si `cv` ≠ la versión actual del usuario → **401**. Se incrementa al
  **resetear** y al **cambiar** la contraseña.
- **Migración de sesiones:** los tokens emitidos antes del deploy (con `sub` = DNI) dejan de ser
  válidos → **todos los usuarios tienen que volver a loguearse una vez**. Es aceptable y más simple
  que aceptar los dos formatos. Documentalo en el commit y en el README.

## 3. Pasos (commits chicos)

1. **Migración** `V<n>__m1_credentials_version.sql` + campo en `Usuario`.
2. **Test RED (a):** `tokenEmitido_noContieneElDni` — decodificá el payload del token del login y
   verificá que no aparezca el DNI en ningún claim. Hoy falla.
3. **Test RED (b):** `resetearPassword_invalidaLosTokensAnteriores` — login → token T1 → reset →
   request autenticado con T1 → **401**. Hoy da 200. Ídem `cambiarPassword_…`.
4. **`JwtUtil`:** `generateToken(Usuario usuario)` (UUID, tipo, capacidades, `cv`);
   `extractDni` → `extractUsuarioId`. El filtro carga el usuario **por id** y compara `cv`.
5. **`UsuarioDetailsService`:** carga por id (el `username` del `UserDetails` pasa a ser el UUID en
   string). **Mantené** el rechazo de cuentas que no están `ACTIVA`.
6. **`UsuarioActual`:** `findById(UUID.fromString(authentication.getName()))`.
7. **`AdminModeracionGate`:** resolver el Admin por el id del usuario, no por DNI.
8. **Login** (`AuthService` / `AuthController`): sigue autenticando por DNI + password; al emitir el
   token usa el `Usuario` ya cargado.
9. **Reset y cambio de contraseña:** `credentialsVersion++` en la misma transacción que el nuevo hash.
10. **Tests existentes:** los 6 archivos que acuñan tokens pasan a `jwtUtil.generateToken(usuario)`.
    Es un cambio de firma, no un cambio de comportamiento esperado: justificalo en el commit (A7).
11. **LiveKit (AUD-003):** borrar la rama `|| usuario.getDni().equals(identity)` de
    `mismaPersona` y su comentario. Test: webhook con identity = DNI → **no** registra el join.
12. **Frontend — el `sub` SÍ se usa hoy (verificado):**
    - `frontend/src/app/cuenta/page.tsx` muestra `payload?.sub` como si fuera el DNI. Con el cambio
      mostraría un UUID: reemplazalo por un dato del perfil (`GET` del usuario actual) o sacalo. **No
      muestres el DNI completo** si no hace falta (minimización): enmascaralo (`••••1234`).
    - `frontend/src/app/cuenta/horarios/page.tsx` usa `String(session?.payload.sub)` como
      **`tutorId`**. Hoy le pasa un **DNI** donde el backend espera un UUID: verificá si esa pantalla
      está rota hoy (probablemente sí) y dejá asentado en el commit que este cambio la arregla.
    - `frontend/src/lib/auth.ts` (`PayloadSesion`): actualizá el tipo y el comentario del `sub`.

## 4. Tests obligatorios

Los dos RED de arriba, más:
- `tokenConCredentialsVersionVieja_401`.
- `tokenDeCuentaSuspendida_401` (regresión).
- `adminGate_resuelvePorId` (el panel de admin sigue funcionando: un test de integración que ya
  exista en `AdminPanelIntegracionTest` cubre esto si pasa verde).
- `webhookLiveKit_identityDni_noRegistraJoin` (limpieza de AUD-003).

## 5. Criterios de aceptación

- Suite verde en cada commit. `REGISTRO_FINDINGS.md`: AUD-027 → `CERRADO`; la fila de AUD-003 suma
  el commit de la limpieza.
- `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`: marcar las tareas 3.6, 3.12 y 4.5
  como hechas por esta spec.
- README: nota de que tras este deploy todos vuelven a loguearse una vez.
- `T-AUD-023` (ADR diferido del "DNI como `sub`"): ya no hace falta, porque la decisión se revirtió.
  Anotalo en `Tasks_Tinku_Implementacion.md` y `Tasks_Tinku_Chunks.md`.

## 6. NO tocar

- El JWT en `localStorage`: es deuda aceptada en ADR-000-04, fuera de alcance.
- El algoritmo ni el secreto del JWT.
