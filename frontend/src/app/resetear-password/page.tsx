"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, resetearPassword } from "@/lib/api";
import { Alerta, Boton, Campo, Tarjeta } from "@/components/ui";

export default function ResetearPasswordPage() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState(false);

  const query = typeof window !== "undefined" ? new URLSearchParams(window.location.search) : null;
  const token = query?.get("token") ?? "";

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (password !== confirmacion) {
      setError("Las contraseñas no coinciden.");
      return;
    }
    if (!token) {
      setError("El enlace no es válido. Solicitá uno nuevo.");
      return;
    }

    setEnviando(true);
    try {
      await resetearPassword(token, password);
      setExito(true);
      setTimeout(() => router.replace("/login"), 2000);
    } catch (err) {
      setError(
        err instanceof ApiError && err.message
          ? err.message
          : "No se pudo cambiar la contraseña. Intentá de nuevo."
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
        <h1 className="mb-1 text-xl tracking-tight">Elegí una contraseña nueva</h1>

        {!token && (
          <Alerta tono="error" className="mt-4">
            Este enlace no es válido.{" "}
            <Link href="/recuperar-password">Pedí uno nuevo</Link>.
          </Alerta>
        )}

        {exito ? (
          <Alerta tono="exito" className="mt-4">
            Tu contraseña se actualizó. Te llevamos a iniciar sesión…
          </Alerta>
        ) : (
          token && (
            <form className="mt-6 flex flex-col gap-4" onSubmit={onSubmit}>
              <Campo
                id="passwordNueva"
                etiqueta="Contraseña nueva"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <Campo
                id="passwordConfirmacion"
                etiqueta="Repetí la contraseña"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={confirmacion}
                onChange={(e) => setConfirmacion(e.target.value)}
              />

              {error && <Alerta tono="error">{error}</Alerta>}

              <Boton type="submit" cargando={enviando} textoCargando="Guardando…">
                Guardar contraseña nueva
              </Boton>
            </form>
          )
        )}
      </Tarjeta>
    </main>
  );
}
