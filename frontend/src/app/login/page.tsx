"use client";

import { useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { setSession } from "@/lib/auth";
import { Alerta, Boton, Campo, Tarjeta } from "@/components/ui";

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

  // B3: leer window.location en el render produce mismatch de hidratación
  // (el servidor pinta sin query → el cliente con query). Con
  // useSyncExternalStore el snapshot de servidor es estable ("") y recién en
  // el cliente se lee el query string, sin error de hidratación.
  const queryParams = useSyncExternalStore(
    () => () => {},
    () => window.location.search,
    () => "",
  );
  const query = new URLSearchParams(queryParams);
  const expirado = query.get("expirado") === "1";
  const registrado = query.get("registrado") === "1";
  const tutorRegistrado = query.get("tutorRegistrado") === "1";

  function destinoSiguiente(): string {
    const siguiente = query.get("siguiente");
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
      <Tarjeta className="w-full max-w-sm p-8">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <h1 className="mb-1 text-xl tracking-tight">Iniciar sesión</h1>
        <p className="mb-6 text-slate-500">Ingresá con tu DNI para acceder a tu cuenta.</p>

        {expirado && (
          <Alerta tono="aviso" className="mb-4">
            Tu sesión expiró. Iniciá sesión de nuevo para continuar.
          </Alerta>
        )}

        {registrado && (
          <Alerta tono="exito" className="mb-4">
            Cuenta creada. Ya podés iniciar sesión.
          </Alerta>
        )}

        {tutorRegistrado && (
          <Alerta tono="exito" className="mb-4">
            Cuenta de tutor creada. Iniciá sesión con tu DNI y contraseña.
          </Alerta>
        )}

        <form className="flex flex-col gap-4" onSubmit={onSubmit}>
          <Campo
            id="dni"
            etiqueta="DNI"
            type="text"
            inputMode="numeric"
            autoComplete="username"
            required
            value={dni}
            onChange={(e) => setDni(e.target.value)}
          />

          <Campo
            id="password"
            etiqueta="Contraseña"
            type="password"
            autoComplete="current-password"
            required
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          {error && <Alerta tono="error">{error}</Alerta>}

          <Boton type="submit" cargando={enviando} textoCargando="Ingresando…">
            Ingresar
          </Boton>
        </form>

        <p className="mt-4 text-center text-sm text-slate-500">
          <Link href="/recuperar-password">¿Olvidaste tu contraseña?</Link>
        </p>

        <p className="mt-2 text-center text-sm text-slate-500">
          ¿No tenés cuenta? <Link href="/registro">Regístrate</Link>
        </p>
      </Tarjeta>
    </main>
  );
}