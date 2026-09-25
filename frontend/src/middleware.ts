import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { COOKIE_NAME } from "@/lib/auth";

/**
 * Redirección de UX, NO un control de seguridad (AUD-016, decidido en FASE 3): sin cookie
 * de sesión → /login, para no mostrar un shell vacío. No valida firma ni expiración: una
 * cookie inventada solo carga el esqueleto de la página, sin datos. La autorización real
 * la hace el backend en cada request (JWT firmado + credentials_version). Verificar la
 * firma acá obligaría a repartir el secreto del JWT al frontend, sin ganar protección.
 * La cookie es la "copia" del token que setSession() escribe en el login (ver src/lib/auth.ts).
 */
export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get(COOKIE_NAME)?.value;

  if (!token) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    // La query se preserva en `siguiente` (ej. /buscar?materia=Física): sin eso, el
    // usuario vuelve del login a una búsqueda vacía.
    url.search = "";
    url.searchParams.set("siguiente", pathname + request.nextUrl.search);
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/cuenta/:path*",
    "/buscar/:path*",
    "/tutores/:path*",
    "/reservar/:path*",
    "/pagar/:path*",
    "/aula/:path*",
    "/admin/:path*",
  ],
};