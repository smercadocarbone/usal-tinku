# tinku-backend

Monolito modular de Tinku. Ver `Constitucion_Tinku.md` antes de tocar cualquier
cosa acá — en particular el Artículo VIII (por qué es un monolito, no
microservicios) y el Registro de Decisiones Técnicas (stack fijo, salvo ADR).

## Estructura de paquetes (bounded contexts, un paquete = un módulo del Spec)

```
com.tinku.identidad    → M1 — Gestión de Identidad y Perfiles
com.tinku.matching     → M2 — llama internamente al servicio Python (../tinku-matching-service)
com.tinku.aula         → M3 — Aula Virtual (LiveKit, kill-switch)
com.tinku.reservas     → M4 — Reservas y Agenda
com.tinku.pagos        → M5 — Motor de Pagos (MercadoPago)
com.tinku.resumen      → M6 — Resumen Automático
com.tinku.reputacion   → M7 — Calificaciones y Reputación
com.tinku.admin        → M8 — Panel de Administración
com.tinku.seguridad    → M9 — Denuncias y Seguridad
com.tinku.config       → Configuración transversal (Security, Quartz)
com.tinku.shared       → Eventos de dominio y utilidades compartidas
```

## Antes de arrancar a programar features

1. Leer el **Spec** y el **Plan técnico** del módulo en el que vas a trabajar — no hay excusa para no tenerlos, están todos en el repo de documentación.
2. Revisar `Tasks_Tinku_Implementacion.md` y marcar la tarea que estás por empezar.
3. Si tu tarea depende de un ADR pendiente (marcado en el Plan técnico del módulo), resolverlo primero — no improvisar una decisión de stack en medio de una feature.

## Cómo correr localmente

```bash
# 1. Levantar Postgres (Postgres 16, ver docker-compose.yml en la raiz del repo)
docker compose up -d db

# 2. Levantar el servicio de matching (ver ../tinku-matching-service/README.md)

# 3. Levantar el backend
./mvnw spring-boot:run
```

## Variables de entorno requeridas (ver application.yml)

`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `MP_ACCESS_TOKEN`, `MATCHING_SERVICE_URL`.

**Nunca commitear valores reales de estas variables.**
