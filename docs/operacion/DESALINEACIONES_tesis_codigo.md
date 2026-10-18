# Desalineaciones entre la tesis (`usal-tinku-docs`) y el sistema (`usal-tinku`)

**Fecha:** 2026-10-15 · **Alcance:** todo el repo de la tesis contra el repo de código.
**Versiones comparadas:**
- Tesis: `main` de `smercadocarbone/usal-tinku-docs` en `b3a21a4` (2026-10-13).
- Código: `origin/main` de `smercadocarbone/usal-tinku` en `03915af` (2026-10-14). Cuando la rama de remediación sin mergear `fix/rem-h` cambia algo, se aclara.

**Método:** se leyeron completos todos los capítulos, preliminares, apéndices, el anexo, el SVG de arquitectura, `tesis.md`, `README.md` y el texto de `main.pdf`. Cada afirmación verificable se contrastó con la Constitución, la Tabla de Tiempos, las specs, los ADR, `AGENTS.md`, la configuración, las migraciones y el código. Solo entra lo que tiene **cita de los dos lados**.

**Quién lo modificó por última vez:** sale de `git blame` sobre la línea citada, en los dos repos. Las cifras económicas del capítulo 5 se recalcularon.

Este documento **solo releva**. No propone el texto nuevo ni modifica nada de la tesis.

---

## 1. Resumen

| Bloque | Alta | Media | Baja | Total |
|---|---|---|---|---|
| A. Tesis contra código (reglas, arquitectura, economía) | 12 | 16 | 14 | 42 |
| B. Inconsistencias internas de la tesis | 3 | 8 | 7 | 18 |
| C. Artefactos de la tesis (PDF, `tesis.md`, README, metadatos) | 2 | 3 | 2 | 7 |
| D. Capítulos 6-8 y la rama de la tesis sin mergear | 6 | 9 | 1 | 16 |
| E. Desactualizaciones dentro del repo de código | 0 | 3 | 4 | 7 |
| **Total** | **23** | **39** | **28** | **90** |

**El patrón de autoría explica casi todo:**

- **Texto anterior a las decisiones.** La mayoría del texto desalineado de la tesis lo escribió **santy-air el 2026-09-10** (`09b76c3`) y el **2026-09-15** (`5870842`, que incluye el diagrama SVG). Las decisiones que lo contradicen se tomaron después, en el código, entre el **2026-09-23 y el 2026-10-14**: ADR-000-07 (Coolify), ADR-M1-04 (CAP), ADR-M5-02 (modelo A de cobro), ADR-M6-03 (GPT-4o), ADR-M1-08 (OCR) y ADR-M5-05/06 (comisión y resumen). Nadie volvió a esas partes de la tesis.
- **Revisiones parciales.** Las reescrituras de **Claude** (2026-09-25 `f208968`, 2026-09-26 `98024af` y 2026-10-14 `663047c`/`7bdfcbb`/`6fb95b0`) actualizaron secciones puntuales del capítulo 4 y del capítulo 5. Dejaron el resto del mismo capítulo sin tocar, y por eso hay contradicciones internas.
- **Plantilla sin completar.** Los capítulos **6, 7 y 8**, los apéndices **A y C**, el **anexo I**, la **declaración de IA**, el **README** y `main.xmpdata` siguen siendo la plantilla de **Norman Funes** (2026-09-07, `ef35e5f` y `2340157`).

### Commits de la tesis que se repiten (abreviados en el resto del documento)

| Clave | Commit | Autor | Fecha | Asunto |
|---|---|---|---|---|
| **TP** | `ef35e5f` | Norman Funes | 2026-09-07 | Template LaTeX para el Proyecto Final de Ingeniería Informática (USAL) |
| **T0** | `09b76c3` | santy-air | 2026-09-10 | Agrega contenido del proyecto Tinku y corrige compatibilidad con babel |
| **T1** | `5870842` | santy-air | 2026-09-15 | Reescribe justificación económica, agrega diagrama de arquitectura y corrige formato |
| **T2** | `f208968` | Claude | 2026-09-25 | Marco tecnológico: decisiones incorporadas durante la implementación |
| **T3** | `663047c` | Claude | 2026-10-14 | Capítulo 5: ticket USD 7,42 desde relevamiento Superprof y recálculo de VAN, TIR y sensibilidad |
| **T4** | `7bdfcbb` | Claude | 2026-10-14 | Capítulo 5: resumen como adicional a costo +20 % según duración, costo de IA actualizado y recálculo |
| **T5** | `6fb95b0` | Claude | 2026-10-14 | Capítulo 5: precio del resumen a costo +50 % |

### Commits del código que se repiten

| Clave | Commit | Autor | Fecha | Asunto |
|---|---|---|---|---|
| **C-ini** | `0616a05` | tinku | 2026-09-02 | chore: estado inicial del monorepo tinku |
| **C-doc** | `d38a234` | santy-air | 2026-09-03 | Update of docs with updates |
| **C-pgv** | `cf1e6c0` / `e0fc10a` | santy-air | 2026-09-04 | chunk m2-a/m2-b: ADR-M2-01 (pgvector) y /match real |
| **C-cap23** | `d7347b7` | santy-air | 2026-09-23 | docs(cap): ADR-M1-04 y enmienda v2.3 — el CAP vuelve acotado a tutores de menores |
| **C-gate** | `94a8c92` | santy-air | 2026-09-23 | feat(reservas): gate de menores del piloto (T-TES-10) — GREEN |
| **C-coolify** | `3e47a05` | Claude | 2026-09-24 | chore(deploy): produccion en Coolify con imagenes de GitHub Actions (ADR-000-07) |
| **C-tunnel** | `f82acc8` | Claude | 2026-09-24 | fix(deploy): Cloudflare Tunnel, IP real en el rate limit y tests que dependian de la hora |
| **C-rate** | `ad853a8` | Claude | 2026-09-24 | fix(identidad): rate limiting, bloqueo de login y política de contraseña (AUD-012) |
| **C-oauth** | `2aa93c7` | Claude | 2026-09-25 | R3: modelo de cobro A, OAuth de MercadoPago por Tutor (ADR-M5-02) |
| **C-llm** | `376ca29` | Claude | 2026-09-25 | resumen: GPT-4o como proveedor de LLM (ADR-M6-03) |
| **C-audio** | `735d784` | Claude | 2026-09-25 | resumen: grabación de solo audio (T08) y resumen como adicional pago (T09) |
| **C-cap** | `27f272f` / `722d4b0` | Claude | 2026-09-25 | cap: CAP obligatorio y vigente solo para clases con menores (T02) / frontend y moderación del CAP |
| **C-v25** | `81f0320` | Claude | 2026-09-26 | Plan de mejoras Tutor/Alumno: enmienda v2.5, specs, ADRs y tiempos |
| **C-27** | `9ca1aac` | Claude | 2026-10-14 | feat: comisión 27 % (T-TES-16), JaCoCo (T-TES-14), seed-demo (T-TES-18) y baseline 661 |

---

## A. Tesis contra código

### A.1 Seguridad del menor y reglas de negocio

**A-01 · CAP: optativo con *boost*, obligatorio para todos, u obligatorio solo con menores** — Alta
- **Tesis:**
  - `01-introduccion.tex:195-197`: «multiplicador de puntaje (*boost*) para aquellos perfiles que hayan validado voluntariamente su CAP».
  - `01-introduccion.tex:234-237`: «se habilitará su carga manual de forma optativa, incentivando a los tutores mediante una mejora algorítmica en su visibilidad».
  - `01-introduccion.tex:391-393`: «exigencia y revisión manual obligatoria del CAP».
  - `04-marco-tecnologico.tex:252-256`: «opcionalmente el CAP … la ausencia de un certificado … no bloqueará la activación del PerfilTutor ni la generación de una Reserva».
  - **Último cambio:** T0, en las cuatro citas.
- **Código:**
  - `docs/Constitucion_Tinku.md:16`: «CAP, obligatorio y vigente para que un Tutor quede habilitado a dictar clases a Menores … Los Tutores que solo enseñan a adultos no cargan el CAP» (C-cap23).
  - `Spec_M1:119` (FR-ID-026): «habilitado para Menores solo con un CAP `aprobado` y vigente» (C-cap, `722d4b0`).
  - `reservas/service/SolicitudService.java:89`: sin CAP vigente responde 409 (C-cap, `27f272f`).
  - `MatchingContextoService.java:101`: «a un menor solo le aparecen Tutores con CAP aprobado y vigente» (C-cap).
  - No hay *boost* en ningún lado.
- **Por qué:** en el código, el CAP es un filtro obligatorio (*fail-closed*) para dar clases a menores, no un incentivo de ranking. La tesis da tres reglas distintas para el mismo requisito de seguridad del menor.
- **Correcto:** el código. Solo `04-marco-tecnologico.tex:372` (T2) lo dice bien.

**A-02 · Rama adultos del kill-switch: «filtro de desenfoque»** — Baja
- **Tesis:** `04:432-434`: «el video del detectado se bloquea con un filtro de desenfoque y el sistema solicita confirmación». Último cambio: T0.
- **Código:** `frontend/src/app/aula/[id]/page.tsx:651` (`remotoOculto`): el video se oculta y se muestra un cartel. Último cambio: Claude, 2026-10-14, `d553009` «feat(aula): kill-switch en el aula … (M3C-05, ADR-M3-07)».
- **Por qué:** no hay desenfoque. Además, la tesis no menciona NSFWJS, el umbral de 0,85 en 3 muestras ni el plazo de 5 minutos de la rama adultos (ADR-M3-07).
- **Correcto:** el código.

**A-03 · La denuncia «suspende preventivamente al denunciado»** — Alta
- **Tesis:** `04:435-439`: «al registrarse una denuncia, el sistema suspende preventivamente al usuario denunciado». Último cambio: T2.
- **Código:** `seguridad/service/DenunciaService.java:108` (`presentar`): pasa la denuncia a `EN_REVISION` con un descargo de 48 hs. No suspende a nadie. Último cambio: santy-air, 2026-09-10, `4fdd8e7` «chunk m9: denuncias y moderacion…».
- **Por qué:** la suspensión automática existe solo en el track de **Alertas** del kill-switch (Spec M9 :14 y :101). La tesis mezcla los dos tracks.
- **Correcto:** el código y la Spec M9.

**A-04 · Las denuncias «preservan automáticamente la evidencia de la sesión» y tienen «verificación de veracidad»** — Alta
- **Tesis:** `01-introduccion.tex:360-363`. Último cambio: T0.
- **Código:**
  - `Spec_M9:21`: «agrego evidencia opcional» (C-ini).
  - `AGENTS.md:22`: «el buffer del kill-switch es rotativo de 30 s y se descarta salvo disparo — nunca grabación continua» (C-audio).
- **Por qué:** no se graba nada que se pueda preservar ante una denuncia. Hacerlo chocaría con la minimización de datos (Constitución, Art. V).
- **Correcto:** el código.

**A-05 · Retención de la evidencia «hasta la resolución»** — Baja
- **Tesis:** `04:437`. Último cambio: T2.
- **Código:** `docs/Tabla_Tiempos_Tinku.md:15`: «30 días desde la resolución de la Alerta». Último cambio: Claude, 2026-09-25, `b252b67` «aula: evidencia del kill-switch como archivo subido … (AUD-021)».
- **Correcto:** la Tabla de Tiempos, que es la fuente única de plazos.

**A-06 · El menor «puede reservar con un tutor autorizado»** — Media
- **Tesis:** `04:247-248`: «no puede … reservar con un tutor no autorizado previamente». Último cambio: T0.
- **Código:** Spec M4: el menor solo genera una **Solicitud de Sesión** y reserva su Adulto Responsable (AGENTS §3).
- **Por qué:** la frase deja entender que, con un tutor autorizado, el menor sí puede reservar. Es interno además: el propio `04:281` dice lo contrario (ver B-07).
- **Correcto:** `04:281` y el código.

**A-07 · Piloto «con alumnos reales» y público desde primaria, sin decir que el piloto corre sin menores** — Media
- **Tesis:**
  - `01-introduccion.tex:170-172` y `:328-330`.
  - Las limitaciones (`:380-383`) no lo mencionan.
  - Último cambio: T0.
- **Código:**
  - `AGENTS.md:38`: flag T-TES-10, «piloto sin menores».
  - `application.yml:177`: `sesiones-habilitadas: ${TINKU_MENORES_SESIONES_HABILITADAS:false}`.
  - Último cambio: C-gate.
- **Correcto:** el código. La tesis tiene que declarar la restricción.
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** el piloto no se hace. La validación pasa a evaluación técnica + sesiones moderadas con adultos y datos de prueba; el texto sugerido para `:170-172`, `:328-330` y las limitaciones está en `TESIS_sin_piloto.md` (Cap. 1). Pendiente de pegar en la tesis.

**A-08 · Revisión manual de la autenticidad del DNI** — Media
- **Tesis:** `01-introduccion.tex:384-389`. Último cambio: T0.
- **Código:**
  - `Spec_M1:31`: el OCR verifica edad, nombre y unicidad (C-ini).
  - `ADR-M1-01.md:16`: «la imagen del DNI nunca sale del servidor». Último cambio: santy-air, 2026-09-04, `1568916`.
  - No se guarda la imagen.
- **Por qué:** no hay revisión manual del DNI. La revisión manual existe para la credencial académica y para el CAP.
- **Correcto:** el código.

**A-09 · Especialidades «verificadas/declaradas» y triaje de credenciales presentados como hechos** — Media
- **Tesis:** `04:363-365` y `:414-420`, en la sección «Decisiones incorporadas durante la implementación». Último cambio: T2.
- **Código:**
  - `Tasks_Tinku_Implementacion.md:315`: «- [ ] T-ESP-01..04: Especialidades del Tutor …».
  - `ADR-M1-06.md:3`: «Implementación pendiente».
  - Último cambio: Claude, 2026-09-25, `abf631e`.
  - No aparece «especialidad» en el backend.
- **Correcto:** el código. Es una decisión tomada pero no implementada.

**A-10 · Reembolsos: «todo reembolso es total, nunca parcial, costo cero»** — Alta
- **Tesis:** `05-justificacion-economica.tex:750-752`. Último cambio: T0.
- **Código:**
  - `Spec_M5:116` (FR-PAG-009, enmendado v2.5): «prohibido el reembolso parcial automático, salvo BR-PAG-11 (adicional de resumen) y la clase de un paquete …» (C-v25).
  - `ADR-M5-03.md:63` (C-v25).
  - `ADR-M5-02.md:49-54`: sigue pendiente probar en sandbox que el reembolso total devuelve la comisión y el `marketplace_fee` (C-oauth).
- **Por qué:** hay dos parciales automáticos más los parciales manuales. Que el costo sea cero no está verificado con el modelo A. Además se contradice con `05:301-302` (ver B-05).
- **Correcto:** la spec y los ADR.

**A-11 · Escrow: el intermediario «retiene los fondos»** — Media
- **Tesis:**
  - `preliminares/glosario.tex:69-72` (T0).
  - `03-marco-teorico.tex:346-348`: «reteniendo los fondos hasta la realización de la clase» (T0).
  - `05:89`: «la retención de fondos vía MercadoPago Marketplace funciona como un mecanismo de formalización tributaria» (T0).
- **Código:**
  - `Constitucion_Tinku.md:84`: «escrow como ventana lógica de 24 hs».
  - `ADR-M5-02.md:39-40`.
  - Último cambio: C-oauth.
  - `ADR-M5-03.md:83-84`: «Tinku no retiene fondos» (C-v25).
- **Por qué:** con el modelo A, la plata entra directo a la cuenta del Tutor. Tinku solo puede reembolsar durante la ventana de 24 hs. Además, la tesis dice «AFIP» y la Spec M5 US-11 ya usa «ARCA».
- **Correcto:** el código.

**A-12 · Reparto de la comisión por OAuth del Tutor «queda abierto»** — Alta
- **Tesis:** `04:378-382`: «Hasta implementarlo, el dinero ingresa a la cuenta de la plataforma». Último cambio: T2.
- **Código:**
  - `ADR-M5-02.md:3`: «Aceptado … Implementado en R3».
  - `MercadoPagoClientHttp.java:23`.
  - Último cambio: C-oauth (el mismo día).
- **Correcto:** el código. Cambia quién recibe el dinero.

### A.2 Arquitectura e infraestructura

**A-13 · Estilo: «cliente-servidor en capas», con la IA como proveedor externo** — Alta
- **Tesis:**
  - `04:51`, `:56` y `:62`: «los servicios desacoplados (video, pagos, IA) … se delegan a proveedores externos» (T0).
  - SVG `diagrama_arquitectura_tinku.svg:83` (T1).
- **Código:**
  - `Constitucion_Tinku.md:52`: «monolito modular … nueve módulos» (C-ini).
  - El matching es un servicio Python propio (`matching-service/`), que es la excepción explícita.
  - `MatchingServiceClient.java:20` (santy-air, 2026-09-02, `9876443`).
- **Por qué:** la tesis nunca habla de monolito modular, de los 9 módulos ni de Quartz. Además, los embeddings los calcula Tinku, no un proveedor externo.
- **Correcto:** el código.

**A-14 · Matching e IA «asincrónicos mediante colas»** — Media
- **Tesis:** `04:107-110`. Último cambio: T0.
- **Código:**
  - `Constitucion_Tinku.md:56`: comunicación síncrona en proceso y «prohibido introducir un message broker externo» (C-ini).
  - El matching se llama por HTTP de forma síncrona.
  - El resumen funciona con `@EventListener` y Quartz.
- **Correcto:** el código.

**A-15 · Postgres en Supabase, conectado por Supavisor** — Alta
- **Tesis:**
  - `04:81` y `:196-197` (T0).
  - `05:337` y `:352`: «Supabase como backend» (T1).
  - `01:346-347` y `:467` (T0).
  - SVG `:36-37` (T1).
- **Código:**
  - `deploy/docker-compose.coolify.yml:29`: `pgvector/pgvector:pg16`.
  - `:99`: `jdbc:postgresql://db:5432/tinku`.
  - `ADR-000-07.md:45-46`: «Sin Vercel … ni Supabase».
  - Último cambio: C-coolify.
- **Correcto:** el código.

**A-16 · Frontend en Vercel** — Alta
- **Tesis:**
  - `04:217` y `:115-116` (T0).
  - `01:467` (T0).
  - `05:337` (T1).
  - SVG `:19` (T1).
- **Código:**
  - `docker-compose.coolify.yml:147`: `ghcr.io/smercadocarbone/tinku-frontend`.
  - `ADR-000-07.md:45`: «Sin Vercel (su plan Hobby no admite uso comercial)».
  - Último cambio: C-coolify.
- **Correcto:** el código.

**A-17 · Redis (Upstash) para estado de sala, chat y *rate limit*** — Alta
- **Tesis:**
  - `01:200-201` y `:343-344` (T0).
  - `03:342-344` (T0).
  - `04:82` y `:198` (T0).
  - `02:284-286` (T0).
  - SVG `:42-43` (T1).
  - `04:97` («chat» entre las operaciones REST, T0).
- **Código:**
  - `RateLimitFilter.java:19-21`: «en memoria del proceso … sin Redis» (C-rate).
  - `ADR-000-04.md:140`: «D8: sin Redis» (C-rate).
  - No hay Redis en `pom.xml` ni en el compose.
  - El texto del aula viaja por el canal de datos de LiveKit (`aula/[id]/page.tsx:994`).
- **Antecedente en el repo de código:** `docs/superpowers/specs/tesis/00-LEEME-tesis.md:99-100` ya anotaba «se corrige la tesis, no el código» (santy-air, 2026-09-23, `156a3b6`). Nunca se aplicó.
- **Correcto:** el código.

**A-18 · Índice semántico FAISS** — Media
- **Tesis:**
  - `01:193-194` y `:468` (T0).
  - `03:283-284` (T0).
  - `04:85` (T2).
- **Código:**
  - `ADR-M2-01.md:5`: «el índice semántico vive en pgvector».
  - `matching-service/main.py:11`.
  - Último cambio: C-pgv.
- **Por qué:** es una decisión de ADR obligatorio (AGENTS §2). La tabla de decisiones del capítulo 4 (T2) omite justamente ADR-M2-01. La tesis tampoco menciona el modelo real (`paraphrase-multilingual-MiniLM-L12-v2`, 384 dimensiones).
- **Correcto:** pgvector.

**A-19 · Host del piloto: «migración a un VPS (Hetzner) antes del piloto»** — Media
- **Tesis:**
  - `01:418-421` y `:467` (T0).
  - `02:339-340` (T0).
  - `04:218` (T0).
  - SVG `:32` (T1).
- **Código:**
  - `ADR-000-07.md:22`: «el "VPS" es un servidor en la casa del usuario, sin IP fija» (C-tunnel).
  - `:51-52`: «se acepta por simplicidad operativa para el piloto» (C-coolify).
- **Correcto:** el código. La tesis tampoco menciona los riesgos que el ADR acepta (cortes de luz e internet, un único punto de falla).

**A-20 · Backups de Supabase y «Flyway o Liquibase»** — Media
- **Tesis:** `04:199`. Último cambio: T0.
- **Código:**
  - `docker-compose.coolify.yml:50`: `postgres-backup-s3` hacia Cloudflare R2 (C-coolify).
  - `pom.xml`: Flyway.
- **Correcto:** el código.

**A-21 · Monitoreo con los paneles de Supabase y Upstash** — Baja
- **Tesis:** `04:471-474`. Último cambio: T0.
- **Código:** `ADR-M8-01.md:10`: panel propio «Salud de Infraestructura» (santy-air, 2026-09-12, `a505484`).
- **Correcto:** el código.

**A-22 · OCR «vía Tess4J, in-process»** — Media
- **Tesis:** `04:403-405`. Último cambio: T0.
- **Código:** `backend/pom.xml:91-92`: «Tesseract se invoca como programa del sistema (ADR-M1-08), sin Tess4J». Último cambio: Claude, 2026-09-26, `4f56690`.
- **Correcto:** el código. La garantía de que la imagen no sale del servidor sigue valiendo.

**A-23 · MercadoPago «con SDK oficial Java»** — Baja
- **Tesis:**
  - `01:468` (T0).
  - `04:84` (T0).
  - SVG `:54` (T1).
- **Código:** `MercadoPagoClientHttp.java:21`: cliente HTTP propio. No hay SDK en `pom.xml`. Último cambio: santy-air, 2026-09-08, `362631b`.
- **Correcto:** el código. LiveKit tampoco usa SDK: son tokens jjwt y llamadas Twirp.

### A.3 Funcionalidades y alcance

**A-24 · Asistente conversacional de repaso conceptual** — Alta
- **Tesis:**
  - `01:206-208` (OE6), `:400-403` y `:468` (T0).
  - `04:85` (T2).
  - `03:162` (T0).
  - SVG `:61` «Resumen/asistente» (T1).
- **Código:**
  - `Constitucion_Tinku.md:38`: alcance cerrado en 9 módulos (C-ini).
  - Ninguna spec, ADR ni línea de código tiene un asistente de repaso.
- **Correcto:** el código. Es un objetivo específico de la tesis que está fuera de alcance.

**A-25 · LLM del resumen «a confirmar» y «audio directo»** — Alta
- **Tesis:** `01:356-357` y `:468`: «procesa audio nativamente», «a confirmar entre GPT-4o y Gemini 2.0 Flash». Último cambio: T0.
- **Código:**
  - `Constitucion_Tinku.md:86-87`: «LLM solo con texto … GPT-4o … Gemini 2.0 Flash descartado».
  - `ADR-M6-03.md:16`.
  - Último cambio: C-llm.
- **Por qué:** mandar audio al LLM violaría la anonimización (FR-SUM-005).
- **Correcto:** el código. `04:360` (T2) ya lo dice bien.

**A-26 · Resumen automático como funcionalidad general** — Media
- **Tesis:**
  - `01:205-207` (OE6) y `:354-356` (T0).
  - `preliminares/abstract.tex:12-13` (T0).
- **Código:** `Spec_M6:63` (FR-SUM-001): «adicional pago … Con un Menor nunca se contrata ni se graba». Último cambio: C-audio.
- **Correcto:** el código. Los capítulos 4 y 5 ya lo dicen bien.

**A-27 · Diagrama: transcripción con «Whisper»** — Baja
- **Tesis:** SVG `:61`. Último cambio: T1.
- **Código:** `TranscriptSesionProveedorOpenAi.java:25`: `gpt-4o-mini-transcribe` (C-audio).
- **Correcto:** el código.

**A-28 · ADR citado para la transcripción** — Baja
- **Tesis:** `04:360`: «(ADR-M6-03)». Último cambio: T2.
- **Código:**
  - `ADR-M6-03.md:31`: «se decide con T08/TS1, no acá» (C-llm).
  - La decisión está en `ADR-M3-04.md:33` (C-audio).
- **Correcto:** ADR-M3-04.

**A-29 · Matching por «similitud de sus descripciones»** — Baja
- **Tesis:** `preliminares/glosario.tex:49-50`. Último cambio: T0.
- **Código:** `ADR-M2-03.md:19`: «score del Tutor = MAX(similitud con cada uno de sus temas)». Último cambio: Claude, 2026-09-26, `9bb492e`.
- **Correcto:** el código.

**A-30 · Modelo de datos: ADMIN como rol de Usuario, PerfilMenor/PerfilTutor como entidades, Reserva→Pago 1 a 1, estados** — Baja
- **Tesis:** `04:242-243`, `:253-261`, `:272` y `:392`. Último cambio: T0.
- **Código:**
  - `V2__m1_identidad.sql:12`: `tipo IN ('ADULTO','MENOR','TUTOR')` (C-ini).
  - `RolAdmin.java:15`: entidad aparte con dos roles (santy-air, 2026-09-10, `3e03302`).
  - `V46`: un pago cubre las 4 clases del paquete (Claude, 2026-09-27, `2469bed`).
  - `EstadoReserva`: tres variantes de no-show (`486a73b`).
  - `EstadoCap.VENCIDO` (`27f272f`).
- **Correcto:** el código.

**A-31 · Diagrama: administrador con «rol único»** — Baja
- **Tesis:** SVG `:67`. Último cambio: T1.
- **Código:** `RolAdmin.java` tiene dos roles (`3e03302`). El propio `04:292-295` también dice dos.
- **Correcto:** el código.

### A.4 Pruebas y calidad

**A-32 · Cobertura mínima del 70 % «por módulo»** — Media
- **Tesis:**
  - `02:238-240` (T0).
  - `04:450` (T0).
- **Código:**
  - `pom.xml:163` y `ADR-000-05.md:11`: «No hay umbral que corte el build».
  - `AGENTS.md:57-58`: 85,6 % global.
  - Último cambio: C-27. En `fix/rem-h` la cobertura es 86,2 %.
- **Correcto:** el código. El 70 % se supera de hecho, pero nada lo exige, y no se mide por módulo.

**A-33 · Jest para el frontend** — Media
- **Tesis:**
  - `01:469` (T0).
  - `02:332-333` (T0).
  - `04:451-453` (T0).
- **Código:** `frontend/package.json:9-10`: `oxlint` y `playwright test`. Último cambio: santy-air, 2026-09-19, `0cca2d0`.
- **Correcto:** el código. Las pruebas del frontend son Playwright con axe-core.

**A-34 · Apéndice D: «las pruebas se escribieron a mano»** — Media
- **Tesis:** `apendices/D-prompts-ia.tex:60-61`. Último cambio: T0.
- **Código:** 76 de los 184 commits que tocan tests en `origin/main` son de «Claude» (por ejemplo `9d17cb5`, 2026-10-14).
- **Correcto:** el historial de git. Afecta la declaración de uso de IA (ver C-05).

### A.5 Economía (capítulo 5 y apéndice E)

Verificado sin hallazgos: el CSV de Superprof (414 avisos, promedio 13.392,43, mediana 12.000), el ticket de 7,42, la inversión de 25.730, el margen de 1,5552, el VAN, la TIR y los repagos de los tres escenarios. También coinciden con `application.yml` la comisión del 27 %, el 6,04 % de MercadoPago, el tipo de cambio de 1.535, el margen de 1,50 y los modelos de IA.

**A-35 · La comisión de MercadoPago se descuenta como costo de Tinku, pero con el modelo A la paga el Tutor** — Alta
- **Tesis:**
  - `05:436`: «Costo de pasarela de pago & Ticket × 6,04 % & −0,4482» (T3).
  - `05:443-446`: «Es un costo real de cobranza» (T1).
- **Código:**
  - `ADR-M5-05.md:18`: «**Discrepancia con la tesis, declarada:** la economía unitaria de la tesis descuenta la pasarela como costo de Tinku; con el modelo A la paga el Tutor» (C-27).
  - `Spec_M5:12`: «el Tutor recibe 1 − 0,27 − 0,0604 = 66,96 %» (C-27).
- **Recálculo con el reparto real:**

  | Indicador | Tesis | Con el reparto real |
  |---|---|---|
  | Margen por sesión | 1,5552 | 2,0034 |
  | VAN base | 3.573 | 16.459 |
  | TIR base | 20,47 % | 38,4 % |
  | VAN pesimista | −14.499 | −6.820 |
  | Comisión de equilibrio | ≈ 25,3 % | ≈ 19,3 % |

  Las condiciones de viabilidad (`05:775-786`) y el «sobrestimaba el margen ~29 %» (`05:449`) describen un reparto que el sistema no tiene.
- **Correcto:** el código. El propio ADR pide ajustar la tesis.

**A-36 · Infraestructura «serverless»: «evita un VPS» como beneficio tangible** — Media
- **Tesis:** `05:105-107`, `:352`, `:364-367` y `:476`. Último cambio: T0 y T1.
- **Código:**
  - `ADR-000-07.md:45-46` (C-coolify).
  - `Constitucion_Tinku.md:80`: «Java + Spring Boot» (santy-air, 2026-09-22, `231bba0`).
- **Por qué:** el beneficio tangible y la justificación de horas con «Supabase como backend» no corresponden a lo desplegado.
- **Correcto:** el código.

**A-37 · Piso tarifario: el código lo atribuye a la tesis («USD 4/h»), pero la tesis no lo define** — Media
- **Tesis:**
  - `05:150-151`: «el mismo [tipo de cambio] que fija el piso tarifario» (T3).
  - `apendices/E-defensa-precio.tex:49` (T3).
  - «USD 4» nunca aparece en el capítulo 5 (verificado con `git log -S`).
- **Código:**
  - `Spec_M5:6`: «USD 4/h … $6.140; tesis Cap. 5: por debajo la comisión no cubre la operación».
  - `application.yml:155-158`.
  - Último cambio: Claude, 2026-09-24, `418a6cb` «feat(pagos): piso de tarifa de USD 4 por hora (T06…)».
- **Por qué:** la justificación que el código atribuye a la tesis no está en la tesis.
- **Correcto:** el valor del código. La cita a la tesis no tiene respaldo.

**A-38 · Neto del adicional de resumen «después de la pasarela»** — Baja
- **Tesis:** `05:260-262`. Último cambio: T4.
- **Código:** `Spec_M5:8`: «el adicional va íntegro a la plataforma (`marketplace_fee` = 27 % de la sesión + adicional)». Último cambio: Claude, 2026-10-14, `85f047a`. MercadoPago descuenta su tasa de los fondos del Tutor (ADR-M5-04/05).
- **Correcto:** el código.

**A-39 · Costo fijo «Procesamiento LLM (base)» de USD 55-70 por mes sin contraparte** — Baja
- **Tesis:**
  - `05:386` (T1).
  - Contra `05:205-207`, que dice que la IA ya no se imputa a la sesión (T4).
- **Código:** el único uso de LLM es el resumen, que se cobra aparte (`application.yml:178-180`). El matching es local y no paga API.
- **Correcto:** el código.

**A-40 · El paquete mensual con descuento (0-30 %) no aparece en el modelo económico** — Baja
- **Tesis:** `05:112-113`: «una única fuente: una comisión sobre cada sesión». Último cambio: T1.
- **Código:**
  - `ADR-M5-03.md:29` (C-v25).
  - `Spec_M5:174-185`.
- **Correcto:** el código. Es funcionalidad vigente desde la enmienda v2.5.

**A-41 · Duración «estándar de 60 minutos» y mezcla 30/60** — Baja
- **Tesis:** `05:187-188` y `:200`. Último cambio: T1.
- **Código:** `Spec_M4:122` (FR-RES-023): de 30 a 180 minutos en bloques de 30. Último cambio: Claude, 2026-09-24, `629fe12`.
- **Correcto:** la regla del código. El promedio de 51 minutos es una estimación propia.

### A.6 Pagos en producción e historial

**A-42 · «Umbral histórico» del 27 %: la tesis dice ≈ 27 %, la spec dice 23,4 %** — Baja
- **Tesis:** `05:176-178`. Último cambio: T3.
- **Código:** `Spec_M5:12`: «por debajo de 23,4 % el VAN del escenario base de entonces era negativo» (C-27).
- **Recálculo:** el modelo previo da 26,85 %.
- **Correcto:** la tesis. El historial de la spec está mal (ver E-04).

---

## B. Inconsistencias internas de la tesis

**B-01 · El umbral de comisión vale 25,7 % en un lugar y 25,3 % en otro** — Alta
- **Dónde:**
  - `05:179`: «el umbral baja a aproximadamente el 25,7 %». Último cambio: T3.
  - `05:722` y `:781`: «≈ 25,3 %». Último cambio: T4.
- **Por qué:** T4 sacó de la sesión el costo de IA de 0,0255 y no actualizó `:179`. Recalculado da 25,33 %. El margen real es de 1,7 puntos, no «poco más de un punto».
- **Correcto:** 25,3 % (también `Spec_M5:12`).

**B-02 · CAP con tres reglas distintas** — Alta
- Ver A-01. Las citas internas son `01:197` y `01:236` (optativo), `01:392` (obligatorio para todos), `04:252` (optativo) contra `04:372` (solo con menores, T2).

**B-03 · Remisiones a contenido que no existe** — Alta
- **Lado A, las remisiones:**
  - `05:69`: «el modelo se incluye como anexo» (T1).
  - `05:796` y `03:267`: remiten al capítulo de riesgos (T0 y T1).
  - `04:241` y `:295`: remiten a un «diccionario de datos … apéndice C» (T0).
  - `04:347` y `04:136`: remiten al anexo I (T2 y T0).
- **Lado B, los destinos (todos TP):**
  - El capítulo 6 es plantilla.
  - El apéndice C es la plantilla (la tabla `usuario`, scikit-learn).
  - El anexo I tiene `usuario/repositorio`.
- **Correcto:** ninguno de los dos lados.

**B-04 · LLM del resumen: el capítulo 1 dice «a confirmar»; los capítulos 4 y 5 ya lo deciden** — Media
- Ver A-25. `01:357` y `:468` (T0) contra `04:360` (T2).

**B-05 · «Nunca parcial» contra «el adicional se reembolsa íntegro»** — Media
- **Dónde:** `05:750-752` (T0) contra `05:301-302` (T4).

**B-06 · Redis: estado de sala sí y no** — Media
- **Dónde:** `01:201` y `03:343` (T0) contra `02:284-287` y la tabla del `04` (T0, mismo commit).

**B-07 · El menor «puede reservar» contra «no puede crear Reservas»** — Media
- **Dónde:** `04:247-248` contra `04:281`, ambos T0. Además, la Solicitud de Sesión no está entre «las nueve entidades» de `04:240`.

**B-08 · Kill-switch con una sola rama (capítulo 3) o con dos (capítulo 4)** — Media
- **Dónde:** `03:206`: «corte automáticamente … y bloquee la cuenta», contra `04:431` y siguientes. Ambos T0.
- **Correcto:** el capítulo 4 y AGENTS §3.

**B-09 · Los indicadores I-01 e I-02 significan otra cosa en el capítulo 7** — Media
- **Dónde:**
  - `02:128-129`: aceptación del matching y calidad percibida (T0).
  - `07:61-62`: latencia p95 y F1 (TP).
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** la reformulación de I-01..I-04 (T11b §3) y la estructura nueva del capítulo 7 usan los mismos nombres que el capítulo 2; propuesta en `TESIS_sin_piloto.md` (Cap. 2 y 7). Pendiente de pegar.

**B-10 · Instrumento de entrevistas distinto en el capítulo 2 y en el apéndice B** — Media
- **Dónde:**
  - `02:150-155`: dos tutores y cuatro familias (T0).
  - `apendices/B-entrevista.tex:23`: dos especialistas, «Sprint 3», que ya pasó y siguen pendientes (T0).
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** se propone que el instrumento con usuarios sean las sesiones moderadas y que el apéndice B tenga su protocolo (T11b §2); si el dueño no hace las entrevistas, quitar la promesa de `02:150-155`. Ver `TESIS_sin_piloto.md` (Cap. 2). Decisión del dueño.

**B-11 · Inversión valorizada en 812 h de cuatro perfiles contra «1 desarrollador, 15-20 h semanales»** — Media
- **Dónde:**
  - `05:333-336` (T1).
  - `01:470` y `02:224-225` (T0).
- **Por qué:** el capítulo 5 no aclara que es una valorización a precio de mercado.

**B-12 · Matching como «microservicio» contra «se descartaron microservicios propios»** — Baja
- **Dónde:** `01:192` y `:350` contra `04:56`, todos T0.
- **Por qué:** falta declarar la excepción del servicio Python.

**B-13 · La nota «corrige la mención original de Jest» apunta al capítulo equivocado** — Baja
- **Dónde:** `04:452` dice capítulo 2; el error está en `01:469`. Ambos T0.

**B-14 · Fechas de entrega: encabezados distintos del cronograma** — Baja
- **Dónde:**
  - Encabezados (TP) contra `02:272-278` (T0).
  - `main.tex:26`: `\graduationDate{17/11/2026}` contra el 28/11 del capítulo 2.
- **Gravedad:** no se imprime.

**B-15 · «Sprints de dos semanas» contra el Gantt** — Baja
- **Dónde:** `02:201` y `:224` contra `02:304-310`. Todos T0.

**B-16 · Siglas, glosario y bibliografía** — Baja
- **Usadas sin definir:** ADR, DNI, REST, AFIP, RENAPER, BNA, CAGR, FAISS.
- **Definidas y no usadas:** `sql`, `stakeholder`, `g:van`, y los símbolos `van`, `r` y `f1`.
- **Bibliografía de la plantilla nunca citada:** `chen2016`, `funes2025`, `lundberg2017`, `nielsen1993`, `pedregosa2011`, `postgresqldocs`.
- `refs.bib:329`: `superprof` con `urldate` 2026-09-10, aunque respalda el relevamiento del 2026-10-13.
- **Autoría:** TP, T0 y `3c7bcff` (Claude, 2026-10-13).

**B-17 · τ = 217 en la tabla de parámetros, pero los cálculos usan 216,67** — Baja
- **Dónde:**
  - `05:245` (T4).
  - `05:275` y `:278` (T5): 0,6459 y 0,9688 en lugar de 0,6461 y 0,9691.
- **Correcto:** 217, que es el valor del código (`application.yml:189`).

**B-18 · El abstract no es traducción del resumen** — Baja
- **Dónde:** `preliminares/abstract.tex:3` lo exige, pero `:7-15` habla de IA y `resumen.tex:14-47` no. Ambos T0.

Otras menores (T0 y T1):
- `05:271`: la columna «Neto de pasarela» en realidad resta también el costo de IA.
- `03:229`: «precio dinámico regional», que nadie retoma.
- `03:162`: remite a `sec:alcance` por el asistente.
- `02:219-220`: «nueve módulos descritos en el cap. 1», que no los enumera.

---

## C. Artefactos de la tesis

**C-01 · `main.pdf` muy desactualizado** — Alta
- **El PDF:** su último commit es **T0** (santy-air, 2026-09-10); los metadatos dicen 2026-09-11. Muestra una comisión del 15 %, VAN USD 47.709 y TIR 92,69 %. No tiene el apéndice E ni las «Decisiones incorporadas», y sale con las 15 cajas «Guía de la cátedra» (`guias=true`, `main.tex:13`, TP).
- **Los `.tex`:** 10 commits posteriores, hasta T5 (2026-10-14).
- **Correcto:** los `.tex`.

**C-02 · `tesis.md` desactualizado** — Alta
- **El archivo:** su último cambio es Claude, 2026-09-26 (`98024af`). Regenerado da 231 líneas de diferencia. Le faltan las secciones nuevas del capítulo 5, el apéndice E y las entradas `openai*`. Además, el script rompe `\frac` y `\tau` y deja ecuaciones ilegibles.
- **Los `.tex`:** T3 a T5.
- **Por qué importa:** el archivo existe «para pasarle el contenido a una IA», así que propaga las cifras viejas.

**C-03 · `README.md` es el de la plantilla** — Media
- **El archivo:**
  - `README.md:1`: «# Template — Proyecto Final…».
  - `:23`: clona `Noorman999/usal-proyecto-final-template`.
  - Último cambio: Norman Funes, 2026-09-07, `2340157` y TP.
- **Por qué:** no menciona `tesis.md`, el script, `main.pdf`, los apéndices D y E ni el repo de código.

**C-04 · Anexo I y apéndice C con el contenido de la plantilla** — Media
- **Dónde:**
  - `anexos/I-repositorios.tex:17`: `\prettygitlink{usuario/repositorio}`.
  - `apendices/C-detalles-tecnicos.tex:41-52`: scikit-learn 1.5, fastapi 0.111 y `CREATE TABLE usuario (id SERIAL…)`.
  - Último cambio: TP.
- **Código:** los repos `smercadocarbone/usal-tinku` y `usal-tinku-docs`; `requirements.txt` con `fastapi>=0.141.1` (dependabot, 2026-10-02, `b61264a`); `V2__m1_identidad.sql` con UUID, `dni` y `tipo`.

**C-05 · La declaración de uso de IA sigue siendo plantilla** — Media
- **Dónde:** `preliminares/declaracion-ia.tex:36`: `\completar{…}`. Último cambio: TP.
- **Por qué:** el repo de la tesis tiene 9 commits de «Claude», incluidos los que reescriben las cifras del capítulo 5, y el código tiene 129. El apéndice D dice que se referencia desde la declaración, pero el `\ref{apx:prompts}` está comentado (`declaracion-ia.tex:5`, T0). Es un requisito de la cátedra.

**C-06 · `main.xmpdata` con los valores de la plantilla** — Baja
- **Dónde:** «Título del proyecto», «Nombre Apellido». Último cambio: TP.
- **Contra:** `main.tex:22-39` (T0).
- **Por qué:** el PDF tiene Title y Author vacíos.

**C-07 · `guias=true`** — Baja
- **Dónde:** `main.tex:13` (TP). Para la entrega tiene que ir en `false`.

---

## D. Capítulos 6, 7 y 8, y la rama de la tesis sin mergear

**Estado en `main` de la tesis:** las 263 líneas de los capítulos 6, 7 y 8 son la plantilla. El último cambio de todas es TP (Norman Funes, 2026-09-07). Las fechas de entrega del capítulo 6 (29/9) y del capítulo 7 (13/10) ya pasaron.

**D-01 · El capítulo de resultados no reporta nada, y el repo sí tiene resultados** — Alta
- **Tesis:** `07-resultados.tex:31` y `:88-89`: `\completar` (TP).
- **Código:**
  - `AGENTS.md:55-58`: 661 tests, 0 fallas, 85,6 % de cobertura (C-27).
  - `HALLAZGOS_pruebas.md:3`: pruebas en producción con 7 testers (Claude, 2026-10-14, `77d0a94`).
  - En `fix/rem-h`: 738 tests y 86,2 %, la prueba del aula en vivo y `CALIBRACION_busqueda.md`.
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** hay resultados nuevos medidos para el capítulo 7 en `docs/operacion/EVALUACION_tecnica.md` (matching sobre 120 búsquedas, latencias p95, kill-switch, auditoría 36/36) y la estructura y el texto sugerido en `TESIS_sin_piloto.md` (Cap. 7). Tests y cobertura se copian del CI al cerrar. Pendiente de pegar.

**D-02 · La matriz de riesgos es la del ejemplo (API de terceros, reentrenar, hosting administrado, presupuesto en pesos)** — Media
- **Tesis:** `06-riesgos.tex:97-98` (TP).
- **Código:**
  - `RUNBOOK_produccion.md:12-19` (C-tunnel).
  - `AGENTS.md:5`: USD 0-100 por mes.
  - Terceros reales: LiveKit, MercadoPago y Resend. El modelo de embeddings no se reentrena.

**D-03 · Faltan los riesgos propios** — Alta
- **Tesis:** `06:37` y `:100` (TP).
- **Código:**
  - `Tasks_Tinku_Chunks.md:227`: T-M3-06 «bloqueante de seguridad» (santy-air, 2026-09-21, `ecb8897`).
  - `application.yml:177`: menores apagados (C-gate).
  - `HALLAZGOS_pruebas.md:80`: MercadoPago en cobro real (`77d0a94`).
- **Por qué:** no figuran estos riesgos ni las mitigaciones que ya están hechas (gate de menores, modo bypass, resumen *fail-closed*).

**D-04 · Conclusiones: OE-1 «Alcanzado» citando un capítulo vacío** — Media
- **Tesis:** `08-conclusiones.tex:50-51` (TP).
- **Código:** `Tasks_Tinku_Implementacion.md:277`: el piloto arranca el 27/10/2026 (santy-air, 2026-09-23, `235796b`).
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** el piloto del 27/10/2026 no se hace. OE-1 se declara según la evidencia del capítulo 7 (texto base en `TESIS_sin_piloto.md`, Cap. 8), sin «Alcanzado» hasta tener los resultados de las sesiones. Pendiente.

**D-05 · RNF de ejemplo (p95 < 5 s, F1 ≥ 0,70)** — Media
- **Tesis:** `07:61-62` (TP).
- **Código:** `Constitucion_Tinku.md:97-101`: los requisitos no funcionales reales (C-ini).
- **Actualización 2026-10-18 (T-TES-11b, sin piloto):** los umbrales que reemplazan a los del piloto están en T11b §1 (p95 de API, MRR, recall@5); propuesta en `TESIS_sin_piloto.md` (Cap. 7, final).

**D-06 · Fila de feedback inventada que figura como «Implementado»** — Baja
- **Tesis:** `07:103` (TP). Se imprime en el PDF.

### Rama `origin/revision/encuesta-modelo-riesgos` (santy-air, 2026-09-23, 7 commits sin mergear)

Esta rama escribe los capítulos 6 y 7 (`ce93850` y `a5f0e6f`). **Si se mergea tal como está, introduce estas afirmaciones, que ya no son ciertas:**

| # | Afirmación de la rama | Estado real en el código | Commit que lo cambió | Gravedad |
|---|---|---|---|---|
| D-07 | «455 casos de prueba» (07:175, :254, :407; 06:191) | 661 tests (AGENTS.md:55); 738 en `fix/rem-h` | C-27 | Alta |
| D-08 | «36 hallazgos, 14 cerrados, 22 abiertos» (07:212-217, :355) | `REGISTRO_FINDINGS.md`: 36 de 36 cerrados (AUD-009, 012, 014 y 015 el 2026-09-24) | `629fe12`, `76703a7`, `7ab745d`, `1e174de` | Alta |
| D-09 | «CAP para tutores de menores pendiente de reincorporar» (07:59, :269, :379, :418) | T-TES-02 cerrado el 2026-09-25; AUD-035 cerrado | `722d4b0` | Alta |
| D-10 | «El resumen automático no funciona todavía» (07:64, :416) | GPT-4o y transcripción implementados; *fail-closed* solo sin configuración | C-llm y C-audio | Alta |
| D-11 | «Cobertura \completar» (07:189) | 85,6 % (86,2 % en `fix/rem-h`) | C-27 | Media |
| D-12 | «Playwright simula la API; el matching fuera del CI» (07:184-187) | AUD-031 cerrado: `ci-matching.yml` y E2E nocturno contra el stack real | Claude, 2026-09-25, `af57691` | Media |
| D-13 | R-01: «el audio va directo al LLM» (06:116, :140-147) | El LLM solo recibe texto; el audio va al transcriptor | C-llm | Media |
| D-14 | R-06: «Gemini dado de baja» (06:121, :175-177) | ADR-M6-03: descartado por decisión del dueño | C-llm | Media |
| D-15 | «Kill-switch sin integrar en el cliente» (06:120, 07:61, :410) | T-M3-06c y 06e cerrados (NSFWJS en el navegador); falta el E2E real y el encendido de menores | Claude, 2026-10-14, `4e0092c`/`d553009` | Media |
| D-16 | «24 migraciones, 14 ADR, 136/32 tareas»; «36 tareas pendientes» como premisa de R-03 y R-14 | V1 a V47, 37 ADR, 221 tareas hechas y 9 pendientes | varios | Media |

Más diferencias de la misma rama, de gravedad media o baja:
- R-11: «MercadoPago solo probado con usuarios de prueba». Producción ya está en cobro real (`77d0a94`).
- R-10: «verificación de la firma digital del CAP». Está diferida según `ADR-M1-04.md:50` (C-cap23).
- R-15: «migración a VPS antes del piloto». Ya está en `RUNBOOK_produccion.md:12` (C-tunnel).
- **Actualización 2026-10-18 (T-TES-11b):** sin piloto, R-11 y R-15 se reformulan (R-11: cobros reales no validados con usuarios, la evaluación usa bypass; R-15: disponibilidad de la demo durante las sesiones) y se suman dos riesgos (validez limitada de la evaluación; parámetros calibrados sin datos de operación). Texto en `TESIS_sin_piloto.md` (Cap. 6).
- M4: «falta la selección dinámica de horarios». T-M4-13 se cerró el 2026-09-25 (`5322caa`).
- La rama no menciona las pruebas en producción del 2026-10-14.

Hay otra rama sin mergear: `origin/claude/ecstatic-thompson-fmtaeb` (Claude, 2026-09-26). Agrega un apéndice E con nueve diagramas UML y **choca de nombre** con el apéndice E actual («Defensa del precio»).

---

## E. Desactualizaciones dentro del repo de código

Alinear las dos fuentes también exige corregir estas siete cosas del lado del código:

| # | Dónde | Qué dice | Qué es lo vigente | Último cambio | Gravedad |
|---|---|---|---|---|---|
| E-01 | `docs/Constitucion_Tinku.md:85` | «Python (sentence-transformers + FAISS)» | pgvector (ADR-M2-01) | santy-air, 2026-09-03, `d38a234` (C-doc) | Media |
| E-02 | `docs/Constitucion_Tinku.md:82` | «Redis (Upstash) \| Decidido» | Sin Redis (ADR-000-04, D8) | santy-air, 2026-09-03, `d38a234` (C-doc) | Media |
| E-03 | `docs/adr/ADR-M6-03.md:37` | «USD 0,03-0,05 por resumen … dentro del presupuesto» | Con la transcripción, ≈ USD 0,19 por resumen de 51 minutos; 1.000 resúmenes ≈ USD 189 | Claude, 2026-09-25, `376ca29` (C-llm) | Media |
| E-04 | `docs/specs/Spec_M5_Motor_Pagos.md:12` | «por debajo de 23,4 % el VAN … era negativo» | El modelo previo da ≈ 26,9 % | Claude, 2026-10-14, `9ca1aac` (C-27) | Baja |
| E-05 | `docs/specs/Spec_M5_Motor_Pagos.md:8` | el adicional es «la única excepción a los reembolsos totales» | FR-PAG-022 suma el paquete como segunda excepción | Claude, 2026-10-14, `85f047a` | Baja |
| E-06 | `ADR-M5-02.md:50` y `ADR-M5-04` | precio fijo del adicional «$770» | Precio por duración (ADR-M5-06) | Claude, 2026-09-25 y 2026-09-28 | Baja |
| E-07 | `AGENTS.md` §9 | «36 hallazgos (7 CRÍTICA, 1 ya cerrado)» | `REGISTRO_FINDINGS.md`: 36 de 36 cerrados | (texto de FASE 0) | Baja |

Hay además un antecedente: `docs/superpowers/specs/tesis/00-LEEME-tesis.md` (santy-air, 2026-09-23, `156a3b6`) ya listaba correcciones pendientes de la tesis, entre ellas Redis y el chat (A-17). No se aplicaron.

---

## F. No verificable con los repos

- **Mercado:** cifras de mercado (USD 637,3 M, CAGR 16,6 %) y la banda de comisiones de Preply.
- **Tarifas externas:** tarifas de OpenAI, LiveKit (5.000 minutos del plan Build), Hetzner y LightNode; el tipo de cambio BNA del 09/09/2026; la tasa real de MercadoPago (el código la marca como estimada).
- **Instrumentos de investigación:** los datos de la encuesta y de las entrevistas (los apéndices A y B están vacíos).
- **Proceso:** las horas reales, GitHub Projects, Postman y Figma; las líneas de código, los commits y las *git-hours* de la rama sin mergear.
- **Ley 25.326 y Código Civil y Comercial:** su aplicación.
- **Pendiente de sandbox:** si el reembolso total de MercadoPago devuelve el `marketplace_fee` (ADR-M5-02 lo deja pendiente).
- **Producción:** si la flag de menores quedó encendida en producción después de la prueba del 2026-10-14 (en el repo el valor por defecto es `false`).
