"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";

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
  const searchParams = useSearchParams();
  const reservaId = searchParams.get("reserva");

  const [estado, setEstado] = useState<"cargando" | "listo" | "error" | "finalizado">(
    "cargando"
  );
  const [error, setError] = useState<string | null>(null);
  const [preferencia, setPreferencia] = useState<Preferencia | null>(null);
  const [reserva, setReserva] = useState<ReservaInfo | null>(null);

  function cargar() {
    if (!reservaId) {
      setEstado("error");
      setError("Falta la reserva a pagar.");
      return;
    }
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
    <main className="mx-auto max-w-[44rem] px-5 py-8">
      <h1 className="text-[1.3rem] tracking-[-0.01em]">
        Pago de la reserva
      </h1>

      {estado === "cargando" && (
        <p className="text-texto-suave">Generando pago...</p>
      )}

      {estado === "listo" && preferencia && (
        <>
          <div
            className="mb-4 w-full max-w-none rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta"
          >
            <dl className="m-0">
              {reserva && reserva.precio !== null && (
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Monto</dt>
                  <dd className="m-0 text-right">
                    {formatearPrecio(reserva.precio)}
                  </dd>
                </div>
              )}
              <div className="flex justify-between gap-4 border-b border-borde py-3">
                <dt className="font-semibold">Metodo</dt>
                <dd className="m-0 text-right">
                  {esBypass() ? "Pago simulado" : "MercadoPago"}
                </dd>
              </div>
            </dl>
          </div>
          {esBypass() && (
            <div
              className="mb-4 w-full rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
              role="alert"
            >
              La pasarela de pagos está deshabilitada: tu reserva se confirmará
              sin procesar un cobro real. No se debitará ningún monto.
            </div>
          )}
          <button
            type="button"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover"
            onClick={irAPagar}
          >
            {esBypass() ? "Confirmar reserva (simulado)" : "Pagar con MercadoPago"}
          </button>
          {!esBypass() && (
            <p className="text-[0.8rem] text-texto-suave">
              Vas a salir de Tinku y continuar en el sitio de MercadoPago.
            </p>
          )}
        </>
      )}

      {estado === "finalizado" && (
        <div
          className="w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
          role="status"
        >
          {esBypass()
            ? "Reserva confirmada en modo simulado. Volvé a mis reservas."
            : "Redirigiendo a MercadoPago..."}
        </div>
      )}

      {estado === "error" && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
          {error}
          <button
            type="button"
            className="mt-3 block cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
            onClick={cargar}
          >
            Reintentar
          </button>
        </div>
      )}

      <p className="mt-5 text-center text-[0.9rem] text-texto-suave">
        <Link href="/cuenta/reservas">Volver a mis reservas</Link>
      </p>
    </main>
  );
}

export default function PagarPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-[44rem] px-5 py-8">Cargando...</div>}>
      <PagarForm />
    </Suspense>
  );
}