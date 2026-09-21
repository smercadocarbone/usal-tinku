# Settings Shell (/cuenta y /admin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **Excepción para esta ejecución:** el spec ya está aprobado y no hay sesión de review humano separada — el mismo agente que escribió este plan lo ejecuta de punta a punta en el mismo turno (inline, sin subagentes), verificando con lint/tsc/Playwright antes de dar por terminada cada fase.

**Goal:** Reemplazar los hubs `/cuenta` y `/admin` (una sola página cliente gigante con tabs en memoria, con un bug real de navegación duplicada en `/cuenta`) por un patrón de navegación tipo Apple Settings: `SettingsShell` (sidebar en desktop, lista agrupada + "← Volver" en mobile) + rutas reales de Next.js App Router por sección.

**Architecture:** Un componente `SettingsShell` genérico (grupos de nav + `usePathname()` para resaltar activo) se monta una vez por hub desde `cuenta/layout.tsx` y `admin/layout.tsx`, envolviendo `children`. Cada sección pasa a ser su propio `page.tsx` con URL real. La lógica de negocio (fetches, validaciones, handlers) se migra tal cual — se mueve JSX y estado entre archivos, no se reescribe.

**Tech Stack:** Next.js 14 App Router, React 18, TypeScript, Tailwind 4, kit `ui/` propio (`Tarjeta`, `Boton`, `Campo`, `Alerta`, `Cargando`), Playwright.

**Spec:** `docs/superpowers/specs/2026-09-20-settings-shell-design.md`

## Global Constraints

- Ninguna sección se renderiza duplicada en el DOM (criterio de aceptación 1) — incluye no duplicar `<Cabecera>`/`<main>` en rutas anidadas.
- Cada sección tiene su propia URL navegable con atrás/adelante y compartible.
- Secciones condicionales a rol (Tutor / Adulto Responsable) usan exactamente `payload.tipo === "TUTOR"` / `payload.cap_ar === true` — sin relajar ni endurecer el guard.
- Mobile (`<lg`): raíz del shell = lista agrupada a ancho completo, sin panel de contenido. Sub-ruta = solo esa sección + "← Volver".
- Desktop (`≥lg`, mismo breakpoint que ya usa `PanelTutor` hoy): sidebar persistente + panel de contenido, activo resaltado con `usePathname()`.
- Ninguna lógica de negocio (fetch, validaciones, mutaciones) cambia de comportamiento.
- `bun run lint` y `npx tsc --noEmit` limpios.
- Suite Playwright de `/cuenta` y `/admin` actualizada y verde contra las nuevas rutas.
- Fuera de alcance: `/buscar`, `/reservar`, `/pagar`, `/aula/[id]`, `/login`, `/registro`, `/registro/tutor`, `/tutores/[id]`, `/recuperar-password`, `/resetear-password`. Ningún cambio de backend. Ningún cambio visual a los átomos de `ui/`.
- El proyecto usa `bun`, no `npm`, en todo el frontend.

## Decisiones no contempladas explícitamente por el spec

1. **`cuenta/reservas/page.tsx`, `cuenta/seguridad/page.tsx` y `cuenta/reservas/[id]/page.tsx`** hoy renderizan su propio `<Cabecera/>` + `<main>`. Como `cuenta/layout.tsx` envuelve TODAS las rutas anidadas bajo `/cuenta` (comportamiento nativo de Next.js App Router, no hay forma de excluir una sub-ruta sin route groups que el spec no pide), dejarles su propio `<Cabecera/>` produciría un `<header>` duplicado en el DOM — exactamente el tipo de bug que este rediseño corrige. Se les quita `<Cabecera/>` y se cambia su `<main className="mx-auto max-w-2xl px-5 py-8">` externo por un `<div className="max-w-2xl">` interno (el `<main>` y el padding ahora los pone el layout una sola vez). Cero cambios de lógica/fetch/validación. `cuenta/reservas/[id]/page.tsx` no está en la lista de archivos del spec, pero se ve forzado por la cascada de layouts de Next.js — se documenta acá.
2. **`admin/page.tsx`**: el spec dice "se elimina (reemplazado por layout.tsx + páginas por sección)", pero Next.js exige un `page.tsx` en cada segmento de ruta para que `/admin` (raíz exacta) no dé 404 — un `layout.tsx` solo no renderiza contenido para su propio segmento. Se interpreta como "se elimina la implementación vieja de 7 tabs", no el archivo en sí: `admin/page.tsx` se reescribe como un shim delgado que en desktop hace `router.replace("/admin/alertas")` (primera sección, Artículo II) y en mobile no renderiza nada (el `SettingsShell` ya muestra la lista agrupada en la raíz, sin importar qué reciba como `children`).
3. **`EditarCuenta.tsx`** se elimina del árbol de componentes tras la migración: su lógica de Email/Password pasa tal cual a `cuenta/acceso/page.tsx` y su lógica de Capacidades pasa tal cual a `cuenta/page.tsx` (Perfil). Mantener el componente como wrapper intermedio sin usuarios sería exactamente el tipo de código muerto que este refactor busca eliminar.
4. **"Buscar tutores" en `Cabecera`**: el spec dice que sigue disponible "donde ya está, en Cabecera" — hoy solo `reservas/page.tsx` le pasa un enlace (`{ href: "/buscar", label: "Buscar" }`) a `Cabecera`. Se estandariza: `cuenta/layout.tsx` le pasa `enlaces={[{ href: "/buscar", label: "Buscar tutores" }]}` a `Cabecera` una sola vez para todo `/cuenta/*`. Los dos botones de acción rápida ("Buscar tutores" / "Mis reservas") que hoy están sueltos en el body de `cuenta/page.tsx` se retiran: quedarían redundantes con el link de `Cabecera` y el ítem de nav "Mis reservas" del propio `SettingsShell`.
5. **Títulos de grupo del sidebar no son `<h1>`/`<h2>`**: son `<p>` visualmente iguales a un heading de sección pero fuera del outline de encabezados — evita un `heading-order` roto (el `<aside>` va antes que el contenido en el DOM, así que un `<h2>` ahí quedaría antes del `<h1>` de la sección).
6. **`admin/layout.tsx`** le pasa a `Cabecera` `enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]}` — no estaba en ningún lado antes, pero es consistente con el patrón que ya usan `reservas/page.tsx` y `seguridad/page.tsx` (link de vuelta a la otra zona de la app).

---

## Task 1: `SettingsShell` — componente de navegación

**Files:**
- Create: `frontend/src/components/settings/SettingsShell.tsx`

**Interfaces:**
- Produces: `export interface GrupoNavAjustes { titulo: string; items: { href: string; label: string }[] }`; `export default function SettingsShell({ base, grupos, children }: { base: string; grupos: GrupoNavAjustes[]; children: ReactNode })`.
- Consumido por: `cuenta/layout.tsx` y `admin/layout.tsx` (Task 2 y Task 9).

- [ ] **Step 1: Crear el componente**

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface ItemNavAjustes {
  href: string;
  label: string;
}

export interface GrupoNavAjustes {
  titulo: string;
  items: ItemNavAjustes[];
}

export interface SettingsShellProps {
  /** Ruta raíz del shell ("/cuenta" o "/admin"): decide qué se ve en mobile. */
  base: string;
  grupos: GrupoNavAjustes[];
  children: ReactNode;
}

/**
 * Patrón de navegación tipo Ajustes de Apple: sidebar/lista agrupada +
 * panel de contenido, con rutas reales (no estado de tabs en memoria).
 *
 * Desktop (`lg:`): sidebar fija + panel de contenido, siempre los dos juntos.
 * Mobile (`<lg`): en la raíz del shell se ve la lista agrupada a ancho
 * completo; en cualquier sub-ruta se ve solo el contenido con "← Volver".
 * No hay estado de "abierto/cerrado" propio — el pathname decide qué se
 * ve, así atrás/adelante del navegador funciona nativo.
 */
export default function SettingsShell({ base, grupos, children }: SettingsShellProps) {
  const pathname = usePathname();
  const enRaiz = pathname === base;

  return (
    <div className="mt-6 flex flex-col gap-6 lg:flex-row">
      <aside className={cn("w-full shrink-0 lg:block lg:w-56", !enRaiz && "hidden")}>
        <nav aria-label="Secciones" className="flex flex-col gap-6">
          {grupos.map((grupo) => (
            <div key={grupo.titulo}>
              <p className="px-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
                {grupo.titulo}
              </p>
              <ul className="mt-2 flex list-none flex-col gap-1 p-0">
                {grupo.items.map((item) => {
                  const activo = pathname === item.href || pathname.startsWith(`${item.href}/`);
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        aria-current={activo ? "page" : undefined}
                        className={cn(
                          "block rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                          activo
                            ? "bg-teal-700 text-white"
                            : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                        )}
                      >
                        {item.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>

      <div className={cn("min-w-0 flex-1", enRaiz && "hidden lg:block")}>
        {!enRaiz && (
          <Link
            href={base}
            className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-teal-700 lg:hidden"
          >
            ← Volver
          </Link>
        )}
        {children}
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: sin errores nuevos originados en este archivo (puede haber errores preexistentes en otros archivos que todavía no migramos — ignéralos en este paso).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/settings/SettingsShell.tsx
git commit -m "feat(frontend): agregar SettingsShell para navegación tipo Ajustes"
```

---

## Task 2: `/cuenta` — Perfil (raíz) + `layout.tsx`

**Files:**
- Modify: `frontend/src/app/cuenta/page.tsx` (reescribir completo)
- Create: `frontend/src/app/cuenta/layout.tsx`
- Modify: `frontend/src/components/EditarCuenta.tsx` → se borra (su lógica de Capacidades pasa a `page.tsx`, la de Email/Password pasa a Task 3)

**Interfaces:**
- Consume: `SettingsShell` (Task 1), `BannerCredencial` (sin cambios), `getSession` (`@/lib/auth`), `getPerfilPropio`/`actualizarCapacidades`/`mensajeDeError`/`type PerfilPropio` (`@/lib/api`).
- Produce: grupos de nav que Task 4-8 dan por sentado que existen como rutas (`/cuenta/acceso`, `/cuenta/horarios`, `/cuenta/materias`, `/cuenta/precio`, `/cuenta/menores`, `/cuenta/reservas`, `/cuenta/seguridad`).

- [ ] **Step 1: Crear `cuenta/layout.tsx`**

```tsx
"use client";

import type { ReactNode } from "react";
import { getSession } from "@/lib/auth";
import Cabecera from "@/components/Cabecera";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";

export default function CuentaLayout({ children }: { children: ReactNode }) {
  const session = getSession();
  const payload = session?.payload;

  const grupos: GrupoNavAjustes[] = [
    {
      titulo: "Cuenta",
      items: [
        { href: "/cuenta", label: "Perfil" },
        { href: "/cuenta/acceso", label: "Acceso" },
      ],
    },
  ];

  if (payload?.tipo === "TUTOR") {
    grupos.push({
      titulo: "Tutor",
      items: [
        { href: "/cuenta/horarios", label: "Mis Horarios" },
        { href: "/cuenta/materias", label: "Mis Materias" },
        { href: "/cuenta/precio", label: "Configuración de Precio" },
      ],
    });
  }

  if (payload?.cap_ar === true) {
    grupos.push({
      titulo: "Adulto responsable",
      items: [{ href: "/cuenta/menores", label: "Menores a cargo" }],
    });
  }

  grupos.push({
    titulo: "General",
    items: [
      { href: "/cuenta/reservas", label: "Mis reservas" },
      { href: "/cuenta/seguridad", label: "Denuncias y alertas" },
    ],
  });

  return (
    <>
      <Cabecera enlaces={[{ href: "/buscar", label: "Buscar tutores" }]} />
      <main className="mx-auto max-w-5xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Mi cuenta</h1>
        <SettingsShell base="/cuenta" grupos={grupos}>
          {children}
        </SettingsShell>
      </main>
    </>
  );
}
```

- [ ] **Step 2: Reescribir `cuenta/page.tsx` (Perfil: info + capacidades + credencial)**

```tsx
"use client";

import { useEffect, useState, type FormEvent } from "react";
import { getSession } from "@/lib/auth";
import {
  actualizarCapacidades,
  getPerfilPropio,
  mensajeDeError,
  type PerfilPropio,
} from "@/lib/api";
import BannerCredencial from "@/components/BannerCredencial";
import { Alerta, Boton, CampoCheckbox, Cargando, Tarjeta } from "@/components/ui";

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

export default function CuentaPerfilPage() {
  const session = getSession();
  const payload = session?.payload;

  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  const [cargandoPerfil, setCargandoPerfil] = useState(true);

  const [capEstudiante, setCapEstudiante] = useState(false);
  const [capAr, setCapAr] = useState(false);
  const [guardandoCapacidades, setGuardandoCapacidades] = useState(false);
  const [errorCapacidades, setErrorCapacidades] = useState<string | null>(null);
  const [exitoCapacidades, setExitoCapacidades] = useState(false);

  useEffect(() => {
    getPerfilPropio()
      .then((p) => {
        setPerfil(p);
        setCapEstudiante(p.capacidadEstudiante);
        setCapAr(p.capacidadAdultoResponsable);
      })
      .catch(() => setPerfil(null))
      .finally(() => setCargandoPerfil(false));
  }, []);

  async function onSubmitCapacidades(e: FormEvent) {
    e.preventDefault();
    setErrorCapacidades(null);
    setExitoCapacidades(false);
    setGuardandoCapacidades(true);
    try {
      const p = await actualizarCapacidades(capEstudiante, capAr);
      setPerfil(p);
      setExitoCapacidades(true);
    } catch (err) {
      setErrorCapacidades(mensajeDeError(err, "No se pudieron actualizar las capacidades."));
    } finally {
      setGuardandoCapacidades(false);
    }
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Perfil</h2>

      {payload?.tipo === "TUTOR" && (
        <div className="mt-4">
          <BannerCredencial />
        </div>
      )}

      <dl className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">DNI</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">{payload?.sub ?? "—"}</dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Tipo de cuenta</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}
          </dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Capacidad Estudiante</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.cap_est ? "Activa" : "Inactiva"}
          </dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Adulto Responsable</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.cap_ar ? "Activa" : "Inactiva"}
          </dd>
        </div>
      </dl>

      {perfil?.tipo === "ADULTO" && (
        <Tarjeta className="mt-6 w-full max-w-sm p-6">
          <h3 className="mb-3 text-base font-semibold text-slate-800">Capacidades</h3>
          {cargandoPerfil ? (
            <Cargando>Cargando…</Cargando>
          ) : (
            <form className="flex flex-col gap-3" onSubmit={onSubmitCapacidades}>
              <CampoCheckbox
                id="capEstudiante"
                etiqueta="Estudiante"
                checked={capEstudiante}
                onChange={(e) => setCapEstudiante(e.target.checked)}
              />
              <CampoCheckbox
                id="capAr"
                etiqueta="Adulto Responsable"
                checked={capAr}
                onChange={(e) => setCapAr(e.target.checked)}
              />
              {errorCapacidades && <Alerta tono="error">{errorCapacidades}</Alerta>}
              {exitoCapacidades && <Alerta tono="exito">Capacidades actualizadas.</Alerta>}
              <Boton
                type="submit"
                tamano="sm"
                className="w-fit"
                cargando={guardandoCapacidades}
                textoCargando="Guardando…"
                disabled={
                  capEstudiante === perfil?.capacidadEstudiante &&
                  capAr === perfil?.capacidadAdultoResponsable
                }
              >
                Guardar capacidades
              </Boton>
            </form>
          )}
        </Tarjeta>
      )}
    </section>
  );
}
```

Nota: el original mostraba el formulario de Capacidades solo cuando `perfil?.tipo === "ADULTO"` (adentro de `EditarCuenta.tsx`) — se preserva exactamente esa condición, no `payload?.cap_ar`.

- [ ] **Step 3: Borrar `EditarCuenta.tsx`**

```bash
rm frontend/src/components/EditarCuenta.tsx
```

- [ ] **Step 4: Verificar que no quedan imports rotos**

Run: `cd frontend && rg -n "EditarCuenta" src`
Expected: sin resultados (Task 3 todavía no creó `acceso/page.tsx`, así que en este punto puede no compilar — se resuelve en la Task 3. No correr `tsc` recién acá).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/cuenta/layout.tsx frontend/src/app/cuenta/page.tsx
git rm frontend/src/components/EditarCuenta.tsx
git commit -m "refactor(frontend): /cuenta usa SettingsShell — Perfil queda solo con info y capacidades"
```

---

## Task 3: `/cuenta/acceso` — Email + Contraseña

**Files:**
- Create: `frontend/src/app/cuenta/acceso/page.tsx`

**Interfaces:**
- Consume: `getPerfilPropio`, `actualizarEmail`, `cambiarPassword`, `mensajeDeError`, `type PerfilPropio` (`@/lib/api`).

- [ ] **Step 1: Crear la página** (lógica de Email + Contraseña migrada tal cual desde `EditarCuenta.tsx`)

```tsx
"use client";

import { useEffect, useState, type FormEvent } from "react";
import {
  actualizarEmail,
  cambiarPassword,
  getPerfilPropio,
  mensajeDeError,
  type PerfilPropio,
} from "@/lib/api";
import { Alerta, Boton, Campo, Cargando, Tarjeta } from "@/components/ui";

export default function CuentaAccesoPage() {
  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  const [cargandoPerfil, setCargandoPerfil] = useState(true);

  const [nuevoEmail, setNuevoEmail] = useState("");
  const [guardandoEmail, setGuardandoEmail] = useState(false);
  const [errorEmail, setErrorEmail] = useState<string | null>(null);
  const [exitoEmail, setExitoEmail] = useState(false);

  const [passwordActual, setPasswordActual] = useState("");
  const [passwordNueva, setPasswordNueva] = useState("");
  const [guardandoPassword, setGuardandoPassword] = useState(false);
  const [errorPassword, setErrorPassword] = useState<string | null>(null);
  const [exitoPassword, setExitoPassword] = useState(false);

  useEffect(() => {
    getPerfilPropio()
      .then((p) => {
        setPerfil(p);
        setNuevoEmail(p.email ?? "");
      })
      .catch(() => setPerfil(null))
      .finally(() => setCargandoPerfil(false));
  }, []);

  async function onSubmitEmail(e: FormEvent) {
    e.preventDefault();
    setErrorEmail(null);
    setExitoEmail(false);
    setGuardandoEmail(true);
    try {
      const p = await actualizarEmail(nuevoEmail);
      setPerfil(p);
      setExitoEmail(true);
    } catch (err) {
      setErrorEmail(mensajeDeError(err, "No se pudo actualizar el email."));
    } finally {
      setGuardandoEmail(false);
    }
  }

  async function onSubmitPassword(e: FormEvent) {
    e.preventDefault();
    setErrorPassword(null);
    setExitoPassword(false);
    setGuardandoPassword(true);
    try {
      await cambiarPassword(passwordActual, passwordNueva);
      setExitoPassword(true);
      setPasswordActual("");
      setPasswordNueva("");
    } catch (err) {
      setErrorPassword(mensajeDeError(err, "No se pudo cambiar la contraseña."));
    } finally {
      setGuardandoPassword(false);
    }
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Acceso</h2>

      <div className="mt-4 flex flex-col gap-4 sm:flex-row">
        <Tarjeta className="w-full max-w-sm p-6">
          <h3 className="mb-3 text-base font-semibold text-slate-800">Email</h3>
          {cargandoPerfil ? (
            <Cargando>Cargando…</Cargando>
          ) : (
            <form className="flex flex-col gap-3" onSubmit={onSubmitEmail}>
              <Campo
                id="nuevoEmail"
                etiqueta="Email"
                type="email"
                autoComplete="email"
                required
                value={nuevoEmail}
                onChange={(e) => setNuevoEmail(e.target.value)}
              />
              {errorEmail && <Alerta tono="error">{errorEmail}</Alerta>}
              {exitoEmail && <Alerta tono="exito">Email actualizado.</Alerta>}
              <Boton
                type="submit"
                tamano="sm"
                className="w-fit"
                cargando={guardandoEmail}
                textoCargando="Guardando…"
                disabled={!nuevoEmail || nuevoEmail === perfil?.email}
              >
                Guardar email
              </Boton>
            </form>
          )}
        </Tarjeta>

        <Tarjeta className="w-full max-w-sm p-6">
          <h3 className="mb-3 text-base font-semibold text-slate-800">Contraseña</h3>
          <form className="flex flex-col gap-3" onSubmit={onSubmitPassword}>
            <Campo
              id="passwordActual"
              etiqueta="Contraseña actual"
              type="password"
              autoComplete="current-password"
              required
              value={passwordActual}
              onChange={(e) => setPasswordActual(e.target.value)}
            />
            <Campo
              id="passwordNueva"
              etiqueta="Contraseña nueva"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={passwordNueva}
              onChange={(e) => setPasswordNueva(e.target.value)}
            />
            {errorPassword && <Alerta tono="error">{errorPassword}</Alerta>}
            {exitoPassword && <Alerta tono="exito">Contraseña actualizada.</Alerta>}
            <Boton
              type="submit"
              tamano="sm"
              className="w-fit"
              cargando={guardandoPassword}
              textoCargando="Guardando…"
            >
              Cambiar contraseña
            </Boton>
          </form>
        </Tarjeta>
      </div>
    </section>
  );
}
```

- [ ] **Step 2: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: sin errores (ya no debería haber referencias rotas a `EditarCuenta`).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/cuenta/acceso/page.tsx
git commit -m "feat(frontend): /cuenta/acceso — email y contraseña migrados desde EditarCuenta"
```

---

## Task 4: `/cuenta/horarios`, `/cuenta/materias`, `/cuenta/precio` — panel del Tutor

**Files:**
- Create: `frontend/src/app/cuenta/horarios/page.tsx`
- Create: `frontend/src/app/cuenta/materias/page.tsx`
- Create: `frontend/src/app/cuenta/precio/page.tsx`

**Interfaces:**
- Consume: `getSession` (`@/lib/auth`), `TabHorarios` (prop `tutorId: string`), `TabMaterias`, `TabPrecio` (sin props) — todos sin cambios.

- [ ] **Step 1: Crear `cuenta/horarios/page.tsx`**

```tsx
"use client";

import { getSession } from "@/lib/auth";
import TabHorarios from "@/components/tutor/TabHorarios";
import { Tarjeta } from "@/components/ui";

export default function CuentaHorariosPage() {
  const session = getSession();
  const tutorId = String(session?.payload.sub ?? "");

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Mis Horarios</h2>
      <Tarjeta className="mt-4 w-full">
        <TabHorarios tutorId={tutorId} />
      </Tarjeta>
    </section>
  );
}
```

- [ ] **Step 2: Crear `cuenta/materias/page.tsx`**

```tsx
"use client";

import TabMaterias from "@/components/tutor/TabMaterias";
import { Tarjeta } from "@/components/ui";

export default function CuentaMateriasPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Mis Materias</h2>
      <Tarjeta className="mt-4 w-full">
        <TabMaterias />
      </Tarjeta>
    </section>
  );
}
```

- [ ] **Step 3: Crear `cuenta/precio/page.tsx`**

```tsx
"use client";

import TabPrecio from "@/components/tutor/TabPrecio";
import { Tarjeta } from "@/components/ui";

export default function CuentaPrecioPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Configuración de Precio</h2>
      <Tarjeta className="mt-4 w-full">
        <TabPrecio />
      </Tarjeta>
    </section>
  );
}
```

- [ ] **Step 4: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/cuenta/horarios/page.tsx frontend/src/app/cuenta/materias/page.tsx frontend/src/app/cuenta/precio/page.tsx
git commit -m "feat(frontend): separar horarios/materias/precio del Tutor en rutas propias"
```

---

## Task 5: `/cuenta/menores` — panel del Adulto Responsable

**Files:**
- Create: `frontend/src/app/cuenta/menores/page.tsx`

**Interfaces:**
- Consume: `api`, `ApiError`, `getMenores`, `mensajeDeError`, `type Menor` (`@/lib/api`); componentes `ui/` ya usados por `PanelAdulto`.

- [ ] **Step 1: Crear la página** (contenido de `PanelAdulto` en `cuenta/page.tsx` actual, migrado tal cual — mismo estado, mismos handlers, mismo JSX interno)

```tsx
"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, ApiError, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import { Alerta, Boton, Campo, CampoSelect, Cargando, Tarjeta } from "@/components/ui";

const LABEL_ESTADO_SOLICITUD: Record<string, string> = {
  pendiente: "Pendiente",
  convertida: "Convertida en reserva",
  expirada: "Expirada",
  rechazada: "Rechazada",
};

interface Solicitud {
  id: string;
  tutorId: string;
  horarioPropuesto: string;
  estado: string;
  expiraAt: string;
}

interface UsuarioResponse {
  id: string;
  [key: string]: unknown;
}

interface ReservaResponse {
  id: string;
  [key: string]: unknown;
}

function formatFechaHoraEsAr(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function CuentaMenoresPage() {
  const [dni, setDni] = useState("");
  const [nombre, setNombre] = useState("");
  const [apellido, setApellido] = useState("");
  const [fechaNac, setFechaNac] = useState("");
  const [password, setPassword] = useState("");
  const [fotoDni, setFotoDni] = useState<File | null>(null);
  const [consentimiento, setConsentimiento] = useState(false);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const [procesando, setProcesando] = useState(false);

  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [errorMenores, setErrorMenores] = useState<string | null>(null);
  const [menorBajaId, setMenorBajaId] = useState("");
  const [bajaPaso, setBajaPaso] = useState<"idle" | "advertencia">("idle");
  const [bajaProcesando, setBajaProcesando] = useState(false);

  const cargarMenores = useCallback(() => {
    setErrorMenores(null);
    getMenores()
      .then((lista) => {
        setMenores(lista);
        setMenorBajaId((actual) => actual || lista[0]?.id || "");
      })
      .catch((err) => setErrorMenores(mensajeDeError(err, "No se pudo cargar tu listado de menores.")));
  }, []);

  useEffect(() => {
    cargarMenores();
  }, [cargarMenores]);

  const [solicitudes, setSolicitudes] = useState<Solicitud[]>([]);
  const [cargandoSolicitudes, setCargandoSolicitudes] = useState(true);
  const [solicitudesError, setSolicitudesError] = useState(false);
  const [aprobandoId, setAprobandoId] = useState<string | null>(null);

  const cargarSolicitudes = useCallback(() => {
    setCargandoSolicitudes(true);
    setSolicitudesError(false);
    api
      .get<Solicitud[]>("/api/solicitudes/pendientes")
      .then(setSolicitudes)
      .catch(() => setSolicitudesError(true))
      .finally(() => setCargandoSolicitudes(false));
  }, []);

  useEffect(() => {
    cargarSolicitudes();
  }, [cargarSolicitudes]);

  function altaMenor(e: FormEvent) {
    e.preventDefault();
    setError("");
    setExito("");

    if (!consentimiento) {
      setError("Necesitas tu consentimiento como Adulto Responsable para dar de alta al menor");
      return;
    }
    if (!fotoDni) {
      setError("Adjunta la foto del DNI del menor.");
      return;
    }
    if (password.length < 8) {
      setError("La contraseña debe tener al menos 8 caracteres.");
      return;
    }

    const datos = {
      dniDeclarado: dni,
      nombreDeclarado: nombre,
      apellidoDeclarado: apellido,
      fechaNacimientoDeclarada: fechaNac,
      password,
      consentimientoExplicito: true,
      versionTextoConsentimiento: "v1",
    };

    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);

    setProcesando(true);
    api
      .post<UsuarioResponse>("/api/usuarios/menores", form)
      .then(() => {
        setExito("Menor dado de alta.");
        cargarMenores();
        setDni("");
        setNombre("");
        setApellido("");
        setFechaNac("");
        setPassword("");
        setFotoDni(null);
        setConsentimiento(false);
      })
      .catch((err) => {
        if (err instanceof ApiError) setError(err.message);
        else setError("Error inesperado.");
      })
      .finally(() => setProcesando(false));
  }

  function aprobarSolicitud(id: string) {
    setAprobandoId(id);
    api
      .post<ReservaResponse>(`/api/solicitudes/${id}/aprobar`)
      .then((res) => {
        setSolicitudes((prev) => prev.map((s) => (s.id === id ? { ...s, estado: "convertida" } : s)));
        setExito(`Solicitud aprobada. Se creo la reserva.`);
        window.location.href = `/pagar?reserva=${res.id}`;
      })
      .catch((err) => {
        if (err instanceof ApiError) setError(err.message);
        else setError("Error inesperado.");
      })
      .finally(() => setAprobandoId(null));
  }

  function bajaMenorSinConfirmar() {
    if (!menorBajaId) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorBajaId}`)
      .then(() => {
        setExito("Menor dado de baja.");
        setBajaPaso("idle");
        setMenorBajaId("");
        cargarMenores();
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.status === 409 || err.status === 422)) {
          setBajaPaso("advertencia");
        } else {
          setError(err instanceof ApiError ? err.message : "Error inesperado.");
        }
      })
      .finally(() => setBajaProcesando(false));
  }

  function bajaMenorConfirmar() {
    if (!menorBajaId) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorBajaId}?confirmar=true`)
      .then(() => {
        setExito("Menor dado de baja.");
        setBajaPaso("idle");
        setMenorBajaId("");
        cargarMenores();
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Error inesperado.");
      })
      .finally(() => setBajaProcesando(false));
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Menores a cargo</h2>

      <Tarjeta className="mt-4 w-full max-w-sm p-8">
        <form className="flex flex-col gap-4" onSubmit={altaMenor}>
          <Campo
            id="dniMenor"
            etiqueta="DNI del menor"
            type="text"
            inputMode="numeric"
            value={dni}
            onChange={(e) => setDni(e.target.value)}
            required
          />
          <Campo
            id="nombreMenor"
            etiqueta="Nombre"
            type="text"
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            required
          />
          <Campo
            id="apellidoMenor"
            etiqueta="Apellido"
            type="text"
            value={apellido}
            onChange={(e) => setApellido(e.target.value)}
            required
          />
          <Campo
            id="fechaNacMenor"
            etiqueta="Fecha de nacimiento"
            type="date"
            value={fechaNac}
            onChange={(e) => setFechaNac(e.target.value)}
            required
          />
          <Campo
            id="passMenor"
            etiqueta="Contrasena"
            type="password"
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Campo
            id="fotoDniMenor"
            etiqueta="Foto del DNI"
            type="file"
            accept="image/*"
            onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
            required
          />
          <label className="flex cursor-pointer items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1 accent-teal-600"
              checked={consentimiento}
              onChange={(e) => setConsentimiento(e.target.checked)}
              required
            />
            Confirmo que soy el Adulto Responsable del menor y doy mi consentimiento explicito para crear su cuenta
          </label>

          {error && <Alerta tono="error">{error}</Alerta>}
          {exito && <Alerta tono="exito">{exito}</Alerta>}

          <Boton type="submit" cargando={procesando} textoCargando="Cargando...">
            Dar de alta
          </Boton>
        </form>
      </Tarjeta>

      <h3 className="mt-6 text-base font-semibold text-slate-800">Solicitudes pendientes</h3>
      {cargandoSolicitudes ? (
        <Cargando>Cargando...</Cargando>
      ) : solicitudesError ? (
        <Alerta tono="error" className="w-fit">
          No se pudieron cargar las solicitudes.
          <Boton
            variante="secundario"
            tamano="sm"
            className="mt-3 flex"
            onClick={cargarSolicitudes}
          >
            Reintentar
          </Boton>
        </Alerta>
      ) : solicitudes.length === 0 ? (
        <p className="text-slate-500">No hay solicitudes pendientes.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {solicitudes.map((s) => (
            <Tarjeta as="li" key={s.id} className="mb-3 w-full max-w-sm p-4">
              <strong>Solicitud #{s.id.slice(0, 8)}</strong>
              <p className="my-1 text-sm text-slate-500">
                {formatFechaHoraEsAr(s.horarioPropuesto)}
              </p>
              <p className="my-1 text-sm">
                Estado: {LABEL_ESTADO_SOLICITUD[s.estado] ?? s.estado}
              </p>
              {s.expiraAt && (
                <p className="my-1 text-sm text-slate-500">
                  Expira: {formatFechaHoraEsAr(s.expiraAt)}
                </p>
              )}
              {s.estado === "pendiente" && (
                <Boton
                  className="mt-2"
                  cargando={aprobandoId === s.id}
                  textoCargando="Procesando..."
                  onClick={() => aprobarSolicitud(s.id)}
                >
                  Aprobar
                </Boton>
              )}
            </Tarjeta>
          ))}
        </ul>
      )}

      <h3 className="mt-6 text-base font-semibold text-slate-800">Baja de menor</h3>

      {errorMenores && <Alerta tono="error">{errorMenores}</Alerta>}

      {!errorMenores && menores === null && <Cargando>Cargando tus menores…</Cargando>}

      {menores !== null && menores.length === 0 && (
        <p className="text-sm text-slate-500">No tenés menores a cargo todavía.</p>
      )}

      {menores !== null && menores.length > 0 && (
        <Tarjeta className="mt-3 w-full max-w-sm p-4">
          {bajaPaso === "advertencia" ? (
            <>
              <p role="alert" className="mb-2 text-amber-800">
                Este menor tiene reservas futuras. Se cancelaran.
              </p>
              <div className="flex gap-2">
                <Boton
                  className="bg-red-700 enabled:hover:bg-red-800"
                  cargando={bajaProcesando}
                  textoCargando="Procesando..."
                  onClick={bajaMenorConfirmar}
                >
                  Confirmar baja
                </Boton>
                <Boton variante="secundario" onClick={() => setBajaPaso("idle")}>
                  Cancelar
                </Boton>
              </div>
            </>
          ) : (
            <div className="flex flex-col gap-3">
              <CampoSelect
                id="menorBaja"
                etiqueta="Menor"
                value={menorBajaId}
                onChange={(e) => setMenorBajaId(e.target.value)}
              >
                {menores.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.nombre} {m.apellido}
                  </option>
                ))}
              </CampoSelect>
              <Boton
                variante="secundario"
                className="w-fit"
                cargando={bajaProcesando}
                textoCargando="Procesando..."
                onClick={bajaMenorSinConfirmar}
              >
                Dar de baja
              </Boton>
            </div>
          )}
        </Tarjeta>
      )}
    </section>
  );
}
```

- [ ] **Step 2: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/cuenta/menores/page.tsx
git commit -m "feat(frontend): mover el panel del Adulto Responsable a /cuenta/menores"
```

---

## Task 6: adoptar `/cuenta/reservas` y `/cuenta/seguridad` al shell (sin tocar lógica)

**Files:**
- Modify: `frontend/src/app/cuenta/reservas/page.tsx`
- Modify: `frontend/src/app/cuenta/seguridad/page.tsx`
- Modify: `frontend/src/app/cuenta/reservas/[id]/page.tsx`

**Interfaces:** ninguna nueva — solo se les quita `<Cabecera/>` y se cambia el `<main>` externo por un `<div>` interno (decisión #1 arriba). El resto del archivo (imports, estado, handlers, JSX interno) no cambia una sola línea.

- [ ] **Step 1: `cuenta/reservas/page.tsx`** — reemplazar el `return`:

Antes:
```tsx
  return (
    <>
      <Cabecera enlaces={[{ href: "/buscar", label: "Buscar" }, { href: "/cuenta", label: "Mi cuenta" }]} />

      <main className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-xl tracking-tight">
          Mis reservas
        </h1>
```
...
```tsx
      </main>
    </>
  );
```

Después:
```tsx
  return (
    <div className="max-w-2xl">
      <h1 className="text-xl tracking-tight">
        Mis reservas
      </h1>
```
...
```tsx
    </div>
  );
```

Y quitar el import `import Cabecera from "@/components/Cabecera";` (deja de usarse en este archivo).

- [ ] **Step 2: `cuenta/seguridad/page.tsx`** — mismo patrón: quitar `<Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />` y su import, cambiar `<main className="mx-auto max-w-2xl px-5 py-8">` por `<div className="max-w-2xl">` (y el `</main>` de cierre por `</div>`), quitar el fragment (`<>...</>`) externo que ya no hace falta con un solo hijo.

- [ ] **Step 3: `cuenta/reservas/[id]/page.tsx`** — mismo patrón: quitar `<Cabecera enlaces={[{ href: "/cuenta/reservas", label: "Mis reservas" }]} />` y su import, cambiar `<main className="mx-auto max-w-2xl px-5 py-8">` (línea 227) por `<div className="max-w-2xl">`, y el `</main>` de cierre (línea 447) por `</div>`, quitar el fragment externo.

- [ ] **Step 4: Verificar tipos y que no queden imports de Cabecera sin usar**

Run: `cd frontend && npx tsc --noEmit && rg -n "import Cabecera" src/app/cuenta/reservas src/app/cuenta/seguridad`
Expected: `tsc` limpio; el `rg` no debe listar ninguna de las tres páginas tocadas en esta tarea.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/cuenta/reservas/page.tsx frontend/src/app/cuenta/seguridad/page.tsx "frontend/src/app/cuenta/reservas/[id]/page.tsx"
git commit -m "refactor(frontend): adoptar reservas y seguridad al SettingsShell sin tocar su lógica"
```

---

## Task 7: `admin/layout.tsx` + `admin/page.tsx` (shim de redirección)

**Files:**
- Create: `frontend/src/app/admin/layout.tsx`
- Modify: `frontend/src/app/admin/page.tsx` (reescribir completo)

**Interfaces:**
- Consume: `SettingsShell` (Task 1).
- Produce: grupos que Task 8 da por sentado (`/admin/alertas`, `/admin/denuncias`, `/admin/credenciales`, `/admin/pagos`, `/admin/precios`, `/admin/tickets`, `/admin/salud`).

- [ ] **Step 1: Crear `admin/layout.tsx`**

```tsx
"use client";

import type { ReactNode } from "react";
import Cabecera from "@/components/Cabecera";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";

// Orden por el Artículo II de la Constitución (seguridad del menor primero),
// no por módulo ni alfabético: Alertas de kill-switch > Denuncias >
// Credenciales (bloquea a un Tutor de dar clases) > lo financiero > soporte
// general > infraestructura.
const GRUPOS: GrupoNavAjustes[] = [
  {
    titulo: "Seguridad",
    items: [
      { href: "/admin/alertas", label: "Alertas de Seguridad" },
      { href: "/admin/denuncias", label: "Denuncias" },
      { href: "/admin/credenciales", label: "Credenciales" },
    ],
  },
  {
    titulo: "Financiero",
    items: [
      { href: "/admin/pagos", label: "Pagos fallidos" },
      { href: "/admin/precios", label: "Precios regionales" },
    ],
  },
  {
    titulo: "Soporte",
    items: [{ href: "/admin/tickets", label: "Tickets de soporte" }],
  },
  {
    titulo: "Sistema",
    items: [{ href: "/admin/salud", label: "Salud de Infraestructura" }],
  },
];

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />
      <main className="mx-auto max-w-6xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Panel de Administración</h1>
        <p className="mt-1 text-sm text-slate-500">
          Esta sección solo responde si tu cuenta tiene rol de Admin — cada
          cola valida el permiso del lado del servidor.
        </p>
        <SettingsShell base="/admin" grupos={GRUPOS}>
          {children}
        </SettingsShell>
      </main>
    </>
  );
}
```

- [ ] **Step 2: Reescribir `admin/page.tsx` como shim de redirección** (decisión #2 arriba: Next.js exige un `page.tsx` en la raíz del segmento; en mobile el `SettingsShell` ya muestra la lista agrupada sin importar qué reciba como `children`, así que alcanza con no renderizar nada ahí)

```tsx
"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

const BREAKPOINT_LG = "(min-width: 1024px)";

/**
 * `/admin` ya no tiene contenido propio — cada sección vive en su propia
 * ruta (`/admin/alertas`, `/admin/denuncias`, etc.). En desktop redirige a
 * la primera sección por defecto (Artículo II: seguridad del menor
 * primero). En mobile no hace falta: `SettingsShell` ya muestra la lista
 * agrupada de secciones en la raíz del shell, sin importar qué reciba acá.
 */
export default function AdminPage() {
  const router = useRouter();

  useEffect(() => {
    if (window.matchMedia(BREAKPOINT_LG).matches) {
      router.replace("/admin/alertas");
    }
  }, [router]);

  return null;
}
```

- [ ] **Step 3: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: puede haber errores en las páginas de sección todavía no creadas (Task 8) si algo las referencia — no debería, porque este archivo no las importa. Confirmá que no hay errores originados en `admin/layout.tsx` ni `admin/page.tsx`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/admin/layout.tsx frontend/src/app/admin/page.tsx
git commit -m "refactor(frontend): /admin usa SettingsShell — la raíz redirige a la primera sección en desktop"
```

---

## Task 8: páginas de sección de `/admin`

**Files:**
- Create: `frontend/src/app/admin/alertas/page.tsx`
- Create: `frontend/src/app/admin/denuncias/page.tsx`
- Create: `frontend/src/app/admin/credenciales/page.tsx`
- Create: `frontend/src/app/admin/pagos/page.tsx`
- Create: `frontend/src/app/admin/precios/page.tsx`
- Create: `frontend/src/app/admin/tickets/page.tsx`
- Create: `frontend/src/app/admin/salud/page.tsx`

**Interfaces:** consume `ColaAlertas`, `ColaDenuncias`, `ColaCredenciales`, `ColaPagosFallidos`, `PreciosRegionales`, `TicketsSoporte` (todos sin props, sin cambios) y `AdminInfrastructurePanel` + `getPasarelaEstado`/`getSaludSistema`/`setPasarelaEstado`/`mensajeDeError`/`type SystemHealthDTO` (`@/lib/api`) para `salud/page.tsx` (lógica de `SaludTab` migrada tal cual).

- [ ] **Step 1: Las seis páginas simples** (mismo patrón: `<h2>` + componente sin props, sin `Tabs`/`PanelTab` porque la navegación ahora es por ruta real)

`frontend/src/app/admin/alertas/page.tsx`:
```tsx
"use client";

import ColaAlertas from "@/components/admin/ColaAlertas";

export default function AdminAlertasPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Alertas de Seguridad</h2>
      <div className="mt-4">
        <ColaAlertas />
      </div>
    </section>
  );
}
```

`frontend/src/app/admin/denuncias/page.tsx`:
```tsx
"use client";

import ColaDenuncias from "@/components/admin/ColaDenuncias";

export default function AdminDenunciasPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Denuncias</h2>
      <div className="mt-4">
        <ColaDenuncias />
      </div>
    </section>
  );
}
```

`frontend/src/app/admin/credenciales/page.tsx`:
```tsx
"use client";

import ColaCredenciales from "@/components/admin/ColaCredenciales";

export default function AdminCredencialesPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Credenciales</h2>
      <div className="mt-4">
        <ColaCredenciales />
      </div>
    </section>
  );
}
```

`frontend/src/app/admin/pagos/page.tsx`:
```tsx
"use client";

import ColaPagosFallidos from "@/components/admin/ColaPagosFallidos";

export default function AdminPagosPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Pagos fallidos</h2>
      <div className="mt-4">
        <ColaPagosFallidos />
      </div>
    </section>
  );
}
```

`frontend/src/app/admin/precios/page.tsx`:
```tsx
"use client";

import PreciosRegionales from "@/components/admin/PreciosRegionales";

export default function AdminPreciosPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Precios regionales</h2>
      <div className="mt-4">
        <PreciosRegionales />
      </div>
    </section>
  );
}
```

`frontend/src/app/admin/tickets/page.tsx`:
```tsx
"use client";

import TicketsSoporte from "@/components/admin/TicketsSoporte";

export default function AdminTicketsPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Tickets de soporte</h2>
      <div className="mt-4">
        <TicketsSoporte />
      </div>
    </section>
  );
}
```

- [ ] **Step 2: `admin/salud/page.tsx`** (lógica de `SaludTab` migrada tal cual desde el `admin/page.tsx` viejo)

```tsx
"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import AdminInfrastructurePanel from "@/components/admin/AdminInfrastructurePanel";
import { Alerta, Cargando } from "@/components/ui";
import {
  getPasarelaEstado,
  getSaludSistema,
  mensajeDeError,
  setPasarelaEstado,
  type SystemHealthDTO,
} from "@/lib/api";

export default function AdminSaludPage() {
  const [salud, setSalud] = useState<SystemHealthDTO | null>(null);
  const [pasarela, setPasarela] = useState<boolean | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    Promise.all([getSaludSistema(), getPasarelaEstado()])
      .then(([saludSistema, pasarelaEstado]) => {
        if (!activo) return;
        setSalud(saludSistema);
        setPasarela(pasarelaEstado.habilitada);
      })
      .catch((err) => {
        if (activo) setError(mensajeDeError(err, "No se pudo consultar el estado de infraestructura."));
      })
      .finally(() => {
        if (activo) setCargando(false);
      });
    return () => {
      activo = false;
    };
  }, []);

  async function alternarPasarela(habilitada: boolean) {
    const resultado = await setPasarelaEstado(habilitada);
    setPasarela(resultado.habilitada);
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Salud de Infraestructura</h2>
      <div className="mt-4">
        {cargando ? (
          <Cargando>Consultando infraestructura…</Cargando>
        ) : error || salud === null || pasarela === null ? (
          <Alerta tono="error" className="flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>
              {error ?? "La infraestructura no respondió. Volvé a intentar más tarde."}
            </span>
          </Alerta>
        ) : (
          <AdminInfrastructurePanel
            healthData={salud}
            isPaymentGatewayEnabled={pasarela}
            onTogglePaymentGateway={alternarPasarela}
          />
        )}
      </div>
    </section>
  );
}
```

- [ ] **Step 3: Verificar tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: sin errores.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/admin/alertas frontend/src/app/admin/denuncias frontend/src/app/admin/credenciales frontend/src/app/admin/pagos frontend/src/app/admin/precios frontend/src/app/admin/tickets frontend/src/app/admin/salud
git commit -m "feat(frontend): separar las 7 colas de /admin en rutas propias"
```

---

## Task 9: lint + tsc en limpio antes de tocar tests

**Files:** ninguno (solo verificación).

- [ ] **Step 1: Lint**

Run: `cd frontend && bun run lint`
Expected: sin errores/warnings nuevos. Si `oxlint` marca algo en los archivos tocados en las Tasks 1-8, corregilo antes de seguir.

- [ ] **Step 2: Tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: limpio.

- [ ] **Step 3: Arrancar el dev server y chusmear manualmente (smoke test)**

Run: `cd frontend && bun run dev &` (o similar en background), después visitar `/cuenta` y `/admin` en el navegador con una sesión falsa (o revisar que al menos no tire un error de render). Si no hay forma rápida de loguearse manualmente, saltear este paso y confiar en Playwright (Task 10-12).

---

## Task 10: actualizar Page Objects de `/cuenta`

**Files:**
- Modify: `frontend/tests/cuenta/cuenta-page.ts`

**Interfaces:**
- Produce: `CuentaPage.gotoPerfil()`, `.gotoAcceso()`, `.gotoMenores()` (reemplazan el único `.goto()` que asumía todo en una página) — usados por Tasks 11-12.

- [ ] **Step 1: Reescribir el Page Object**

```ts
import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class CuentaPage extends BasePage {
  readonly selectMenorBaja: Locator;
  readonly botonDarDeBaja: Locator;

  readonly campoEmail: Locator;
  readonly botonGuardarEmail: Locator;
  readonly campoPasswordActual: Locator;
  readonly campoPasswordNueva: Locator;
  readonly botonCambiarPassword: Locator;

  readonly selectTipoCredencial: Locator;
  readonly campoArchivoCredencial: Locator;
  readonly botonCargarCredencial: Locator;

  constructor(page: Page) {
    super(page);
    this.selectMenorBaja = page.getByLabel("Menor", { exact: true });
    this.botonDarDeBaja = page.getByRole("button", { name: "Dar de baja", exact: true });

    this.campoEmail = page.getByLabel("Email", { exact: true });
    this.botonGuardarEmail = page.getByRole("button", { name: "Guardar email" });
    this.campoPasswordActual = page.getByLabel("Contraseña actual", { exact: true });
    this.campoPasswordNueva = page.getByLabel("Contraseña nueva", { exact: true });
    this.botonCambiarPassword = page.getByRole("button", { name: "Cambiar contraseña" });

    this.selectTipoCredencial = page.getByLabel("Tipo de documento", { exact: true });
    this.campoArchivoCredencial = page.getByLabel("Archivo", { exact: true });
    this.botonCargarCredencial = page.getByRole("button", { name: "Cargar credencial" });
  }

  /** Perfil (raíz del shell): info de cuenta, capacidades y credencial del Tutor. */
  async goto(): Promise<void> {
    await super.goto("/cuenta");
  }

  /** Acceso: email y contraseña. */
  async gotoAcceso(): Promise<void> {
    await super.goto("/cuenta/acceso");
  }

  /** Menores a cargo del Adulto Responsable: alta, solicitudes, baja. */
  async gotoMenores(): Promise<void> {
    await super.goto("/cuenta/menores");
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/tests/cuenta/cuenta-page.ts
git commit -m "test(frontend): CuentaPage expone goto() por sección para las nuevas rutas"
```

---

## Task 11: actualizar specs de `/cuenta` a las nuevas rutas

**Files:**
- Modify: `frontend/tests/cuenta/cuenta.spec.ts` (baja de menor)
- Modify: `frontend/tests/cuenta/editar-cuenta.spec.ts` (email/contraseña)

**Interfaces:** consume `CuentaPage.gotoMenores()` y `.gotoAcceso()` (Task 10).

- [ ] **Step 1: `cuenta.spec.ts`** — reemplazar las tres llamadas `await cuenta.goto();` por `await cuenta.gotoMenores();` (los tres tests del describe "Cuenta — baja de menor" usan `selectMenorBaja`/`botonDarDeBaja`, que ahora viven en `/cuenta/menores`). El resto del archivo (mocks, asserts) no cambia.

- [ ] **Step 2: `editar-cuenta.spec.ts`** — reemplazar las cuatro llamadas `await cuenta.goto();` por `await cuenta.gotoAcceso();` (los cuatro tests usan `campoEmail`/`botonGuardarEmail`/`campoPasswordActual`/`campoPasswordNueva`/`botonCambiarPassword`, que ahora viven en `/cuenta/acceso`). Ojo con el test `"la contraseña actual incorrecta muestra el error sin desloguear"`: el assert final es

```ts
await expect(page).toHaveURL(/\/cuenta$/);
```

  y con la nueva ruta debe verificar que seguimos en Acceso, no que un 401 no nos mandó a `/login`:

```ts
await expect(page).toHaveURL(/\/cuenta\/acceso$/);
```

- [ ] **Step 3: `capacidades.spec.ts` y `credencial-tutor.spec.ts`** — no requieren cambios: ambos siguen operando sobre `/cuenta` (raíz = Perfil), que sigue teniendo el checkbox de Capacidades (cuando `perfil.tipo === "ADULTO"`) y el `BannerCredencial` (cuando `payload.tipo === "TUTOR"`). Confirmalo leyendo los archivos, no hace falta tocarlos.

- [ ] **Step 4: Correr la suite de `/cuenta`**

Run: `cd frontend && npx playwright test tests/cuenta/`
Expected: todos los tests en verde.

- [ ] **Step 5: Commit**

```bash
git add frontend/tests/cuenta/cuenta.spec.ts frontend/tests/cuenta/editar-cuenta.spec.ts
git commit -m "test(frontend): apuntar los specs de /cuenta a /cuenta/menores y /cuenta/acceso"
```

---

## Task 12: actualizar specs de `/admin` a las nuevas rutas

**Files:**
- Modify: `frontend/tests/admin/admin.spec.ts`
- Modify: `frontend/tests/admin/tickets.spec.ts`

**Interfaces:** ninguna nueva — navegación directa por URL, sin Page Object (mismo estilo que ya usan estos dos archivos).

- [ ] **Step 1: Reescribir `admin.spec.ts`**

El test viejo cubría el componente `Tabs` (rol `tablist`, flechas de teclado) — ese componente ya no se usa en `/admin` (la navegación es por `<Link>`s reales en `SettingsShell`, no por un `role="tablist"` en memoria). Se reemplaza por un test que cubre lo que sí es nuevo acá: la sección activa se resalta con `aria-current="page"` y cambia al navegar.

```ts
import { test, expect } from "@playwright/test";
import { setFakeSession } from "../helpers";

/**
 * Cubre la navegación del `SettingsShell` en su hábitat real: la sección
 * activa se resalta (`aria-current="page"`) según la URL, no según un
 * estado de tabs en memoria — así el botón atrás/adelante del navegador
 * funciona nativo. Reemplaza al viejo test de `Tabs` (`role="tablist"`),
 * que ya no aplica: `/admin` navega por rutas reales, no por tabs en
 * memoria.
 */
test.describe("Panel de Administración — navegación del SettingsShell", () => {
  test(
    "la sección activa se resalta según la URL y cambia al navegar",
    { tag: ["@a11y", "@TABS-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await page.goto("/admin/alertas");

      const nav = page.getByRole("navigation", { name: "Secciones" });
      const linkAlertas = nav.getByRole("link", { name: "Alertas de Seguridad" });
      const linkDenuncias = nav.getByRole("link", { name: "Denuncias" });

      await expect(linkAlertas).toHaveAttribute("aria-current", "page");
      await expect(linkDenuncias).not.toHaveAttribute("aria-current", "page");

      await linkDenuncias.click();

      await expect(page).toHaveURL(/\/admin\/denuncias$/);
      await expect(linkDenuncias).toHaveAttribute("aria-current", "page");
      await expect(linkAlertas).not.toHaveAttribute("aria-current", "page");
    }
  );
});
```

- [ ] **Step 2: `tickets.spec.ts`** — reemplazar, en los dos tests, estas dos líneas:

```ts
      await page.goto("/admin");
      await page.getByRole("tab", { name: "Tickets de soporte" }).click();
```

por:

```ts
      await page.goto("/admin/tickets");
```

El resto de cada test (mocks, asserts) no cambia.

- [ ] **Step 3: Correr la suite de `/admin`**

Run: `cd frontend && npx playwright test tests/admin/`
Expected: todos los tests en verde.

- [ ] **Step 4: Commit**

```bash
git add frontend/tests/admin/admin.spec.ts frontend/tests/admin/tickets.spec.ts
git commit -m "test(frontend): apuntar los specs de /admin a las rutas por sección y probar el nuevo nav"
```

---

## Task 13: verificación final de punta a punta

**Files:** ninguno.

- [ ] **Step 1: Lint**

Run: `cd frontend && bun run lint`
Expected: limpio.

- [ ] **Step 2: Tipos**

Run: `cd frontend && npx tsc --noEmit`
Expected: limpio.

- [ ] **Step 3: Suite completa de `/cuenta` y `/admin`**

Run: `cd frontend && npx playwright test tests/cuenta/ tests/admin/`
Expected: todos verdes.

- [ ] **Step 4: Suite completa del frontend (regresión — nada fuera de alcance debería romperse)**

Run: `cd frontend && bun run test:e2e`
Expected: verde. Si algo fuera de `/cuenta`/`/admin` rompe, es señal de que algún cambio de esta tarea tuvo un efecto colateral no previsto (ej. una ruta compartida) — investigar antes de dar por cerrada la tarea, no ignorar.

- [ ] **Step 5: Commit final si quedó algo suelto**

Si Steps 1-4 obligaron a tocar algo no cubierto por un commit anterior, commitealo ahora con un mensaje conventional commit describiendo el ajuste puntual (ej. `fix(frontend): ...`).
