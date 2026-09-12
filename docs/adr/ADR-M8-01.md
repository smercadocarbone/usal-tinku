# ADR-M8-01 — Salud de Infraestructura en el panel M8 (datos reales, no estáticos)

## Estado
Aceptado (PR #19, 2026-09-12). La función **no está en Spec_M8 ni Plan_M8** —
fue pedida para el panel y la persona que decide la confirmó explícitamente
(fuera de scope de los documentos fuente). Se formaliza retroactivamente
(AGENTS §7) para dejar la desviación documentada y decidida, no implícita.

## Contexto
El panel `/admin` necesita una pestaña "Salud de Infraestructura" que el único
operador (1 desarrollador, presupuesto USD 0-100/mes) use en el día a día para
ver de un vistazo si las piezas del monolito modular están vivas:

- **Base de datos** (PostgreSQL del monolito),
- **Motor de Matching** (proceso Python separado, única excepción al Artículo
  VIII; expone `GET /health` — la infraestructura ya quedó definida allí,
  T-000-08),
- **MercadoPago** (pagos reales),
- **LiveKit** (salas) y
- **OCR** (Tesseract in-process, ADR-M1-01).

Requisitos del diagnóstico: reflejar el estado **en vivo** (no un panel
configurado a mano), no exponer credenciales, fallar rápido (timeouts cortos)
y poder distinguir entornos de prueba/seed de producción.

## Decisión
**Endpoint `GET /api/admin/salud`** read-only que sondea cada servicio en el
momento de la llamada (nunca cacheado):

- **Base de datos:** conexión JDBC real (`Connection.isValid`).
- **Matching:** `GET {tinku.matching-service.base-url}/health` real con
  timeout de 2s → `operational` si 200, `offline` si no responde.
- **MercadoPago:** si el token está configurado, una llamada autenticada
  (`v1/payments/search?limit=1`) con timeout de 2s: 200 → `operational`,
  401/403 u otro status → `degraded`, sin red → `offline`. Sin token →
  `degraded` (fail-closed, el mismo criterio que el resto del backend).
- **LiveKit:** compuerta de configuración (base-url + api-key + api-secret
  presentes) → `operational`/`degraded`. Se evalúa el mismo fail-closed que
  ya usa el backend para emitir tokens, sin abrir un WebSocket en cada sondeo.
- **OCR:** el bean activo es el real (Tesseract) u operando el stub de
  dev/test → `operational`/`degraded`.
- **Banderas de entorno:** `isTestMode` = perfil Spring activo distinto de
  `prod`; `hasSeedData` = conteo real en `identidad.usuarios` de emails
  `@test.tinku` (dominio de la semilla de dev).

Decisión de forma: el DTO usa los nombres exactos que ya consume el componente
del frontend (`SystemHealthDTO`/`ServiceStatus` con `isTestMode`,
`hasSeedData`, status `operational|degraded|offline`), y el endpoint:

- cae dentro de `/api/admin/**` pero se **excluye de la auditoría** de M8
  (`AdminWebConfig.excludePathPatterns("/api/admin/salud")`): sondear es
  lectura de diagnóstico — auditarla inundaría `log_auditoria_admin` en cada
  refresco del panel;
- lo puede ver **cualquier Admin activo** (transversal a roles de moderación y
  de soporte financiero): un 403 por rol no tendría sentido para diagnóstico.

## Alternativas descartadas / consideradas
- **Estados estáticos/config-manual:** se desactualizan en el día 1 y no
  sirven para diagnosticar una caída real. Descartado.
- **Spring Boot Actuator:** trae su propio contrato y endpoints de
  infraestructura que no mapean a los servicios de negocio del panel, y pedirle
  un chequeo de MercadoPago/LiveKit/OCR requeriría indicadores custom con el
  mismo esfuerzo. Descartado por no aportar sobre el endpoint dedicado.
- **Sondeo "barato" sin llamadas autenticadas (sólo TCP/conectividad):** no
  distingue "la API responde pero la credencial está rota" (lo más común en
  runtime). Por eso MercadoPago usa una llamada autenticada real de bajo costo
  en vez de un ping de red.

## Implementación
- `SaludInfraestructuraService` (probes con `RestClient` + timeouts de 2s,
  `DataSource`, `JdbcTemplate`, `Environment`, detección de `StubOcrService`).
- `SaludInfraestructuraController` (`GET /api/admin/salud`,
  `AdminModeracionGate.adminAutenticado`).
- `SaludInfraestructuraResponse` (DTO con `@JsonProperty("isTestMode")/...`).
- `AdminWebConfig`: `excludePathPatterns("/api/admin/salud")`.
- Frontend: `app/admin/page.tsx` consume `getSaludSistema()` +
  `getPasarelaEstado()` y monta `AdminInfrastructurePanel`; `/admin/:path*`
  agregado al matcher del middleware.
- **Tests:** `PasarelaBypassIntegracionTest.salud_infra_*` (shape del DTO,
  403 a no-admin en el perfil de test: `isTestMode=true`, MP/LiveKit
  `degraded`, DB `operational`, sin filas de auditoría).

## Consecuencias
- Un refresco del panel dispara 1 llamada autenticada a MercadoPago y 1 a
  matching por cada carga: costo trivial y acotado por timeout de 2s.
- El probe de MercadoPago sólo sale de red si hay token configurado — sin
  credenciales el panel muestra `degraded`, que es el estado real del backend
  (fail-closed).
- El contrato del DTO queda fijado por el frontend (nombres `isTestMode`,
  `hasSeedData`): se documenta en el ADR para no romperlo en refactors.
- Esta función queda como incremento del panel M8 y no reabre el alcance de
  los Specs: cualquier otra pantalla nueva se evalúa contra Spec/Plan antes de
  implementar.

## Registro de Decisiones Técnicas (Constitución)
No aplica: no hay fila del Registro que cubra un endpoint de salud. La
decisión se documenta en este ADR.