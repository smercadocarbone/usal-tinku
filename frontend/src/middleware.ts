import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { COOKIE_NAME } from "@/lib/auth";

// FIXME AUD-016 (auditoría 2026-09-21): esto NO es una protección de seguridad. Solo verifica
// que la cookie exista: no valida firma ni expiración. Cualquiera puede setear
// document.cookie = "tinku_jwt=x" y cargar el shell de /admin. La autorización real la hace
// el backend en cada request. Se decide en FASE 3: verificar la firma, o renombrar esto
// honestamente como redirección de UX.
/**
 * Protección de rutas desde el server: sin cookie de sesión → /login.
 * La cookie es la "copia" del token que setSession() escribe en el login
 * (misma clave que en localStorage, ver src/lib/auth.ts).
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