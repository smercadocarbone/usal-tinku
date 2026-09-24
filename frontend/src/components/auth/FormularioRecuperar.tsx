"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { MailCheck } from "lucide-react";
import { mensajeDeError, solicitarResetPassword } from "@/lib/api";
import { Alerta, Boton, Campo, clasesBoton } from "@/components/ui";

/**
 * "Olvidé mi contraseña" (FASE2-03, ADR-000-06). La respuesta es la misma exista o no
 * el DNI (FR-ID-018), así que la pantalla tampoco lo confirma.
 */
export default function FormularioRecuperar() {
  const [dni, setDni] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      await solicitarResetPassword(dni);
      setEnviado(true);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos procesar el pedido. Probá de nuevo en unos minutos."));
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return (
      <div className="mt-6 flex flex-col gap-6" role="status">
        <Alerta tono="exito" titulo="Revisá tu email">
          Si ese DNI tiene una cuenta con email, te mandamos un enlace para elegir una contraseña nueva. Vence en 1
          hora y sirve una sola vez. Si no lo ves, fijate en correo no deseado.
        </Alerta>
        <p className="flex gap-2.5 text-sm text-tinta-suave">
          <MailCheck className="size-5 shrink-0 text-marca-700" aria-hidden />
          ¿No te llega? Si tu cuenta no tiene email cargado, escribinos a soporte de Tinku y te ayudamos.
        </p>
        <Link href="/login" className={clasesBoton("secundario", "lg", "w-full")}>
          Volver a ingresar
        </Link>
      </div>
    );
  }

  return (
    <form className="mt-6 flex flex-col gap-5" onSubmit={onSubmit}>
      <Campo
        id="dni"
        etiqueta="DNI"
        variante="dni"
        autoComplete="username"
        required
        value={dni}
        onValor={setDni}
        placeholder="12.345.678"
      />
      {error && <Alerta tono="peligro">{error}</Alerta>}
      <Boton type="submit" tamano="lg" anchoCompleto cargando={enviando} textoCargando="Enviando…">
        Mandarme el enlace
      </Boton>
      <Link href="/login" className={clasesBoton("fantasma", "lg", "w-full")}>
        Volver a ingresar
      </Link>
    </form>
  );
}
