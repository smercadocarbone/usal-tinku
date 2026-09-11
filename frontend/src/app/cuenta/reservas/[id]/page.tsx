"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";
import { formatearFecha, formatearHora, formatearPrecio } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";

const DIAS = ["Domingo", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado"];

interface Franja {
  id: string;
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
  activa: boolean;
}

export default function ReservaDetallePage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [confirmando, setConfirmando] = useState(false);
  const [nuevoHorario, setNuevoHorario] = useState("");
  const [editandoHorario, setEditandoHorario] = useState(false);

  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [cargandoFranjas, setCargandoFranjas] = useState(false);
  const [franjasPendiente, setFranjasPendiente] = useState(false);
  const [fechaElegida, setFechaElegida] = useState("");
  const [franjaElegida, setFranjaElegida] = useState("");
  const [horaElegida, setHoraElegida] = useState("");
  const [horas, setHoras] = useState<string[]>([]);

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

  function pagar() {
    if (!reserva) return;
    router.replace(`/pagar?reserva=${reserva.id}`);
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

  async function cargarFranjas() {
    if (!reserva) return;
    setCargandoFranjas(true);
    setFranjasPendiente(false);
    try {
      const lista = await api.get<Franja[]>(`/api/tutores/${reserva.tutorId}/franjas`);
      setFranjas(lista);
    } catch {
      setFranjasPendiente(true);
    } finally {
      setCargandoFranjas(false);
    }
  }

  function abrirReprogramar() {
    const abriendo = !editandoHorario;
    setEditandoHorario(abriendo);
    if (abriendo) {
      setFranjas(null);
      setFranjasPendiente(false);
      setFechaElegida("");
      setFranjaElegida("");
      setHoraElegida("");
      setHoras([]);
      cargarFranjas();
    }
  }

  function fechaAplicable(f: Franja): string | null {
    if (f.fechaEspecifica) return f.fechaEspecifica;
    if (!fechaElegida) return null;
    const dia = new Date(`${fechaElegida}T12:00:00`).getDay();
    return f.diaSemana === dia ? fechaElegida : null;
  }

  function elegirFranja(id: string) {
    setFranjaElegida(id);
    setHoraElegida("");
    setHoras([]);
    const f = franjas?.find((x) => x.id === id);
    if (!f || !fechaAplicable(f)) return;
    setError(null);
    const [ini, fin] = [f.horaInicio, f.horaFin].map((h) => {
      const [hh, mm] = h.split(":").map(Number);
      return hh * 60 + mm;
    });
    const lista: string[] = [];
    for (let t = ini; t < fin; t += 60) {
      lista.push(
        `${String(Math.floor(t / 60)).padStart(2, "0")}:${String(t % 60).padStart(2, "0")}`
      );
    }
    setHoras(lista);
  }

  const franjasAplicables = franjas?.filter((f) => fechaAplicable(f) !== null) ?? [];

  async function reprogramar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!reserva) return;
    let iso = "";
    if (franjaElegida && horaElegida) {
      const f = franjas?.find((x) => x.id === franjaElegida);
      const fecha = f ? fechaAplicable(f) : null;
      if (fecha) iso = new Date(`${fecha}T${horaElegida}:00`).toISOString();
    } else if (nuevoHorario) {
      iso = new Date(nuevoHorario).toISOString();
    }
    if (!iso) return;
    setConfirmando(true);
    setError(null);
    try {
      const actualizada = await api.post<Reserva>(`/api/reservas/${reserva.id}/reprogramar`, {
        nuevoHorario: iso,
      });
      setReserva(actualizada);
      setEditandoHorario(false);
      setNuevoHorario("");
      setFranjas(null);
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
  const faltanMenosDe24hs =
    reserva !== null &&
    Date.now() >= new Date(reserva.horario).getTime() - 24 * 60 * 60 * 1000;

  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta/reservas", label: "Mis reservas" }]} />

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        <h1 className="text-[1.3rem] tracking-[-0.01em]">
          Detalle de la reserva
        </h1>

        {cargando && (
          <p className="text-texto-suave">Cargando...</p>
        )}

        {error && !cargando && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
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

        {reserva && (
          <>
            <div className="mb-4 w-full max-w-none rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
              <dl className="m-0">
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Estado</dt>
                  <dd className="m-0 text-right">
                    {ESTADO_ETIQUETA[reserva.estado] ?? reserva.estado}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Fecha</dt>
                  <dd className="m-0 text-right">
                    {formatearFecha(reserva.horario)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Horario</dt>
                  <dd className="m-0 text-right">
                    {formatearHora(reserva.horario)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Monto</dt>
                  <dd className="m-0 text-right">
                    {reserva.precio !== null
                      ? formatearPrecio(reserva.precio)
                      : "—"}
                  </dd>
                </div>
                {reserva.motivoCancelacion && (
                  <div className="flex justify-between gap-4 border-b border-borde py-3">
                    <dt className="font-semibold">Motivo de cancelacion</dt>
                    <dd className="m-0 text-right">
                      {reserva.motivoCancelacion}
                    </dd>
                  </div>
                )}
              </dl>
            </div>

            <div className="flex flex-col gap-2">
              {puedePagar && (
                <button
                  type="button"
                  className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover"
                  onClick={pagar}
                >
                  Pagar ahora
                </button>
              )}

              {puedeReprogramar && (
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                  onClick={abrirReprogramar}
                >
                  {editandoHorario ? "Cancelar edicion" : "Reprogramar"}
                </button>
              )}

              {puedeCancelar && (
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-red-200 bg-transparent px-4 py-[0.65rem] font-semibold text-peligro enabled:hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-60"
                  onClick={cancelar}
                  disabled={confirmando}
                >
                  {confirmando ? "Procesando..." : "Cancelar reserva"}
                </button>
              )}
            </div>

            {editandoHorario && puedeReprogramar && (
              <form
                onSubmit={reprogramar}
                className="mt-6 flex flex-col gap-4"
              >
                {faltanMenosDe24hs && (
                  <div
                    className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
                    role="status"
                  >
                    Faltan menos de 24 horas para esta clase. La reprogramacion se va a tratar
                    como cancelacion tardia.
                  </div>
                )}

                {cargandoFranjas && (
                  <p className="text-[0.9rem] text-texto-suave">
                    Cargando franjas...
                  </p>
                )}

                {!cargandoFranjas && franjasPendiente && (
                  <>
                    <div
                      className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
                      role="status"
                    >
                      El listado de franjas del tutor esta pendiente en backend.
                    </div>
                    <div className="flex flex-col gap-[0.35rem]">
                      <label htmlFor="nuevoHorario" className="text-[0.85rem] font-semibold">Nuevo horario</label>
                      <input
                        id="nuevoHorario"
                        type="datetime-local"
                        required
                        value={nuevoHorario}
                        onChange={(e) => setNuevoHorario(e.target.value)}
                        className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                      />
                    </div>
                  </>
                )}

                {!cargandoFranjas && !franjasPendiente && franjas?.length === 0 && (
                  <>
                    <div
                      className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
                      role="status"
                    >
                      Este tutor no publico disponibilidad todavia. Elegi un horario manual o
                      cerra el panel.
                    </div>
                    <div className="flex flex-col gap-[0.35rem]">
                      <label htmlFor="nuevoHorario" className="text-[0.85rem] font-semibold">Nuevo horario</label>
                      <input
                        id="nuevoHorario"
                        type="datetime-local"
                        required
                        value={nuevoHorario}
                        onChange={(e) => setNuevoHorario(e.target.value)}
                        className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                      />
                    </div>
                  </>
                )}

                {!cargandoFranjas && !franjasPendiente && franjas && franjas.length > 0 && (
                  <>
                    <div className="flex flex-col gap-[0.35rem]">
                      <label htmlFor="fechaReprogramar" className="text-[0.85rem] font-semibold">Fecha</label>
                      <input
                        id="fechaReprogramar"
                        type="date"
                        value={fechaElegida}
                        onChange={(e) => {
                          setFechaElegida(e.target.value);
                          setFranjaElegida("");
                          setHoraElegida("");
                          setHoras([]);
                        }}
                        className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                      />
                    </div>
                    <div className="flex flex-col gap-[0.35rem]">
                      <label htmlFor="franja" className="text-[0.85rem] font-semibold">Franja del tutor</label>
                      {franjasAplicables.length === 0 ? (
                        <p className="text-[0.9rem] text-texto-suave">
                          Elegi una fecha que corresponda a una franja.
                        </p>
                      ) : (
                        <select
                          id="franja"
                          value={franjaElegida}
                          onChange={(e) => elegirFranja(e.target.value)}
                          className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <option value="">Elegi una franja</option>
                          {franjasAplicables.map((f) => (
                            <option key={f.id} value={f.id}>
                              {f.fechaEspecifica
                                ? `${new Date(`${f.fechaEspecifica}T12:00:00`).toLocaleDateString(
                                    "es-AR",
                                    { day: "numeric", month: "short" }
                                  )} de ${f.horaInicio} a ${f.horaFin}`
                                : `${DIAS[f.diaSemana ?? 0]} de ${f.horaInicio} a ${f.horaFin}`}
                            </option>
                          ))}
                        </select>
                      )}
                    </div>
                    {horas.length > 0 && (
                      <div className="flex flex-col gap-[0.35rem]">
                        <label htmlFor="hora" className="text-[0.85rem] font-semibold">Horario</label>
                        <select
                          id="hora"
                          value={horaElegida}
                          onChange={(e) => setHoraElegida(e.target.value)}
                          className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <option value="">Elegi un horario</option>
                          {horas.map((h) => (
                            <option key={h} value={h}>
                              {h}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                  </>
                )}

                <button
                  type="submit"
                  className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={confirmando || (!nuevoHorario && !(franjaElegida && horaElegida))}
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