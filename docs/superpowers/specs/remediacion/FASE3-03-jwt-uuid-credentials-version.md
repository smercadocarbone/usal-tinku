# FASE3-03 — JWT con UUID como subject e invalidación al cambiar la contraseña (AUD-027 + limpieza de AUD-003)

**Branch:** sub-branch `aud/fase3-jwt` (desde `aud/fase3-p2-calidad`, o desde `main` si se adelanta
antes del piloto) · **Riesgo: ALTO** (toca la autenticación de TODO el sistema) ·
**Findings:** AUD-027 y la limpieza pendiente de AUD-003 (Tasks 3.6, 3.12 y 4.5 del plan viejo).

> **Cómo ejecutar esta spec.** Es una **receta**: pasos **en orden**, **un commit por paso**, y el
> **punto de control** de cada paso antes de seguir. Si un punto de control falla, **PARAR**. Los
> nombres entre comillas invertidas fueron verificados contra el código al 2026-09-23.
> Suite (desde `backend/`):
> `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD"`

---

## 0. Qué problema se resuelve

1. **El DNI viaja en el JWT.** `config/security/JwtUtil.generateToken(dni, tipo, capEst, capAr)` hace
   `.subject(dni)`. El token vive en `localStorage` y en la cookie `tinku_jwt`, y **cualquiera lo
   decodifica sin secreto** (es base64). Con menores, es un dato sensible (Ley 25.326).
2. **Cambiar o resetear la contraseña no cierra las sesiones abiertas.** Un token robado sigue valiendo
   hasta que expira (`tinku.jwt.expiration-minutes`).
3. **Limpieza de AUD-003:** `aula/LiveKitWebhookService.mismaPersona` todavía acepta el DNI como
   identity de LiveKit (compatibilidad con tokens previos a FASE 1, TTL 1 h, ya vencidos).

## 1. Decisiones (no volver a discutir)

- `sub` = **UUID del usuario** (`usuario.getId().toString()`). El **login sigue siendo por DNI**: es la
  credencial, no el contenido del token.
- **`credentials_version`** (`INT NOT NULL DEFAULT 0` en `identidad.usuarios`). El token lleva el claim
  **`cv`**. En cada request, si falta `cv` o no coincide con la versión actual → la request queda **no
  autenticada**. **Ojo: hoy el backend responde 403 (no 401) a toda request sin sesión válida**
  (verificado: `GET /api/usuarios/me` sin token → 403), porque `SecurityConfig` no configura un
  `authenticationEntryPoint`. Esta tarea **no** cambia eso: los tests esperan **403**. Se incrementa al **resetear** y al **cambiar** la contraseña.
- **Todos los usuarios vuelven a loguearse una vez** tras el deploy (los tokens viejos no tienen `cv`
  y su `sub` es un DNI). Es aceptable y es lo más simple. Va en el README y en el commit.

## 2. ⚠️ Mapa completo de quién depende del DNI como principal (verificado)

`authentication.getName()` hoy devuelve el **DNI**, porque el filtro arma el `UserDetails` con
`username = dni`. Después de esta tarea va a devolver el **UUID en texto**. **Todos** estos lugares
cambian; si te olvidás uno, esa parte del sistema deja de funcionar:

| # | Dónde | Qué hace hoy | Qué hace después |
|---|---|---|---|
| 1 | `config/security/JwtUtil` | `.subject(dni)`, `extractDni(token)` | `.subject(uuid)`, claim `cv`, `extractUsuarioId(token)` |
| 2 | `config/security/JwtAuthenticationFilter.doFilterInternal` | `loadUserByUsername(dni)` | carga por id y compara `cv` |
| 3 | `identidad/service/UsuarioDetailsService` | `loadUserByUsername(dni)` → `User(dni, …)` | método nuevo por id → `User(uuid, …)` |
| 4 | `shared/UsuarioActual.obtener` | `findByDni(authentication.getName())` | `findById(UUID.fromString(getName()))` |
| 5 | `shared/AdminModeracionGate.requiereAdmin` y `adminAutenticado` | `findByUsuario_Dni…(getName())` | `findByUsuario_Id…(UUID)` |
| 6 | `config/security/AdminActivoAuthorizationManager` (línea con `auth.getName()`) | trata `getName()` como DNI | como UUID |
| 7 | `admin/web/TicketsController` (usa `authentication.getName()` + `findByDni`) | DNI | usar `UsuarioActual.obtener(authentication)` |
| 8 | `identidad/service/AuthService.login` | `generateToken(usuario.getDni(), …)` | `generateToken(usuario)` |
| 9 | `admin/repository/AdminRepository` | `findByUsuario_DniAndRolAndActivoTrue`, `findByUsuario_DniAndActivoTrue` | agregar `findByUsuario_IdAndRolAndActivoTrue`, `findByUsuario_IdAndActivoTrue` |
| 10 | Tests: 6 usos de `jwtUtil.generateToken(...)` en 6 archivos (`ResumenControllerIntegracionTest`, `PasarelaBypassIntegracionTest`, `AdminPanelIntegracionTest`, `E2ERamaSeguridadIntegracionTest`, `DenunciasModeracionIntegracionTest`, `KillswitchIntegracionTest`) | `generateToken(u.getDni(), tipo, …)` | `generateToken(u)` |
| 11 | Frontend `app/cuenta/page.tsx` | muestra `payload?.sub` como si fuera el DNI | mostrar nombre y email desde `GET /api/usuarios/me` (que no devuelve el DNI, a propósito) |
| 12 | Frontend `app/cuenta/horarios/page.tsx` | `tutorId = session.payload.sub` (**hoy es un DNI → la agenda da 403**) | con el cambio, el `sub` es el UUID: **queda arreglado** |

**Antes de empezar**, confirmá que la lista está completa:
```bash
rg -n "getName\(\)" backend/src/main/java -g '*.java' | rg -v "\.name\(\)|getTriggerKey|TriggerKey"
rg -n "extractDni|findByDni\(|generateToken\(" backend/src -g '*.java'
rg -n "payload\??\.sub" frontend/src
```
Si aparece un uso que no está en la tabla, **agregalo a tu plan y reportalo**. `findByDni` en
`AuthService.login`, `PasswordResetService`, `OcrBackoffService` y `AdminSeedRunner` **se quedan**:
buscan por DNI porque el DNI es lo que ingresa el usuario o lo que dice la config, no el principal.

## 3. Pasos

### Paso 0 — Preparación (sin commit)
`docker info`, último número de migración (`fd -e sql . backend/src/main/resources/db/migration | sort | tail -1`),
suite completa y **anotá N**.

### Paso 1 — Columna `credentials_version`
- `V<n>__m1_credentials_version.sql`:
  ```sql
  -- AUD-027: versión de credenciales. Cambia al resetear/cambiar la contraseña y deja inválidos
  -- los JWT emitidos antes (claim cv).
  ALTER TABLE identidad.usuarios ADD COLUMN credentials_version INT NOT NULL DEFAULT 0;
  ```
- `identidad/model/Usuario.java`: `@Column(name = "credentials_version", nullable = false) private int credentialsVersion = 0;`
  con getter y un método `public void invalidarCredenciales() { this.credentialsVersion++; }`.
- **Punto de control 1:** suite verde, N igual. **Commit:** `feat(identidad): credentials_version en usuarios (AUD-027, paso 1)`.

### Paso 2 — Tests RED (sin commit propio: van en el commit del Paso 3)
Escribí estos tests **y corrélos para ver el rojo** (pegá la salida en tu reporte), pero no los
commitees solos: van en el commit del Paso 3.

En `backend/src/test/java/com/tinku/identidad/IdentidadFlujosIntegracionTest.java` (ya tiene
`registrarAdultoYToken`, `login`, `mockMvc`):
```java
@Test
void aud027_tokenEmitido_noContieneElDni() throws Exception {
    String dni = "55556601";
    String token = registrarAdultoYToken(dni, "Ana", "Test", true, false);
    String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(payload).doesNotContain(dni);
}

@Test
void aud027_cambiarPassword_invalidaElTokenAnterior() throws Exception {
    String token = registrarAdultoYToken("55556602", "Ana", "Test", true, false);
    mockMvc.perform(patch("/api/usuarios/me/password")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"passwordActual\":\"" + PASSWORD + "\",\"passwordNueva\":\"OtraClave123!\"}"))
            .andExpect(status().isNoContent());
    mockMvc.perform(get("/api/usuarios/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden()); // sin sesión válida la app responde 403 (ver §1)
}
```
(Ajustá la firma de `registrarAdultoYToken` y el nombre de la constante de password a los que tiene
el archivo. El endpoint de cambio es `PATCH /api/usuarios/me/password` con
`{passwordActual, passwordNueva}` (`UsuarioController.cambiarPassword`, responde 204).) Sumá el
equivalente para el **reset** (`resetearPassword_invalidaElTokenAnterior`): el token de reset en claro
no se loguea desde AUD-008, así que en el test tomalo **mockeando** `NotificadorResetPassword` (el
archivo ya lo tiene como `@MockBean`) y capturando el argumento `tokenPlano` con un `ArgumentCaptor`.

**Rojo esperado:** el primero falla porque el payload contiene el DNI; los otros porque la request
con el token viejo responde 200 (y tiene que responder 403).

### Paso 3 — JWT nuevo, filtro, principal (el corazón del cambio)
Hacé **todo** este paso junto: por separado deja el sistema inconsistente.

1. **`JwtUtil`**:
   ```java
   public String generateToken(Usuario usuario) {
       Instant now = Instant.now();
       return Jwts.builder()
               .subject(usuario.getId().toString())
               .claim("tipo", usuario.getTipo().name())
               .claim("cap_est", usuario.isCapacidadEstudiante())
               .claim("cap_ar", usuario.isCapacidadAdultoResponsable())
               .claim("cv", usuario.getCredentialsVersion())
               .issuedAt(Date.from(now))
               .expiration(Date.from(now.plusMillis(expirationMillis)))
               .signWith(signingKey)
               .compact();
   }
   public UUID extractUsuarioId(String token) { return UUID.fromString(validateToken(token).getSubject()); }
   public Integer extractCredentialsVersion(String token) { return validateToken(token).get("cv", Integer.class); }
   ```
   Borrá `generateToken(String dni, …)` y `extractDni`: si queda alguien llamándolos, que no compile.
   `JwtUtil` pasa a importar `com.tinku.identidad.model.Usuario` (config ya depende de identidad por
   `UsuarioDetailsService`; no es un ciclo nuevo).
2. **`UsuarioDetailsService`**: agregá
   ```java
   /** Para el filtro JWT: carga por id, exige cuenta ACTIVA y la versión de credenciales del token. */
   public UserDetails cargarParaToken(UUID usuarioId, Integer cvDelToken) throws UsernameNotFoundException {
       Usuario u = usuarioRepository.findById(usuarioId)
               .orElseThrow(() -> new UsernameNotFoundException("Usuario del token inexistente"));
       if (u.getEstadoCuenta() != EstadoCuenta.ACTIVA) throw new UsernameNotFoundException("Cuenta inactiva");
       if (cvDelToken == null || cvDelToken != u.getCredentialsVersion())
           throw new UsernameNotFoundException("Token emitido antes de un cambio de credenciales");
       return new User(u.getId().toString(), u.getPasswordHash(),
               List.of(new SimpleGrantedAuthority("ROLE_" + u.getTipo().name())));
   }
   ```
   Dejá `loadUserByUsername(String dni)` como está (lo exige la interfaz `UserDetailsService`). **No
   metas el DNI en ningún mensaje de excepción nuevo.**
3. **`JwtAuthenticationFilter`**: reemplazá `jwtUtil.extractDni(token)` + `loadUserByUsername(dni)` por
   `userDetailsService.cargarParaToken(jwtUtil.extractUsuarioId(token), jwtUtil.extractCredentialsVersion(token))`.
   Hoy el filtro recibe la **interfaz** `UserDetailsService` por constructor
   (`JwtAuthenticationFilter(JwtUtil, UserDetailsService)`). Cambiá el tipo del campo **y** del parámetro
   del constructor a `UsuarioDetailsService` (la clase concreta) para ver el método nuevo. El filtro es
   un bean de Spring y `SecurityConfig` lo recibe ya construido por inyección, así que **no** hay que
   tocar `SecurityConfig` por esto.
   **Conservá** el `catch (UsernameNotFoundException e)` que deja la request como no autenticada, y
   sumá `IllegalArgumentException` al catch (un `sub` viejo con DNI no es un UUID válido y
   `UUID.fromString` la tira): así los tokens viejos dan **403** (sin sesión) y no **500**.
4. **`UsuarioActual.obtener`**: `usuarioRepository.findById(UUID.fromString(authentication.getName()))`.
5. **`AdminRepository`**: agregá `Optional<Admin> findByUsuario_IdAndRolAndActivoTrue(UUID usuarioId, RolAdmin rol);`
   y `Optional<Admin> findByUsuario_IdAndActivoTrue(UUID usuarioId);`. Borrá los de DNI si quedan sin uso.
6. **`AdminModeracionGate`** (en `shared/`, o en `admin/` si FASE3-01 ya lo movió): `requiereAdmin` y
   `adminAutenticado` convierten `authentication.getName()` a `UUID` (si es `null` o no parsea →
   `AccesoModeracionDenegadoException`, igual que hoy con un DNI inexistente).
7. **`AdminActivoAuthorizationManager`**: mismo cambio (el nombre es un UUID).
8. **`TicketsController`**: reemplazá `authentication.getName()` + `usuarioRepository.findByDni(dni)` por
   `usuarioActual.obtener(authentication)`.
9. **`AuthService.login`**: `jwtUtil.generateToken(usuario)`.
10. **Tests (fila 10 de la tabla):** los 6 usos pasan a `jwtUtil.generateToken(u)`. Es un **cambio de
    firma**, no de comportamiento esperado: justificalo en el commit (A7).

**Punto de control 3:** los 3 tests del Paso 2 **pasan**, y la suite completa queda verde. Si fallan
tests de admin, casi seguro te faltó el ítem 5, 6 o 7.
**Commit:** `fix(auth): el JWT lleva el UUID y no el DNI, y no sobrevive a un cambio de contraseña (AUD-027)`.

### Paso 4 — Invalidar al cambiar o resetear la contraseña
- `identidad/service/PasswordResetService.resetearPassword`: después de `usuario.setPasswordHash(...)`,
  `usuario.invalidarCredenciales();` (misma transacción, antes del `save`).
- `identidad/service/UsuarioService.cambiarPassword`: ídem.
- Si hay un "cerrar sesión en todos los dispositivos" en el alcance futuro, este es el mecanismo: no lo
  agregues ahora (A10).

**Punto de control 4:** los tests de cambio y reset de contraseña pasan; suite verde.
**Commit:** `fix(identidad): cambiar o resetear la contrasenia invalida las sesiones abiertas (AUD-027)`.

(Si el Paso 3 ya hizo pasar los tests del Paso 2 porque sumaste esto ahí, está bien: juntá 3 y 4 en un
solo commit y decilo.)

### Paso 5 — Limpieza de AUD-003 (LiveKit)
- `aula/LiveKitWebhookService.mismaPersona`: borrá `|| usuario.getDni().equals(identity)` y el
  comentario de compatibilidad que está encima.
- **Test RED** en `LiveKitWebhookIntegracionTest`: webhook `participant_joined` firmado con
  `identity` = DNI del tutor → **no** registra el join (`tutorJoinedAt` sigue `null`).
**Commit:** `refactor(aula): LiveKit ya no acepta el DNI como identity (limpieza de AUD-003)`.

### Paso 6 — Frontend
- `frontend/src/lib/auth.ts`: en `PayloadSesion`, documentá que `sub` es el **id del usuario**
  (UUID), no el DNI. Sumá `cv?: number`.
- `frontend/src/app/cuenta/page.tsx`: dejá de mostrar `payload?.sub`. Mostrá **nombre, apellido y email**
  desde `GET /api/usuarios/me` (`UsuarioResponse`: `id, nombre, apellido, tipo, capacidadEstudiante,
  capacidadAdultoResponsable, email`). `UsuarioResponse` **no** devuelve el DNI a propósito
  (minimización): **no lo agregues**.
- `frontend/src/app/cuenta/horarios/page.tsx`: no hace falta cambiar código (el `sub` ya es el UUID),
  pero **verificá a mano** que la agenda del tutor carga sus franjas (hoy da 403).
- Validación: `cd frontend && bun run lint && npx tsc --noEmit -p .`.
**Commit:** `fix(frontend): el sub del token es el id del usuario, nunca el DNI`.

### Paso 7 — Documentación (commit propio)
- `REGISTRO_FINDINGS.md`: AUD-027 → `CERRADO`; AUD-003 suma el commit del Paso 5.
- `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`: marcar 3.6, 3.12 y 4.5 como hechas.
- `Tasks_Tinku_Implementacion.md` **y** `Tasks_Tinku_Chunks.md`: T-AUD-023 (el ADR diferido del "DNI
  como `sub`") ya no hace falta porque la decisión se revirtió; anotalo así.
- README: "Tras este deploy, todos los usuarios vuelven a iniciar sesión una vez."
**Commit:** `docs: FASE3-03 cerrada (AUD-027, limpieza de AUD-003)`.

## 4. Verificación manual final (con el stack local)
1. `docker compose up -d --build --no-deps backend`.
2. Loguearse en el frontend como tutor (`30224455` / `Password123!`): la agenda **carga** sus franjas.
3. Copiar el token de `localStorage` y decodificar el payload (`echo <parte2> | base64 -d`): **no**
   aparece el DNI.
4. Cambiar la contraseña desde la cuenta y recargar otra pestaña con la sesión vieja → vuelve al login.

## 5. NO tocar
- El JWT en `localStorage` (deuda aceptada en ADR-000-04).
- El algoritmo, el secreto y la expiración del JWT.
- `findByDni` en login, reset de contraseña, OCR y el seed de admins (§2).
