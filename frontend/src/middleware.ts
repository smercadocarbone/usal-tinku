import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { COOKIE_NAME } from "@/lib/auth";

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
    url.searchParams.set("siguiente", pathname);
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
}

export const config = {
  matcher: ["/cuenta/:path*"],
};