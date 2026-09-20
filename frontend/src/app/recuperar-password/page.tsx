"use client";

import { useState } from "react";
import Link from "next/link";
import { solicitarResetPassword } from "@/lib/api";
import { Alerta, Boton, Campo, Tarjeta } from "@/components/ui";

export default function RecuperarPasswordPage() {
  const [dni, setDni] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    try {
      await solicitarResetPassword(dni);
    } finally {
      // El backend responde 204 exista o no el DNI (no hay que distinguir
      // el caso de error): mostrar siempre el mismo mensaje de éxito.
      setEnviando(false);
      setEnviado(true);
    }
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <Tarjeta className="w-full max-w-sm p-8">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <h1 className="mb-1 text-xl tracking-tight">Recuperar contraseña</h1>
        <p className="mb-6 text-slate-500">
          Ingresá tu DNI y te vamos a enviar un enlace para elegir una contraseña nueva.
        </p>

        {enviado ? (
          <Alerta tono="exito">
            Si el DNI está registrado, vas a recibir un enlace de recuperación en breve.
          </Alerta>
        ) : (
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

            <Boton type="submit" cargando={enviando} textoCargando="Enviando…">
              Enviar enlace de recuperación
            </Boton>
          </form>
        )}

        <p className="mt-5 text-center text-sm text-slate-500">
          <Link href="/login">Volver a iniciar sesión</Link>
        </p>
      </Tarjeta>
    </main>
  );
}
