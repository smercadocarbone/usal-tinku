import type { PayloadSesion } from "./auth";

export type IconoNav = "buscar" | "clases" | "chicos" | "cuenta" | "agenda" | "perfil" | "panel" | "cobros";

export interface ItemNav {
  href: string;
  label: string;
  icono: IconoNav;
  /** Rutas que también marcan este ítem como activo. */
  tambien?: string[];
}

/**
 * Navegación principal por rol (UX-01 §4). La misma lista alimenta la cabecera en
 * desktop y la barra inferior en mobile: nunca divergen.
 */
export function navegacionPorRol(payload: PayloadSesion | null | undefined, esAdmin: boolean): ItemNav[] {
  const cuenta: ItemNav = { href: "/cuenta", label: "Mi cuenta", icono: "cuenta", tambien: ["/cuenta/acceso", "/cuenta/seguridad"] };
  if (!payload) return [];

  if (esAdmin) {
    return [{ href: "/admin", label: "Panel", icono: "panel", tambien: ["/admin/"] }, cuenta];
  }

  if (payload.tipo === "TUTOR") {
    // Un Tutor puede además tomar clases o tener chicos a cargo (ADR-M1-07): esos accesos van
    // en "Mi cuenta" para no pasar de 5 ítems en la barra de abajo del celular.
    return [
      { href: "/cuenta/horarios", label: "Mi agenda", icono: "agenda" },
      { href: "/cuenta/reservas", label: "Mis clases", icono: "clases", tambien: ["/cuenta/reservas/"] },
      { href: "/cuenta/cobros", label: "Cobros", icono: "cobros" },
      // Mi cuenta incluye su perfil de tutor (presentación, materias, precio, credenciales, menores).
      {
        ...cuenta,
        tambien: [
          ...(cuenta.tambien ?? []),
          "/cuenta/perfil",
          "/cuenta/presentacion",
          "/cuenta/materias",
          "/cuenta/precio",
          "/cuenta/credenciales",
          "/cuenta/clases-con-menores",
        ],
      },
    ];
  }

  const items: ItemNav[] = [
    { href: "/buscar", label: "Buscar", icono: "buscar", tambien: ["/tutores/", "/reservar"] },
    { href: "/cuenta/reservas", label: "Mis clases", icono: "clases", tambien: ["/cuenta/reservas/", "/pagar"] },
  ];
  if (payload.tipo !== "MENOR" && payload.cap_ar === true) {
    items.push({ href: "/cuenta/menores", label: "Mis chicos", icono: "chicos" });
  }
  items.push(cuenta);
  return items;
}

export function itemActivo(item: ItemNav, pathname: string): boolean {
  if (pathname === item.href) return true;
  return (item.tambien ?? []).some((p) => (p.endsWith("/") ? pathname.startsWith(p) : pathname === p));
}

/** Pantallas de "Mi cuenta" que solo usa un Tutor. */
const RUTAS_SOLO_TUTOR = [
  "/cuenta/presentacion",
  "/cuenta/materias",
  "/cuenta/precio",
  "/cuenta/credenciales",
  "/cuenta/clases-con-menores",
  "/cuenta/horarios",
  "/cuenta/cobros",
  "/cuenta/perfil-tutor",
];

/** Lo único de "Mi cuenta" que ve un Menor: su perfil, su acceso, sus clases y sus avisos. */
const RUTAS_DEL_MENOR = ["/cuenta", "/cuenta/perfil", "/cuenta/acceso", "/cuenta/reservas", "/cuenta/notificaciones"];

function coincide(pathname: string, ruta: string): boolean {
  return pathname === ruta || pathname.startsWith(ruta + "/");
}

/**
 * T3-03 / T7-M3 (pruebas en producción): qué pantallas de "Mi cuenta" puede abrir cada perfil.
 * Es UX y defensa en profundidad: la API ya rechaza lo que no corresponde.
 */
export function rutaCuentaPermitida(pathname: string, payload: PayloadSesion | null | undefined): boolean {
  if (!payload) return true; // sin sesión decide el proxy (redirige al login)
  if (payload.tipo === "MENOR") {
    return RUTAS_DEL_MENOR.some((r) => (r === "/cuenta" ? pathname === r : coincide(pathname, r)));
  }
  if (payload.tipo !== "TUTOR" && RUTAS_SOLO_TUTOR.some((r) => coincide(pathname, r))) return false;
  return true;
}

/** ¿La pantalla depende del perfil? Esas esperan a conocer la sesión antes de montarse (y de pedir datos). */
export function rutaCuentaRestringida(pathname: string): boolean {
  return [...RUTAS_SOLO_TUTOR, "/cuenta/menores", "/cuenta/seguridad"].some((r) => coincide(pathname, r));
}
