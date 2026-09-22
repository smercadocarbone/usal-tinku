# Registro de Findings — Auditoría 2026-09-21

> Fuente: `docs/auditoria/2026-09-21-auditoria-independiente.md`.
> Un finding solo pasa a `CERRADO` con un commit + un test de regresión.
> Un finding solo pasa a `ACEPTADO` con un ADR que lo justifique.
> Estados válidos: ABIERTO | EN CURSO | CERRADO | ACEPTADO | REFUTADO
> Columna `Fase`: fase del plan de remediación en la que está **agendado** su arreglo — no la
> fase en la que efectivamente se cerró. Un finding puede cerrarse antes de su fase agendada
> (ej. AUD-030 agenda Fase 2 y cerró en FASE 0); eso no cambia el número de esta columna.

| ID | Sev | Título | Estado | Fase | Commit | Test |
|----|-----|--------|--------|------|--------|------|
| AUD-001 | CRÍTICA | El kill-switch no cierra la sala de LiveKit | CERRADO | 1 | c650018 — ADR-M3-03 | `KillswitchIntegracionTest.aud001_*` (4), `LiveKitServiceTest.eliminarSala*` (3) |
| AUD-002 | CRÍTICA | Tokens de LiveKit con roomAdmin y roomCreate | CERRADO | 1 | 92375ee | `LiveKitServiceTest.tokenDeParticipanteSeVerificaYDeclaraLaSalaCorrecta` |
| AUD-003 | CRÍTICA | El DNI se usa como identity de LiveKit y se expone en pantalla | CERRADO | 1 | 049fb8f | `SesionesIntegracionTest.tM3Token_participanteConSalaCreada_obtieneTokenYUrl`, `LiveKitServiceTest.tokenDeParticipanteLlevaElNombreVisibleEnElClaimName` |
| AUD-004 | CRÍTICA | Perfil `dev` con OCR stub activo por defecto en el artefacto | CERRADO | 1 | e55d2d1 | `ArranqueSeguroValidatorTest` (elArtefactoNoTraePerfilActivoHardcodeado, prodConStubOcr, prodYDevJuntosConStubOcr) |
| AUD-005 | CRÍTICA | Kill-switch disparable por cualquier participante sin evidencia | CERRADO | 1 | ba6a761 — ADR-M3-02 (vector financiero cerrado; corte arbitrario aceptado por Art. II) | `EscrowListenersIntegracionTest` (killswitch pausa, alertaResuelta), `DenunciasModeracionIntegracionTest.aud005_*`, `E2ERamaSeguridadIntegracionTest.killswitchMenor_*` |
| AUD-006 | ALTA | `ramaMenor` suspende siempre al Tutor, ignorando al detectado real | CERRADO | 1 | 5834fb1 | `KillswitchIntegracionTest.aud006_menor_detectadoEsElMenor_noSuspendeAlTutorYLaResolucionRevierte` |
| AUD-007 | CRÍTICA | No hay endpoint para ver el archivo de la Credencial Académica | CERRADO | 1 | f97b12e | `AdminPanelIntegracionTest.aud007_*` (3), `AlmacenamientoLocalTest` (5), `IdentidadFlujosIntegracionTest.aud007_*` (2) |
| AUD-008 | CRÍTICA | El token de reset de contraseña se loguea en claro junto al DNI | CERRADO | 1 | 676a6b8 | `NotificadorResetPasswordLogTest.notificarNoLoguearElTokenNiElDni` |
| AUD-009 | ALTA | La constraint de reservas compara igualdad exacta, no solapamiento | ABIERTO | 2 | — | — |
| AUD-010 | ALTA | Sin índice único en `mp_payment_id`/`reserva_id`: escrow duplicable | CERRADO | 1 | b6bd538 | `PagosWebhookIntegracionTest.webhook_dosNotificacionesConcurrentesMismoMpPaymentId_unaSolaFilaYAmbas2xx` |
| AUD-011 | ALTA | `DenunciaService.presentar` sin validar participación (BOLA) | CERRADO | 1 | beaf32a | `DenunciasModeracionIntegracionTest.aud011_*` (5) |
| AUD-012 | ALTA | Sin rate limiting ni bloqueo de intentos en ningún endpoint | ABIERTO | 2 | — | — |
| AUD-013 | ALTA | Reactivación de cuenta/matching sin verificar sanción vigente | CERRADO | 1 | 5ddaa1d | `DenunciasModeracionIntegracionTest.aud013_*` (5) |
| AUD-014 | ALTA | No existe infraestructura real de notificaciones | ABIERTO | 2 | — | — |
| AUD-015 | ALTA | `matching-service` sin autenticación, expuesto en el host | ABIERTO | 2 | — | — |
| AUD-016 | MEDIA | El middleware de Next.js sólo verifica que exista la cookie JWT | ABIERTO | 3 | — | — |
| AUD-017 | ALTA | `darDeBajaMenor` hace DELETE físico sin limpiar FKs dependientes | ABIERTO | 2 | — | — |
| AUD-018 | ALTA | Modo Bypass deja el marketplace gratis sin TTL ni alerta | ABIERTO | 2 | — | — |
| AUD-019 | ALTA | Límites de módulo organizativos, no reales: imports cruzados y ciclos | ABIERTO | 3 | — | — |
| AUD-020 | ALTA | La duración de la sesión no se persiste, se deriva de la franja | ABIERTO | 2 | — | — |
| AUD-021 | MEDIA | `subirEvidencia` acepta cualquier URL http(s) provista por el cliente | ABIERTO | 2 | — | — |
| AUD-022 | MEDIA | Los eventos de dominio viven en el módulo consumidor, no el emisor | ABIERTO | 3 | — | — |
| AUD-023 | MEDIA | `ReservasExceptionHandler` traduce cualquier conflicto de FK a 409 | ABIERTO | 3 | — | — |
| AUD-024 | MEDIA | M6 no puede generar resumen: falta proveedor de transcript | ABIERTO | 3 | — | — |
| AUD-025 | MEDIA | `FranjaService.publicar` no valida solapamiento entre franjas | ABIERTO | 3 | — | — |
| AUD-026 | MEDIA | `getCatalogos` cae a un mock local ante cualquier error, no sólo 404 | ABIERTO | 3 | — | — |
| AUD-027 | MEDIA | JWT con DNI como subject y sin invalidación al resetear password | ABIERTO | 3 | — | — |
| AUD-028 | MEDIA | Pagador y beneficiario pueden calificar dos veces la misma sesión | ABIERTO | 3 | — | — |
| AUD-029 | MEDIA | El webhook de LiveKit sólo procesa `participant_joined` | ABIERTO | 2 | — | — |
| AUD-030 | MEDIA | `Tasks_Tinku_Implementacion.md` desactualizado y contradice los chunks | CERRADO | 2 | 8707da6, 64fe386, 352d065, 2d4e108 | n/a (documentación) |
| AUD-031 | MEDIA | `matching-service` sin CI; E2E de Playwright mockean `/api` completo | ABIERTO | 2 | — | — |
| AUD-032 | BAJA | Spring Boot 3.3.4 sin escaneo de dependencias | ABIERTO | 3 | — | — |
| AUD-033 | MEDIA | `marcarAprobada`/`marcarRechazada` no verifican estado PENDIENTE previo | CERRADO | 2 | 5ddaa1d | `IdentidadFlujosIntegracionTest.aud033_credencialYaRechazada_noSePuedeAprobarNiReRechazar` |
| AUD-034 | BAJA | Sin configuración de producción; `JWT_SECRET` con default placeholder | CERRADO | 1 | e55d2d1 | `ArranqueSeguroValidatorTest` (prodConJwtPlaceholder, sinPerfilConJwtPlaceholder) |
| AUD-035 | BAJA | Tablas de V6 (CAP) huérfanas en la base tras ADR-M1-02 | ABIERTO | 3 | — | — |
| AUD-036 | BAJA | Ítems menores de calidad de código y performance (7 sub-ítems) | ABIERTO | 4 | — | — |
