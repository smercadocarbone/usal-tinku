"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { clearSession } from "@/lib/auth";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";

export default function ReservasPage() {
  const router = useRouter();
  const [reservas, setReservas] = useState<Reserva[] | null>(null);

  function logout() {
    clearSession();
    router.replace("/");
  }

  useEffect(() => {
    let activo = true;
    api
      .get<Reserva[]>("/api/reservas")
      .then((lista) => activo && setReservas(lista))
      .catch((err) => {
        if (!activo) return;
        if (err instanceof ApiError && err.status === 404) {
          setReservas([]);
        } else {
          setReservas(null);
        }
      });
    return () => {
      activo = false;
    };
  }, []);

  return (
    <>
      <header className="cabecera">
        <div className="marca" style={{ marginBottom: 0 }}>
          Tinku<span>.</span>
        </div>
        <nav style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          <Link href="/buscar" style={{ fontSize: "0.9rem" }}>
            Buscar
          </Link>
          <Link href="/cuenta" style={{ fontSize: "0.9rem" }}>
            Mi cuenta
          </Link>
          <button
            type="button"
            className="boton boton--secundario"
            onClick={logout}
          >
            Cerrar sesion
          </button>
        </nav>
      </header>

      <main className="contenido">
        <h1 style={{ fontSize: "1.3rem", letterSpacing: "-0.01em" }}>
          Mis reservas
        </h1>

        {reservas === null && (
          <div className="alerta alerta--informativa" role="status">
            No se pudieron cargar tus reservas en este momento. Si acabas de
            crear una, proba de nuevo en un momento.
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
                    {new Date(r.horario).toLocaleDateString("es-AR", {
                      day: "numeric",
                      month: "long",
                      year: "numeric",
                    })}{" "}
                    {
                      new Date(r.horario).toLocaleTimeString("es-AR", {
                        hour: "2-digit",
                        minute: "2-digit",
                      })
                    }
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
                      ` — $${Number(r.precio).toLocaleString("es-AR")}`}
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