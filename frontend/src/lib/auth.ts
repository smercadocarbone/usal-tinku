/**
 * Manejo de sesión del frontend.
 *
 * El token vive en DOS lugares, por dos motivos distintos:
 *  - `localStorage`: fuente de verdad para el cliente HTTP (`src/lib/api.ts`),
 *    que lo manda como header `Authorization: Bearer`.
 *  - Cookie (no httpOnly, misma clave): para que `middleware.ts` pueda
 *    proteger rutas desde el server y redirigir a /login sin JS.
 * Ambas se limpian juntas en logout/expiración (ver `clearSession`).
 */

export const TOKEN_KEY = "tinku_jwt";
export const COOKIE_NAME = "tinku_jwt";

export interface PayloadSesion {
  sub?: string;
  tipo?: "ADULTO" | "MENOR" | "TUTOR";
  cap_est?: boolean;
  cap_ar?: boolean;
  exp?: number;
}

export interface Sesion {
  token: string;
  payload: PayloadSesion;
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

export function setSession(token: string, expiresInMinutes: number): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TOKEN_KEY, token);
  const maxAge = Math.max(0, Math.floor(expiresInMinutes * 60));
  document.cookie = `${COOKIE_NAME}=${encodeURIComponent(token)}; Path=/; SameSite=Lax; Max-Age=${maxAge}`;
}

export function clearSession(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  document.cookie = `${COOKIE_NAME}=; Path=/; SameSite=Lax; Max-Age=0`;
}

function decodePayload(token: string): PayloadSesion | null {
  try {
    const segment = token.split(".")[1];
    if (!segment) return null;
    const base64 = segment.replace(/-/g, "+").replace(/_/g, "/");
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, "=");
    return JSON.parse(window.atob(padded)) as PayloadSesion;
  } catch {
    return null;
  }
}

export function getSession(): Sesion | null {
  const token = getToken();
  if (!token) return null;
  return { token, payload: decodePayload(token) ?? {} };
}

/** La sesión está viva (JWT sin expirar). Si no existe o expiró, la limpia. */
export function isAuthenticated(): boolean {
  const session = getSession();
  if (!session) return false;
  const exp = session.payload.exp;
  if (exp !== undefined && exp * 1000 <= Date.now()) {
    clearSession();
    return false;
  }
  return true;
}