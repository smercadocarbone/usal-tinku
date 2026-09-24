import { api } from "./api";
import { setSession } from "./auth";

interface TokenResponse {
  token: string;
  tipo: string;
  expiresInMinutes: number;
}

/** Login por DNI + contraseña; deja la sesión guardada y devuelve el token. */
export async function iniciarSesion(dni: string, password: string): Promise<string> {
  const res = await api.post<TokenResponse>("/api/usuarios/login", { dni, password });
  setSession(res.token, res.expiresInMinutes);
  return res.token;
}

/** `siguiente` de la URL solo si es una ruta interna (nunca un redirect abierto). */
export function siguienteSeguro(valor: string | null | undefined): string | null {
  return valor && valor.startsWith("/") && !valor.startsWith("//") ? valor : null;
}
