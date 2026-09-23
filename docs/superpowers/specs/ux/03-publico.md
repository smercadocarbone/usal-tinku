# UX-03 — Zona pública: landing, login, registros y recuperación

**Branch:** `ux/publico` · **Precondición:** UX-01 y UX-02 mergeadas · **Parte bloqueada por:** **U2**.
Capturas "antes": `capturas-antes/anonimo__*`.

---

## 1. Landing (`/`)

**Hoy** (`anonimo__home__desktop.png`, `__mobile.png`):
- **Sin cabecera:** no hay forma de ingresar ni de crear cuenta desde arriba; el logo no existe.
- La sección "Pensado para que las familias confíen" aparece **vacía** (UX-02 B8).
- El carrusel de materias corta la 4ª tarjeta contra el borde, sin indicio de que se desplaza.
- El copy promete "reservá la próxima clase **sin tener que registrarte primero**", y `/buscar`
  exige login (**U2**).
- El mockup del hero ("Martín · Tutor", "Resumen IA") es decorativo, no explica nada concreto.

**Objetivo:** que una madre o un padre entienda en 10 segundos **qué es, por qué es seguro y cómo
empiezo**, y que un tutor encuentre su camino.

**Estructura propuesta (mobile first):**
1. `Cabecera` pública (UX-01 §4).
2. **Hero:** título corto y concreto ("Clases particulares online con tutores verificados"),
   subtítulo con los 3 diferenciales (identidad verificada, pagás cuando termina la clase, aula
   segura para chicos), **un** CTA principal ("Buscar un tutor") y uno secundario de texto ("Quiero
   dar clases"). Buscador de una línea embebido en el hero ("¿Qué necesitás aprender?") que lleva a
   `/buscar?q=…`. Si U2 = no, el buscador lleva a crear cuenta con la búsqueda preservada.
3. **Cómo funciona** en 3 pasos con íconos: Buscá → Reservá y pagá (el dinero queda retenido) →
   Tomá la clase en el aula de Tinku.
4. **Seguridad para familias** (hoy invisible): 4 garantías **reales y verificables en el código**,
   sin exagerar: identidad validada con DNI, el menor no paga ni elige tutores solo, corte automático
   de la clase ante contenido inapropiado, el pago se libera al tutor recién después de la clase.
   **No prometas lo que no existe:** hoy no hay notificaciones por email ni el clasificador on-device
   (T-M3-06): no los menciones como funcionando.
5. **Materias:** grilla (no carrusel) de 6–8 materias, cada una → `/buscar?materia=…`.
6. **Para tutores:** bloque con beneficios y CTA a `/registro/tutor`.
7. **Preguntas frecuentes** (acordeón accesible): cómo se paga, qué pasa si la clase no se da,
   desde qué edad, cómo se verifica al tutor.
8. Footer con links legales (términos y privacidad **si existen**; si no, no pongas links rotos).

## 2. Login (`/login`)

**Hoy** (`anonimo__login__desktop.png`): correcto pero mínimo. Sin logo navegable, sin mostrar
contraseña, el DNI sin ayuda de formato.

**Propuesta:**
- Layout de dos columnas en desktop (formulario + panel de marca con una frase de confianza); una
  columna en mobile.
- `Campo` variante `dni` y variante `password` con "mostrar".
- Error de credenciales **genérico** ("DNI o contraseña incorrectos"): no revelar si el DNI existe.
  Con el bloqueo de FASE2-02, el 429 muestra "Demasiados intentos. Probá de nuevo en X minutos".
- Preservar `?siguiente=` (ya existe) y mostrar "Ingresá para continuar con tu reserva" cuando venga
  de un flujo de reserva.

## 3. Registro de adulto (`/registro`)

**Hoy** (`anonimo__registro__desktop.png`): stepper de 4 pasos (buena base). El paso 1 duplica la
opción "Soy Tutor" con un link "Registrate como tutor", las opciones no tienen íconos ni estado
seleccionado claro, y "Continuar" arranca deshabilitado sin explicar por qué.

**Propuesta:**
- Paso 1 "¿Quién va a usar Tinku?": tarjetas seleccionables (radio group accesible) con ícono:
  "Voy a tomar clases", "Tengo un hijo o hija a cargo", "Las dos cosas". Esto mapea a las
  capacidades Estudiante / Adulto Responsable **sin nombrarlas así** (UX-01 §5). "Quiero dar clases"
  va como link secundario abajo, una sola vez.
- Paso 2 "Tus datos": nombre, apellido, fecha de nacimiento (con validación de 18+ **antes** de
  enviar), email.
- Paso 3 "Verificá tu identidad": `SubidaArchivo` para la foto del DNI con **guía visual** (buena
  luz, sin reflejos, el frente completo) y qué pasa con la foto (se procesa para verificar y no se
  muestra a nadie: confirmá en el código que no se persiste la imagen del DNI antes de afirmarlo).
  Errores del OCR (ilegible, edad, DNI ya registrado) con mensaje específico y el conteo de intentos
  restantes (FR-ID-011: 3 intentos, espera de 24 hs).
- Paso 4 "Creá tu acceso": contraseña con la política de FASE2-02 mostrada **en vivo** (checklist),
  confirmación, aceptación de términos.
- Éxito: pantalla de bienvenida con el siguiente paso según lo elegido en el paso 1 (si tiene un
  hijo a cargo → "Sumá a tu hijo o hija").

## 4. Registro de tutor (`/registro/tutor`)

**Hoy** (`anonimo__registro_tutor__desktop.png`): formulario plano distinto del de adultos,
**sin email (UX-02 B1, rompe el alta)**, sin tildes, input de archivo nativo en inglés.

**Propuesta:** el mismo patrón de pasos que el registro de adulto, con los pasos propios del tutor:
Datos → Verificación de identidad → Acceso → **Qué sigue** (explicar el onboarding: subir la
credencial académica, cargar materias, horarios y precio; ver `06-tutor.md`). Reutilizá los mismos
componentes de los pasos compartidos.

## 5. Recuperar / resetear contraseña

**Hoy:** `/recuperar-password` promete un email que **no se envía** (UX-02 B9), y
`/resetear-password` tiene errores de hidratación (UX-02 B3).

**Propuesta:**
- Mientras P5 (proveedor de email) no esté resuelta: pantalla honesta con el canal real de
  recuperación (soporte). **No** mostrar un formulario que no hace nada.
- Cuando exista el email: formulario de DNI → pantalla de "Si el DNI está registrado, te mandamos un
  email" (mismo mensaje exista o no: no enumeración) → `/resetear-password?token=` con la política de
  contraseña en vivo y estados de token vencido/usado ("Este enlace ya no es válido. Pedí uno nuevo.").

## Backend que esta spec necesita

- **U2 = sí:** permitir `GET` de búsqueda y de perfil de tutor sin autenticación (revisar
  `SecurityConfig`, qué datos devuelve cada endpoint a un anónimo: **nunca** datos de menores), con el
  rate limiting de FASE2-02 aplicado a esos endpoints.

## Criterios de aceptación

- Capturas "después" de las 6 pantallas en desktop y mobile, en `capturas-despues/`.
- Registro de adulto y de tutor completos de punta a punta contra el backend real (no mocks): crear
  una cuenta de cada tipo en el stack local y loguearse.
- `axe` sin violaciones; E2E actualizados.
