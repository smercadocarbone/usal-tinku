"use client";

import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { formatearPrecioConMoneda } from "@/lib/formatos";
import { Alerta, Boton, Cargando, Tarjeta } from "@/components/ui";
import FormularioSoporte from "@/components/FormularioSoporte";

interface Preferencia {
  preferenciaId: string;
  initPoint: string;
  bypass: boolean;
}

interface ReservaInfo {
  id: string;
  precio: number | null;
}

function PagarForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const reservaId = searchParams.get("reserva");

  const [estado, setEstado] = useState<"cargando" | "listo" | "error" | "finalizado">(
    "cargando"
  );
  const [error, setError] = useState<string | null>(null);
  const [preferencia, setPreferencia] = useState<Preferencia | null>(null);
  const [reserva, setReserva] = useState<ReservaInfo | null>(null);

  function cargar() {
    if (!reservaId) return;
    setEstado("cargando");
    setError(null);
    api
      .get<ReservaInfo>(`/api/reservas/${reservaId}`)
      .then((r) => setReserva(r))
      .catch(() => {})
      .then(() =>
        api
          .post<Preferencia>("/api/pagos/preferencia", { reservaId })
          .then((p) => {
            setPreferencia(p);
            setEstado("listo");
          })
      )
      .catch((err) => {
        setEstado("error");
        setError(
          err instanceof ApiError
            ? err.message
            : "No se pudo generar el pago."
        );
      });
  }

  useEffect(() => {
    // Sin reserva en la URL no hay nada que pagar: no dejar un error rojo con
    // un Reintentar que no reintenta nada. Redirigir con un mensaje neutro
    // (B9).
    if (!reservaId) {
      router.replace("/cuenta/reservas");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reservaId]);

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reservaId]);

  function irAPagar() {
    if (!preferencia) return;
    setEstado("finalizado");
    if (preferencia.bypass) return;
    window.location.assign(preferencia.initPoint);
  }

  function esBypass() {
    return preferencia?.bypass ?? false;
  }

  return (
    <main className="mx-auto max-w-2xl px-5 py-8">
      <h1 className="text-xl tracking-tight">
        Pago de la reserva
      </h1>

      {estado === "cargando" && <Cargando>Generando pago...</Cargando>}

      {estado === "listo" && preferencia && (
        <>
          <Tarjeta className="mb-4 w-full max-w-none p-8">
            <dl className="m-0">
              {reserva && reserva.precio !== null && (
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Monto</dt>
                  <dd className="m-0 text-right">
                    {formatearPrecioConMoneda(reserva.precio)}
                  </dd>
                </div>
              )}
              <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                <dt className="font-semibold">Metodo</dt>
                <dd className="m-0 text-right">
                  {esBypass() ? "Pago simulado" : "MercadoPago"}
                </dd>
              </div>
            </dl>
          </Tarjeta>
          {esBypass() && (
            <Alerta tono="aviso" rol="alert" className="mb-4 w-full">
              La pasarela de pagos está deshabilitada: tu reserva se confirmará
              sin procesar un cobro real. No se debitará ningún monto.
            </Alerta>
          )}
          <Boton onClick={irAPagar}>
            {esBypass() ? "Confirmar reserva (simulado)" : "Pagar con MercadoPago"}
          </Boton>
          {!esBypass() && (
            <p className="text-xs text-slate-500">
              Vas a salir de Tinku y continuar en el sitio de MercadoPago.
            </p>
          )}
        </>
      )}

      {estado === "finalizado" && (
        <Alerta tono="aviso" className="w-fit">
          {esBypass()
            ? "Reserva confirmada en modo simulado. Volvé a mis reservas."
            : "Redirigiendo a MercadoPago..."}
        </Alerta>
      )}

      {estado === "error" && (
        <Alerta tono="error">
          {error}
          <Boton
            variante="secundario"
            tamano="sm"
            className="mt-3 flex"
            onClick={cargar}
          >
            Reintentar
          </Boton>
          {reservaId && (
            <FormularioSoporte
              origenModulo="M5.pago_fallido"
              asunto={`No se pudo generar el pago de la reserva ${reservaId}`}
              detalleInicial={`Reserva ${reservaId}: ${error ?? "no se pudo generar el pago."}`}
            />
          )}
        </Alerta>
      )}

      <p className="mt-5 text-center text-sm text-slate-500">
        <Link href="/cuenta/reservas">Volver a mis reservas</Link>
      </p>
    </main>
  );
}

export default function PagarPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-2xl px-5 py-8">Cargando...</div>}>
      <PagarForm />
    </Suspense>
  );
}