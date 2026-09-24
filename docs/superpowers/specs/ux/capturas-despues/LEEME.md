# Capturas "después" del rediseño UX (2026-09-24)

Mismo formato que `capturas-antes/`: `<rol>__<ruta>__<viewport>.png`, a 1440×900 (desktop) y
390×844 (mobile), página completa.

**Cómo se sacaron:** Next en modo dev + Playwright, con la API **mockeada** por `page.route()`
(mismos contratos que el backend, incluidos los campos nuevos de U1 y UX-05). No se usó el stack
completo (`docker compose` + seeds): en la sesión donde se hizo el rediseño no había stack local
sembrado. Antes del piloto conviene repetirlas contra el stack real con los usuarios de
`scripts/seed-usuarios.sh` (método de `00-LEEME-ux.md` §6).

En las capturas mobile de página completa, la barra de navegación inferior (fija) aparece en el
medio de la imagen: es un efecto de la captura de página completa, no del layout.

| Archivo | Pantalla |
|---|---|
| `anonimo__home` | Landing |
| `anonimo__login`, `anonimo__registro`, `anonimo__registro_tutor` | Ingreso y registros (paso 1) |
| `anonimo__recuperar-password`, `anonimo__resetear-password_token_x` | Recuperación |
| `estudiante__buscar`, `estudiante__buscar-resultados` | Buscar: estado inicial y resultados |
| `adultoResponsable__tutores_ID` | Perfil del tutor (con "Para tus chicos") |
| `adultoResponsable__reservar_tutor_ID`, `menor__reservar_tutor_ID` | Reservar / pedir una clase |
| `estudiante__pagar` | Pago con cuenta regresiva y Modo Bypass |
| `adultoResponsable__cuenta_reservas`, `adultoResponsable__cuenta_reservas_ID` | Mis clases y detalle |
| `adultoResponsable__cuenta`, `estudiante__cuenta_acceso`, `menor__cuenta` | Perfil y acceso |
| `adultoResponsable__cuenta_menores` | Mis chicos |
| `tutor__cuenta_horarios`, `tutor__cuenta_perfil-tutor`, `tutor__cuenta_precio`, `tutor__cuenta_materias`, `tutor__cuenta_reservas` | Experiencia del tutor |
| `admin__admin`, `admin__admin_denuncias`, `fin__admin` | Panel (Moderación y Soporte Financiero con Modo Bypass) |
| `dev__componentes` | Catálogo del sistema visual (`/dev/componentes`) |
