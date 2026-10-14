# Plan de pruebas exploratorias en `tinku.site`

> Pruebas manuales-asistidas por rol sobre el despliegue real, con los datos del seed. Objetivo:
> encontrar problemas antes del piloto. **No es una suite automática**: las suites viven en
> `backend/src/test` y `frontend/tests`.

## 0. Condiciones previas (si alguna falla, se corta y se avisa)

| # | Condición | Cómo se verifica |
|---|-----------|------------------|
| P1 | Todo mergeado a `main` | `git log origin/main` y 0 PR abiertos |
| P2 | `Deploy - produccion` verde en el último commit de `main` | Actions: tests → GHCR → webhook de Coolify |
| P3 | El sitio sirve la versión nueva | `https://tinku.site` responde, `https://api.tinku.site/actuator/health` en `UP` |
| P4 | **Este entorno alcanza `tinku.site` y `api.tinku.site`** | Si la política de red lo bloquea, se pide agregar los dos hosts a *Allowed domains* y no se simula nada |
| P5 | Hay datos de prueba (§1) | Login con una cuenta del seed |

## 1. Datos de prueba — lo que tenés que hacer vos antes

En producción registrarse exige la foto de un DNI real (OCR), así que se usan cuentas sembradas. Hay dos caminos:

**A. Perfil `seed-demo` (PR #75, ya integrado en esta rama).** En Coolify → recurso → *Environment Variables*, agregar
`TINKU_PERFILES=prod,seed-demo` y redeploy (el compose la pasa como `SPRING_PROFILES_ACTIVE: ${TINKU_PERFILES:-prod}`; no se usa el nombre `SPRING_PROFILES_ACTIVE` porque Coolify lo marca como gestionado por el compose). Crea solo (idempotente, contraseña `password123`):
37 Tutores con foto, tarifa y franjas de 1 h (08–23), Estudiantes adultos `40000001`–`40000014`
(`40000011` también es Adulto Responsable, `40000012` solo AR), 3 menores `50000001`–`50000003` con
Tutores autorizados e historial calificado. **Es una contraseña conocida: solo mientras dure la
prueba; después sacar el perfil.**

**B. `deploy/demo/cargar-demo.sql`** (RUNBOOK §10): DNIs `99900xxx`, contraseña a tu elección, y
exige subir la foto de cada Tutor a mano.

En ambos casos, después:
1. **Embeddings**: no hay que hacer nada. El backend pide el recompute solo al arrancar (`RecomputeAlArrancar`, ya con los Tutores sembrados), con reintentos mientras el modelo carga (~1 min). Si algún Tutor no aparece en la búsqueda, ahí sí se dispara a mano (RUNBOOK §10.1).
2. **Admins**: `AdminSeedRunner` solo corre en `dev`. Promover cuentas con el `INSERT INTO admin.admins` del RUNBOOK §2.5 (uno por rol: Moderación y Seguridad, Soporte Financiero).

Decime cuál camino usaste y las credenciales de admin (o dónde están). **No necesito ninguna clave real**: no se usa tarjeta ni dinero.

## 2. Reglas de la prueba

- Solo cuentas del seed. Nada de datos reales de nadie.
- Nada destructivo fuera del seed: no borrar, no tocar configuración, no escribir en Coolify.
- Dinero: sin MercadoPago real. Si el pago está en `bypass` se prueba el flujo; si exige tarjeta real, ese tramo queda **sin probar** y se informa.
- Cada hallazgo se registra con: rol, pasos, esperado, obtenido, severidad (bloqueante / alta / media / baja), captura. Nada se da por bueno "porque cargó".
- Los arreglos van en una rama aparte con test de regresión; no se mergean sin que lo pidas.

## 3. Herramientas

Navegador headless (Chromium de Playwright) con un contexto de sesión **separado por rol**. Audio/video del aula con dispositivos falsos (`--use-fake-device-for-media-stream`). Lectura de `https://api.tinku.site` con el JWT de cada rol para contrastar UI y API.

## 4. Subagentes testers (en paralelo, independientes)

Cada uno recibe: URL, credenciales de su rol, su checklist y el formato de hallazgo. Ninguno comparte sesión con otro.

| Agente | Rol | Qué prueba |
|--------|-----|-----------|
| T1 | **Estudiante adulto** | Búsqueda (texto libre, filtros, "sin coincidencia exacta", tutores sin calificaciones), perfil del Tutor, reservar (duraciones, ventana mínima de 30 min, horario ocupado), pagar, cancelar/reprogramar, calificar, resumen (opción oculta si está apagado) |
| T2 | **Tutor** | Panel, "Mi perfil" (foto, temas, especialidades), tarifa y piso ($6.140), pestaña Precio (27 % + ~6,04 % MP, neto por hora), franjas, aceptar/rechazar pedidos, pedido de reprogramación, mis cobros |
| T3 | **Adulto Responsable + menor** | Que el corte fail-closed de menores funcione (reservar/Solicitud rechazados con `sesiones-habilitadas=false`), permisos del menor (no paga, no denuncia), autorizaciones, que el resumen **nunca** se ofrezca con un menor |
| T4 | **Admin Moderación y Seguridad** | Cola de credenciales y CAP, denuncias, alertas, que un admin no resuelva casos propios |
| T5 | **Admin Soporte Financiero** | Colas de reembolsos y de pagos fallidos, tablero, permisos cruzados (el admin financiero no entra a moderación) |
| T6 | **Anónimo / adversarial** | Rutas protegidas sin sesión, IDs ajenos por URL (acceso horizontal), roles cruzados contra la API, rate limit (429), cabeceras, errores que filtren datos, registro/login/recuperar contraseña |
| T7 | **Transversal** | Móvil (360×640), accesibilidad básica (foco, contraste, teclado), tiempos de carga, consola con errores, textos en español rioplatense, PWA/manifest |

Aula en vivo (un Tutor y un Estudiante a la vez): lo hago yo con dos contextos en el mismo run, no por subagentes. Si LiveKit no está configurado, se registra y se sigue.

## 5. Orden de ejecución

1. P1–P5 (condiciones). 2. Humo con T1 (login → buscar → reservar) para confirmar que el seed sirve. 3. T1–T7 en paralelo. 4. Aula (dos contextos). 5. Consolidar hallazgos, deduplicar y ordenar por severidad.

## 6. Entregable

`docs/operacion/HALLAZGOS_pruebas.md` con la tabla de hallazgos, lo que **no** se pudo probar y por qué, y una propuesta de arreglos priorizada. Los bloqueantes se me escalan a vos antes de seguir.

## 7. Qué NO cubre

Pago con tarjeta real, correo real (Resend) más allá de lo que llegue a las casillas del seed, carga/estrés, y el kill-switch del cliente (T-M3-06 sigue pendiente).
