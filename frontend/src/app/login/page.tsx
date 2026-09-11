"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { setSession } from "@/lib/auth";

interface TokenResponse {
  token: string;
  tipo: string;
  expiresInMinutes: number;
}

export default function LoginPage() {
  const router = useRouter();
  const [dni, setDni] = useState("");
  const [password, setPassword] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const query = typeof window !== "undefined" ? new URLSearchParams(window.location.search) : null;
  const expirado = query?.get("expirado") === "1";
  const registrado = query?.get("registrado") === "1";
  const tutorRegistrado = query?.get("tutorRegistrado") === "1";

  function destinoSiguiente(): string {
    const siguiente = query?.get("siguiente");
    return siguiente && siguiente.startsWith("/")
      ? siguiente
      : "/cuenta";
  }

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setError(null);

    try {
      const res = await api.post<TokenResponse>("/api/usuarios/login", {
        dni,
        password,
      });
      setSession(res.token, res.expiresInMinutes);
      router.replace(destinoSiguiente());
    } catch (err) {
      setError(
        err instanceof ApiError && err.message
          ? err.message
          : "No se pudo iniciar sesión. Intentá de nuevo."
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="pantalla">
      <div className="tarjeta">
        <div className="marca">
          Tinku<span>.</span>
        </div>
        <h1>Iniciar sesión</h1>
        <p>Ingresá con tu DNI para acceder a tu cuenta.</p>

        {expirado && (
          <div className="alerta alerta--informativa" role="status">
            Tu sesión expiró. Inicio sesión de nuevo para continuar.
          </div>
        )}

        {registrado && (
          <div className="alerta alerta--exito" role="status">
            Cuenta creada. Ya podés iniciar sesión.
          </div>
        )}

        {tutorRegistrado && (
          <div className="alerta alerta--exito" role="status">
            Cuenta de tutor creada. Iniciá sesión con tu DNI y contraseña.
          </div>
        )}

        <form className="formulario" onSubmit={onSubmit}>
          <div className="campo">
            <label htmlFor="dni">DNI</label>
            <input
              id="dni"
              type="text"
              inputMode="numeric"
              autoComplete="username"
              required
              value={dni}
              onChange={(e) => setDni(e.target.value)}
            />
          </div>

          <div className="campo">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <div className="alerta alerta--error" role="alert">
              {error}
            </div>
          )}

          <button type="submit" className="boton" disabled={enviando}>
            {enviando ? "Ingresando…" : "Ingresar"}
          </button>
        </form>

        <p className="pie-enlace">
          ¿No tenés cuenta? <Link href="/registro">Registrate</Link>
        </p>
      </div>
    </main>
  );
}