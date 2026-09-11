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
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <div className="w-full max-w-[26rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <div className="mb-6 text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>
        <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Iniciar sesión</h1>
        <p className="mb-6 text-texto-suave">Ingresá con tu DNI para acceder a tu cuenta.</p>

        {expirado && (
          <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
            Tu sesión expiró. Inicio sesión de nuevo para continuar.
          </div>
        )}

        {registrado && (
          <div className="mb-4 rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito" role="status">
            Cuenta creada. Ya podés iniciar sesión.
          </div>
        )}

        {tutorRegistrado && (
          <div className="mb-4 rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito" role="status">
            Cuenta de tutor creada. Iniciá sesión con tu DNI y contraseña.
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={onSubmit}>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="dni" className="text-[0.85rem] font-semibold">DNI</label>
            <input
              id="dni"
              type="text"
              inputMode="numeric"
              autoComplete="username"
              required
              value={dni}
              onChange={(e) => setDni(e.target.value)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="password" className="text-[0.85rem] font-semibold">Contraseña</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
              {error}
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={enviando}
          >
            {enviando ? "Ingresando…" : "Ingresar"}
          </button>
        </form>

        <p className="mt-5 text-center text-[0.9rem] text-texto-suave">
          ¿No tenés cuenta? <Link href="/registro">Registrate</Link>
        </p>
      </div>
    </main>
  );
}