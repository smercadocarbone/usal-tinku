"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { clearSession } from "@/lib/auth";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";

interface Preferencia {
  preferenciaId: string;
  initPoint: string;
}

export default function ReservaDetallePage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [nuevoHorario, setNuevoHorario] = useState("");
  const [editandoHorario, setEditandoHorario] = useState(false);

  async function cargar() {
    setCargando(true);
    setError(null);
    try {
      const r = await api.get<Reserva>(`/api/reservas/${params.id}`);
      setReserva(r);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(
          err.status === 404
            ? "La reserva no existe."
            : err.message || "No se pudo cargar la reserva."
        );
      } else {
        setError("No se pudo cargar la reserva.");
      }
    } finally {
      setCargando(false);
    }
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.id]);

  function logout() {
    clearSession();
    router.replace("/");
  }

  async function pagar() {
    setError(null);
    try {
      const p = await api.post<Preferencia>("/api/pagos/preferencia", {
        reservaId: reserva!.id,
      });
      window.location.assign(p.initPoint);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo generar el pago.");
      }
    }
  }

  async function cancelar() {
    if (!reserva) return;
    if (!window.confirm("Seguro que queres cancelar esta reserva?")) return;
    setConfirmando(true);
    setError(null);
    try {
      const actualizada = await api.post<Reserva>(`/api/reservas/${reserva.id}/cancelar`);
      setReserva(actualizada);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo cancelar la reserva.");
      }
    } finally {
      setConfirmando(false);
    }
  }

  async function reprogramar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!reserva || !nuevoHorario) return;
    setConfirmando(true);
    setError(null);
    try {
      const actualizada = await api.post<Reserva>(`/api/reservas/${reserva.id}/reprogramar`, {
        nuevoHorario: new Date(nuevoHorario).toISOString(),
      });
      setReserva(actualizada);
      setEditandoHorario(false);
      setNuevoHorario("");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo reprogramar la reserva.");
      }
    } finally {
      setConfirmando(false);
    }
  }

  const puedePagar = reserva?.estado === "pendiente_pago";
  const puedeCancelar =
    reserva?.estado === "pendiente_pago" || reserva?.estado === "confirmada";
  const puedeReprogramar = reserva?.estado === "confirmada";

  return (
    <>
      <header className="cabecera">
        <div className="marca" style={{ marginBottom: 0 }}>
          Tinku<span>.</span>
        </div>
        <nav style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          <Link href="/cuenta/reservas" style={{ fontSize: "0.9rem" }}>
            Mis reservas
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
          Detalle de la reserva
        </h1>

        {cargando && (
          <p style={{ color: "var(--color-texto-suave)" }}>Cargando...</p>
        )}

        {error && (
          <div className="alerta alerta--error" role="alert" style={{ marginBottom: "1rem" }}>
            {error}
          </div>
        )}

        {reserva && (
          <>
            <div
              className="tarjeta"
              style={{ maxWidth: "none", marginBottom: "1rem" }}
            >
              <dl style={{ margin: 0 }}>
                <div className="perfil-fila">
                  <dt>Estado</dt>
                  <dd style={{ textTransform: "none" }}>
                    {ESTADO_ETIQUETA[reserva.estado] ?? reserva.estado}
                  </dd>
                </div>
                <div className="perfil-fila">
                  <dt>Fecha</dt>
                  <dd style={{ textTransform: "none" }}>
                    {new Date(reserva.horario).toLocaleDateString("es-AR", {
                      day: "numeric",
                      month: "long",
                      year: "numeric",
                    })}
                  </dd>
                </div>
                <div className="perfil-fila">
                  <dt>Horario</dt>
                  <dd style={{ textTransform: "none" }}>
                    {new Date(reserva.horario).toLocaleTimeString("es-AR", {
                      hour: "2-digit",
                      minute: "2-digit",
                    })}
                  </dd>
                </div>
                <div className="perfil-fila">
                  <dt>Monto</dt>
                  <dd style={{ textTransform: "none" }}>
                    {reserva.precio !== null
                      ? `$${Number(reserva.precio).toLocaleString("es-AR")}`
                      : "—"}
                  </dd>
                </div>
                {reserva.motivoCancelacion && (
                  <div className="perfil-fila">
                    <dt>Motivo de cancelacion</dt>
                    <dd style={{ textTransform: "none" }}>
                      {reserva.motivoCancelacion}
                    </dd>
                  </div>
                )}
              </dl>
            </div>

            <div
              style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}
            >
              {puedePagar && (
                <button type="button" className="boton" onClick={pagar}>
                  Pagar ahora
                </button>
              )}

              {puedeReprogramar && (
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={() => setEditandoHorario((v) => !v)}
                >
                  {editandoHorario ? "Cancelar edicion" : "Reprogramar"}
                </button>
              )}

              {puedeCancelar && (
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cancelar}
                  disabled={confirmando}
                  style={{ color: "var(--color-peligro)", borderColor: "#fecaca" }}
                >
                  {confirmando ? "Procesando..." : "Cancelar reserva"}
                </button>
              )}
            </div>

            {editandoHorario && puedeReprogramar && (
              <form
                onSubmit={reprogramar}
                className="formulario"
                style={{ marginTop: "1.5rem" }}
              >
                <div className="campo">
                  <label htmlFor="nuevoHorario">Nuevo horario</label>
                  <input
                    id="nuevoHorario"
                    type="datetime-local"
                    required
                    value={nuevoHorario}
                    onChange={(e) => setNuevoHorario(e.target.value)}
                  />
                </div>
                <button
                  type="submit"
                  className="boton"
                  disabled={confirmando || !nuevoHorario}
                >
                  {confirmando ? "Reprogramando..." : "Confirmar nuevo horario"}
                </button>
              </form>
            )}
          </>
        )}
      </main>
    </>
  );
}