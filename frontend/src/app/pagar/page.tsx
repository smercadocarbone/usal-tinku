"use client";

import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";

interface Preferencia {
  preferenciaId: string;
  initPoint: string;
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
    window.location.assign(preferencia.initPoint);
  }

  return (
    <main className="contenido">
      <h1 style={{ fontSize: "1.3rem", letterSpacing: "-0.01em" }}>
        Pago de la reserva
      </h1>

      {estado === "cargando" && (
        <p style={{ color: "var(--color-texto-suave)" }}>Generando pago...</p>
      )}

      {estado === "listo" && preferencia && (
        <>
          <div
            className="tarjeta"
            style={{ maxWidth: "none", marginBottom: "1rem" }}
          >
            <dl style={{ margin: 0 }}>
              {reserva && reserva.precio !== null && (
                <div className="perfil-fila">
                  <dt>Monto</dt>
                  <dd style={{ textTransform: "none" }}>
                    {formatearPrecio(reserva.precio)}
                  </dd>
                </div>
              )}
              <div className="perfil-fila">
                <dt>Metodo</dt>
                <dd style={{ textTransform: "none" }}>MercadoPago</dd>
              </div>
            </dl>
          </div>
          <button type="button" className="boton" onClick={irAPagar}>
            Pagar con MercadoPago
          </button>
          <p style={{ fontSize: "0.8rem", color: "var(--color-texto-suave)" }}>
            Vas a salir de Tinku y continuar en el sitio de MercadoPago.
          </p>
        </>
      )}

      {estado === "finalizado" && (
        <div className="alerta alerta--informativa" role="status">
          Redirigiendo a MercadoPago...
        </div>
      )}

      {estado === "error" && (
        <div className="alerta alerta--error" role="alert">
          {error}
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

      <p className="pie-enlace">
        <Link href="/cuenta/reservas">Volver a mis reservas</Link>
      </p>
    </main>
  );
}

export default function PagarPage() {
  return (
    <Suspense fallback={<div className="contenido">Cargando...</div>}>
      <PagarForm />
    </Suspense>
  );
}