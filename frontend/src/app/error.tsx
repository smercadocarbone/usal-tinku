"use client";

import { useEffect } from "react";
import Link from "next/link";
import Logo from "@/components/Logo";
import { Alerta, Boton, Tarjeta, clasesBoton } from "@/components/ui";

/**
 * Límite de error de las rutas. Sin esto, cualquier excepción de render deja
 * la pantalla en blanco: el usuario no sabe si se rompió la app, si perdió la
 * sesión, o si su reserva se llegó a crear.
 *
 * `digest` es el hash que Next le asigna al error en el server — es lo único
 * que se le puede pedir a un usuario para correlacionar con los logs, porque
 * el stack real nunca viaja al cliente en producción.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Punto de enganche del rastreador de errores (Sentry u otro) cuando se
    // decida el proveedor: hoy no hay ninguno, así que al menos queda en la
    // consola del navegador en vez de perderse del todo.
    console.error("[tinku] error de ruta:", error);
  }, [error]);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <Tarjeta className="w-full max-w-lg p-8">
        <div className="mb-6 flex justify-center">
          <Logo />
        </div>
        <h1 className="mb-1 text-2xl font-bold text-center">Algo se rompió de nuestro lado</h1>
        <p className="mb-6 text-center text-tinta-suave">
          No es culpa tuya. Podés reintentar; si el problema sigue, volvé al
          inicio y escribinos.
        </p>

        {error.digest && (
          <Alerta tono="info" className="mb-4">
            Código de referencia: <code className="font-mono">{error.digest}</code>
          </Alerta>
        )}

        <div className="flex flex-wrap justify-center gap-3">
          <Boton onClick={reset}>Reintentar</Boton>
          <Link href="/" className={clasesBoton("secundario")}>
            Ir al inicio
          </Link>
        </div>
      </Tarjeta>
    </main>
  );
}
