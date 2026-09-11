"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";
import { formatearFecha, formatearHora, formatearPrecio } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";

export default function ReservasPage() {
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [cargando, setCargando] = useState(true);

  const cargar = useCallback(() => {
    setCargando(true);
    setReservas(null);
    api
      .get<Reserva[]>("/api/reservas")
      .then((lista) => setReservas(lista))
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setReservas([]);
        } else {
          setReservas(null);
        }
      })
      .finally(() => setCargando(false));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  return (
    <>
      <Cabecera enlaces={[{ href: "/buscar", label: "Buscar" }, { href: "/cuenta", label: "Mi cuenta" }]} />

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        <h1 className="text-[1.3rem] tracking-[-0.01em]">
          Mis reservas
        </h1>

        {cargando && (
          <p className="text-texto-suave">Cargando...</p>
        )}

        {!cargando && reservas === null && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
            No se pudieron cargar tus reservas en este momento.
            <button
              type="button"
              className="mt-3 block cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
              onClick={cargar}
            >
              Reintentar
            </button>
          </div>
        )}

        {reservas && reservas.length === 0 && (
          <p className="text-texto-suave">
            No tenes reservas todavia.{" "}
            <Link href="/buscar">Busca un tutor</Link> para empezar.
          </p>
        )}

        {reservas && reservas.length > 0 && (
          <ul className="m-0 list-none p-0">
            {reservas.map((r) => (
              <li
                key={r.id}
                className="mb-2 flex items-center justify-between rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta"
              >
                <div>
                  <div className="font-semibold">
                    {formatearFecha(r.horario)} {formatearHora(r.horario)}
                  </div>
                  <div className="mt-[0.15rem] text-[0.85rem] text-texto-suave">
                    {ESTADO_ETIQUETA[r.estado] ?? r.estado}
                    {r.precio !== null &&
                      ` — ${formatearPrecio(r.precio)}`}
                  </div>
                </div>
                <Link
                  href={`/cuenta/reservas/${r.id}`}
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                >
                  Detalle
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  );
}