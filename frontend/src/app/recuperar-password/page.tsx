"use client";

import Link from "next/link";
import { Alerta, Tarjeta } from "@/components/ui";

/**
 * El envío de emails todavía no existe (AUD-008/014, P5): el backend solo
 * escribe el token de recuperación en el log. Prometer un enlace que nunca
 * sale es mentirle al usuario, así que el flujo queda deshabilitado con una
 * explicación honesta hasta que exista un canal de notificación real.
 */
export default function RecuperarPasswordPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <Tarjeta className="w-full max-w-sm p-8">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <h1 className="mb-1 text-xl tracking-tight">Recuperar contraseña</h1>
        <p className="mb-6 text-slate-500">
          ¿No podés entrar a tu cuenta?
        </p>

        <Alerta tono="aviso">
          La recuperación automática por email todavía no está disponible. Si
          no podés entrar a tu cuenta, escribinos a soporte de Tinku y un
          administrador te ayuda a recuperarla.
        </Alerta>

        <p className="mt-5 text-center text-sm text-slate-500">
          <Link href="/login">Volver a iniciar sesión</Link>
        </p>
      </Tarjeta>
    </main>
  );
}