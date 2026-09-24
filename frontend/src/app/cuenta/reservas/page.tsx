"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";
import { formatearFecha, formatearHora, formatearPrecio } from "@/lib/formatos";
import { Alerta, Boton, Cargando, EstadoVacio, Tarjeta, clasesBoton } from "@/components/ui";

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
    <div className="max-w-2xl">
      <h1 className="text-xl tracking-tight">
        Mis reservas
      </h1>

        {cargando && <Cargando>Cargando...</Cargando>}

        {!cargando && reservas === null && (
          <Alerta tono="error">
            No se pudieron cargar tus reservas en este momento.
            <Boton
              variante="secundario"
              tamano="sm"
              className="mt-3 flex"
              onClick={cargar}
            >
              Reintentar
            </Boton>
          </Alerta>
        )}

        {reservas && reservas.length === 0 && (
          <EstadoVacio>
            No tenés reservas todavía.{" "}
            <Link href="/buscar">Buscá un tutor</Link> para empezar.
          </EstadoVacio>
        )}

        {reservas && reservas.length > 0 && (
          <ul className="m-0 list-none p-0">
            {reservas.map((r) => (
              <Tarjeta
                as="li"
                key={r.id}
                className="mb-2 flex items-center justify-between p-4"
              >
                <div>
                  <div className="font-semibold">
                    {formatearFecha(r.horario)} {formatearHora(r.horario)}
                  </div>
                  <div className="mt-0.5 text-sm text-slate-500">
                    {ESTADO_ETIQUETA[r.estado] ?? r.estado}
                    {r.precio !== null &&
                      ` — ${formatearPrecio(r.precio)}`}
                  </div>
                </div>
                <Link
                  href={`/cuenta/reservas/${r.id}`}
                  className={clasesBoton("secundario", "sm")}
                >
                  Detalle
                </Link>
              </Tarjeta>
            ))}
          </ul>
        )}
    </div>
  );
}
