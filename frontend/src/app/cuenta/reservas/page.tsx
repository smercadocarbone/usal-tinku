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

      <main className="contenido">
        <h1 style={{ fontSize: "1.3rem", letterSpacing: "-0.01em" }}>
          Mis reservas
        </h1>

        {cargando && (
          <p style={{ color: "var(--color-texto-suave)" }}>Cargando...</p>
        )}

        {!cargando && reservas === null && (
          <div className="alerta alerta--error" role="alert">
            No se pudieron cargar tus reservas en este momento.
            <button
              type="button"
              className="boton boton--secundario"
              onClick={cargar}
              style={{ marginTop: "0.75rem", fontSize: "0.85rem", padding: "0.4rem 0.75rem" }}
            >
              Reintentar
            </button>
          </div>
        )}

        {reservas && reservas.length === 0 && (
          <p style={{ color: "var(--color-texto-suave)" }}>
            No tenes reservas todavia.{" "}
            <Link href="/buscar">Busca un tutor</Link> para empezar.
          </p>
        )}

        {reservas && reservas.length > 0 && (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {reservas.map((r) => (
              <li
                key={r.id}
                style={{
                  padding: "1rem",
                  marginBottom: "0.5rem",
                  background: "var(--color-superficie)",
                  border: "1px solid var(--color-borde)",
                  borderRadius: "var(--radio)",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <div>
                  <div style={{ fontWeight: 600 }}>
                    {formatearFecha(r.horario)} {formatearHora(r.horario)}
                  </div>
                  <div
                    style={{
                      fontSize: "0.85rem",
                      color: "var(--color-texto-suave)",
                      marginTop: "0.15rem",
                    }}
                  >
                    {ESTADO_ETIQUETA[r.estado] ?? r.estado}
                    {r.precio !== null &&
                      ` — ${formatearPrecio(r.precio)}`}
                  </div>
                </div>
                <Link
                  href={`/cuenta/reservas/${r.id}`}
                  className="boton boton--secundario"
                  style={{
                    fontSize: "0.85rem",
                    padding: "0.4rem 0.75rem",
                    textDecoration: "none",
                  }}
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