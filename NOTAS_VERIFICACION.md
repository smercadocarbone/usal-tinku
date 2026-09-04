# NOTAS_VERIFICACION — branch `chunk/m1-d`

Chunk M1-D: **T-M1-08** (capacidades combinables) + **T-M1-09** (registro Tutor).
Este branch NACE de `chunk/m1-c` (depende de su `OcrBackoffService` FR-ID-011 y
de la migración V4). NO fue mergeado a main y NO marca ningún chunk/tarea como
cerrado.

## Qué quedó implementado

- **T-M1-08 — `PATCH /api/usuarios/me/capacidades`** (autenticado):
  - `ActualizarCapacidadesRequest` (`capacidadEstudiante`, `capacidadAdultoResponsable`).
  - `UsuarioService.actualizarCapacidades(...)`: FR-ID-015 (activación inmediata,
    sin re-OCR), FR-ID-016 (no desactivar Adulto Responsable con menores a cargo
    → 409), FR-ID-001 (nunca quedar sin capacidades → 422), y Artículo II (un
    menor no puede ser Adulto Responsable → 422).
  - `NoPuedeDesactivarAdultoResponsableException` + handler (409).
  - Helper `usuarioActual(Authentication)` en `UsuarioController`.
- **T-M1-09 — `POST /api/tutores/registro`** (público, autorregistro FR-ID-007):
  - `RegistroTutorRequest`.
  - `TutorController` nuevo.
  - `UsuarioService.registrarTutor(...)`: mismo OCR que adulto, edad ≥ 18 sin
    excepciones (FR-ID-007), unicidad de DNI, backoff FR-ID-011.
  - `SecurityConfig`: `/api/tutores/registro` agregado a `permitAll` (el alta de
    menor NO es público).

## Unit tests corridos en esta sesión (verdes, sin Docker/Tesseract)

- `UsuarioServiceTutorCapacidadesUnitTest` (10): alta Tutor exitosa, Tutor menor
  (rechazo), documento ilegible (consume backoff), nombre no coincide, DNI
  duplicado, activación inmediata sin re-OCR, quedar sin capacidades, desactivar
  AR con menores, desactivar AR sin menores, menor→AR (rechazo).
- Regresión: `UsuarioServiceRegistroUnitTest` (9), `OcrBackoffServiceTest` (6),
  `DniParserTest` (7), `PreprocesadorImagenTest` (6). Total 38 verdes.
- TODOS unitarios con mocks; ninguno levanta Spring ni requiere Docker.

## Qué falta correr/confirmar en un entorno con Docker + Tesseract

1. Migraciones Flyway aplican (V2, V3, V4) y `ddl-auto:validate` pasa.
2. `@SpringBootTest` Testcontainers (no corrieron): confirmar que el contexto
   completo autowirea los controllers nuevos (`TutorController`) y el
   `UsuarioController` ampliado.
3. Seguridad: verificar por HTTP real que `/api/usuarios/menores` y
   `/api/usuarios/me/capacidades` REQUIEREN token (401 sin token) y que
   `/api/tutores/registro` es `permitAll`.
4. E2E de alta de Tutor con DNI real (Tesseract): rama de edad y coincidencia.
5. Capacidades: no hay test de integración de US-1bis todavía (va en M1-G,
   T-M1-13).

## Nota de seguridad (Artículo II)

Se añadió una salvaguarda en `actualizarCapacidades` para que un perfil MENOR no
pueda activar `capacidadAdultoResponsable`. La restricción a nivel de permisos
del menor (no pagar/autorizar Tutores/denunciar) se cierra en los módulos que
consumen esas acciones (M4/M9).
