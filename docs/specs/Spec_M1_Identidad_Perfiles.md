# Spec: M1 — Gestión de Identidad y Perfiles

**Módulo:** M1 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** Nada (es la base de todos los demás módulos)
**Alimenta a:** M2, M3, M4, M5, M6, M7, M9 (todos consumen identidad, capacidades y autorización de M1)
**Referencia de tiempos:** Tabla_Tiempos_Tinku.md

---

## 1. Resumen

Este módulo gestiona el ciclo de vida de las identidades del sistema. Un Usuario adulto verificado (por OCR) puede tener activas dos **capacidades independientes y combinables**: **Estudiante** (reserva sesiones para sí mismo) y **Adulto Responsable** (gestiona uno o más perfiles de menor). El **Tutor** es un camino de registro separado y exclusivo.

**Decisión de arquitectura clave (corregida): el menor sí tiene su propia cuenta y sesión, pero no puede crearla por sí mismo.** Es el Adulto Responsable quien da de alta la cuenta del menor (datos, credenciales de acceso, consentimiento). Una vez creada, el menor inicia sesión de forma independiente, con permisos restringidos: puede buscar y generar una Solicitud de Sesión (ver Spec de M4), pero no puede pagar, no puede autorizar Tutores nuevos por sí mismo, y no puede presentar Denuncias directamente — eso lo hace su Adulto Responsable en su nombre (ver Spec de M9), como restricción de permisos, no por falta de cuenta.

## 2. Actores

| Actor | Tiene cuenta/login propio | Cómo se crea |
|---|---|---|
| Usuario adulto (capacidad Estudiante y/o Adulto Responsable) | Sí | Registro propio, con OCR de verificación de edad e identidad |
| Perfil de menor | **Sí, pero no puede crearla por sí mismo** | La da de alta el Adulto Responsable (datos, credenciales de acceso, consentimiento); el menor no puede autorregistrarse bajo ninguna circunstancia |
| Tutor | Sí | Registro propio y exclusivo, con OCR + Credencial Académica |

## 3. Historias de Usuario y Criterios de Aceptación

### US-1 — Registro de Usuario adulto
*Como* persona adulta, *quiero* crear una cuenta, *para* usar la plataforma para mí, para gestionar a un menor a mi cargo, o ambas cosas.

- **Dado** que complete el registro y suba su DNI, **cuando** el OCR lo procese, **entonces** verifica tres cosas: (a) que la persona sea mayor de 18 años, (b) que el nombre y apellido declarados coincidan con los del documento, y (c) que el número de DNI no pertenezca ya a otra cuenta existente en el sistema (FR-ID-001). Si alguna falla, el registro no se completa.
- **Dado** que el OCR determine que es menor de edad, **cuando** eso ocurra, **entonces** el sistema muestra una pantalla informativa ("Sos menor de edad. Un Adulto Responsable debe crearte el perfil") y frena el flujo por completo — no se crea ninguna cuenta ni sesión para esa persona.
- **Dado** que el DNI ya esté registrado por otra cuenta, **cuando** eso se detecte, **entonces** el registro se rechaza con un mensaje claro, sin exponer a quién pertenece esa cuenta (FR-ID-018).

### US-1bis — Activar/desactivar capacidades desde Configuración
*Como* Usuario adulto ya registrado, *quiero* activar la otra capacidad más adelante, *para* no crear una cuenta nueva si mi situación cambia.

- **Dado** que tenga la cuenta con una sola capacidad activa, **cuando** active la otra desde Configuración, **entonces** se habilita de inmediato, sin nueva verificación OCR (FR-ID-015).
- **Dado** que quiera desactivar "Adulto Responsable", **cuando** todavía tenga perfiles de menor a cargo, **entonces** el sistema lo bloquea hasta transferir o eliminar esos perfiles primero (FR-ID-016).

### US-2 — Alta de la cuenta de un menor a cargo
*Como* Usuario con capacidad Adulto Responsable, *quiero* crear la cuenta de mi hijo/a, *para* que después pueda loguearse solo y solicitarme clases, sin manejar sus propios datos de pago ni contacto sin supervisión.

- **Dado** que inicie el alta, **cuando** complete el formulario (incluyendo el DNI del menor), **entonces** el sistema aplica las mismas validaciones de OCR que a un adulto — coincidencia de nombre/apellido/DNI y unicidad del DNI en el sistema (FR-ID-019) —, y exige un consentimiento explícito y separado del T&C general (BR-CONSENT-01).
- **Dado** que declare la fecha de nacimiento del menor, **cuando** calcule su edad, **entonces** rechaza el alta si tiene menos de 6 años (FR-ID-017).
- **Dado** que la cuenta quede creada, **cuando** eso ocurra, **entonces** el Adulto Responsable define las credenciales de acceso del menor (o genera una invitación para que el menor las configure él mismo en su primer ingreso) — el menor nunca pasa por el flujo de autorregistro (FR-ID-020).
- **Dado** un perfil de menor con sesiones futuras agendadas, **cuando** decida eliminarlo, **entonces** exige confirmación explícita advirtiendo la pérdida de esas reservas (FR-ID-014).
- **Dado** que ya tenga 5 perfiles de menor, **cuando** intente crear un sexto, **entonces** lo bloquea (FR-ID-013).

### US-3 — Registro de Tutor con verificación de edad e identidad
*Como* aspirante a Tutor, *quiero* registrarme y demostrar quién soy, *para* poder ofrecer clases.

- **Dado** que suba una foto de su DNI, **cuando** el OCR la procese, **entonces** aplica las mismas tres validaciones que US-1 (edad, coincidencia nombre/apellido/DNI, unicidad de DNI en el sistema) — rechaza el registro si es menor de 18 (FR-ID-007), sin excepciones.
- **Dado** que el OCR no pueda leer el documento, **cuando** eso ocurra, **entonces** permite hasta 3 intentos de foto por ciclo; si fallan los 3, exige 24hs de espera antes de un nuevo ciclo (FR-ID-011).

### US-4 — Carga y aprobación de Credencial Académica
*Como* Tutor recién registrado, *quiero* cargar mi título o certificado, *para* que mi perfil quede habilitado para matching.

- **Dado** que suba un documento de la lista cerrada aceptada (mantenida por el equipo de operaciones de Tinku, BR-ID-01), **cuando** lo envíe, **entonces** queda `pendiente` hasta revisión del Admin de Moderación y Seguridad (plazo: 48hs por intento, ver Spec de M8).
- **Dado** que el Admin rechace la credencial, **cuando** eso ocurra, **entonces** puede reintentar hasta 3 veces (FR-ID-008); al agotarlas, espera 24hs antes de un nuevo ciclo, duplicándose en cada agotamiento posterior (48hs, 96hs...), con soporte disponible siempre (FR-ID-012).

### US-5 — Marcar un Tutor como "no confiable"
*Como* Usuario con capacidad Adulto Responsable, *quiero* señalar que no confío en un Tutor, *para* que no vuelva a aparecer en las búsquedas de mis menores a cargo.

- **Dado** que marque a un Tutor como "no confiable", **cuando** eso ocurra, **entonces** deja de aparecer en los resultados de esa cuenta específicamente — sin alertar al Admin ni afectar su reputación pública (FR-ID-009). Aplica solo a la capacidad Adulto Responsable de esa cuenta, no a su capacidad Estudiante.

## 4. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-ID-001 | Registro de Usuario adulto: OCR verifica edad ≥18, coincidencia nombre/apellido/DNI, y unicidad del DNI en el sistema. |
| FR-ID-007 | Bloqueo del registro de Tutor si es menor de edad, sin excepciones. |
| FR-ID-008 | Hasta 3 intentos de recarga de Credencial ante rechazo, 48hs de revisión del Admin de Moderación y Seguridad por intento. |
| FR-ID-009 | Marcado de Tutor como "no confiable" — privado, acotado a la capacidad Adulto Responsable de esa cuenta. |
| FR-ID-011 | Ante falla de OCR: 3 intentos por ciclo, 24hs de espera antes de un nuevo ciclo. |
| FR-ID-012 | Ante agotamiento de intentos de Credencial: espera de 24hs, duplicándose en cada ciclo posterior agotado. |
| FR-ID-013 | Límite de 5 perfiles de menor por cuenta con capacidad Adulto Responsable. |
| FR-ID-014 | Confirmación explícita antes de eliminar un perfil de menor con sesiones futuras agendadas. |
| FR-ID-015 | Activación de la capacidad complementaria desde Configuración, sin nueva verificación OCR. |
| FR-ID-016 | Bloqueo de desactivación de "Adulto Responsable" mientras existan menores a cargo. |
| FR-ID-017 | Edad mínima de 6 años para crear un perfil de menor. |
| FR-ID-018 | Rechazo de registro si el DNI ya pertenece a una cuenta existente, sin exponer de quién es. |
| FR-ID-019 | La cuenta del menor pasa por la misma validación OCR de coincidencia nombre/apellido/DNI y unicidad que un adulto, al momento de darla de alta. |
| FR-ID-020 | El menor no puede autorregistrarse; el Adulto Responsable crea la cuenta y define o delega la configuración de sus credenciales de acceso. Una vez creada, el menor inicia sesión de forma independiente, con permisos restringidos. |

## 5. Reglas de Negocio Aplicables

- **BR-ID-01:** Credencial de lista cerrada, revisión manual estricta.
- **BR-CONSENT-01:** consentimiento de datos del menor explícito y separado del T&C general.
- **Regla de acceso (corregida en esta ronda):** el perfil de menor tiene su propia cuenta y sesión, con permisos restringidos, pero no puede crearla por sí mismo — la da de alta su Adulto Responsable.

## 6. Fuera de Alcance de este Spec

- La lógica de "Solicitud de Sesión" del menor y su conversión en Reserva por el adulto (ver Spec de M4).
- La combinación de Tutor con las otras capacidades — descartada para este MVP.

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Modelo de acceso del menor resuelto (E-01/E-04/E-31/E-38 del informe de QA).
- [x] Revisado contra la Constitución (Artículo II — el menor tiene cuenta propia pero nunca puede pagar ni autorizar Tutores por sí mismo; Artículo V — sin verificación redundante).

---

**Estado: APROBADO.**
