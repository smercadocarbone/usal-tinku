"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import {
  api,
  ApiError,
  getResumenSesion,
  getSesionPorReserva,
  type ResumenSesionInfo,
  type SesionInfo,
} from "@/lib/api";
import { ESTADO_ETIQUETA, Reserva } from "@/lib/reservas";
import { ETIQUETA_MOTIVO_CANCELACION, etiqueta } from "@/lib/etiquetas";
import { formatearFecha, formatearHora, formatearPrecio } from "@/lib/formatos";
import FormularioCalificacion from "@/components/FormularioCalificacion";
import { Alerta, Boton, Campo, CampoSelect, Cargando, Tarjeta, clasesBoton } from "@/components/ui";

const DIAS = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"];

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
  const [sesion, setSesion] = useState<SesionInfo | null>(null);
  const [resumen, setResumen] = useState<ResumenSesionInfo | null>(null);
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
    let r: Reserva | null = null;
    try {
      r = await api.get<Reserva>(`/api/reservas/${params.id}`);
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
      return;
    } finally {
      setCargando(false);
    }

    // Estados que por construcción no pueden tener Sesión: no pedirla (B11) —
    // un 404 esperado en cada carga de una reserva cancelada no es un error.
    // Para confirmada en adelante, un 404 puntual SÍ es el caso normal (job
    // T-5 que todavía no creó la Sesión), y se trata igual: nada que mostrar.
    const ESTADOS_SIN_SESION = new Set(["pendiente_pago", "cancelada"]);
    let sesionActual: SesionInfo | null = null;
    if (r && !ESTADOS_SIN_SESION.has(r.estado)) {
      try {
        sesionActual = await getSesionPorReserva(params.id);
        setSesion(sesionActual);
      } catch {
        setSesion(null);
      }

      if (sesionActual) {
        try {
          setResumen(await getResumenSesion(sesionActual.id));
        } catch {
          setResumen(null);
        }
      }
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
    if (!window.confirm("¿Seguro que querés cancelar esta reserva?")) return;
    setConfirmando(true);
    setError(null);
    try {
      const actualizada = await api.post<Reserva>(`/api/reservas/${reserva.id}/cancelar`);
      setReserva(actualizada);
      // Al cancelar ya no hay clase a la que entrar ni sesión que calificar.
      setSesion(null);
      setResumen(null);
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
  const puedeEntrarAClase =
    sesion !== null && (sesion.estado === "no_iniciada" || sesion.estado === "en_curso");
  // Solo el cierre limpio (`finalizada`) habilita calificar — un no-show doble
  // o un corte interrumpido antes del 50% no son una clase que se pueda
  // evaluar, y el backend tampoco lo esperaría como caso de uso normal.
  const puedeCalificar = sesion !== null && sesion.estado === "finalizada";
  const faltanMenosDe24hs =
    reserva !== null &&
    Date.now() >= new Date(reserva.horario).getTime() - 24 * 60 * 60 * 1000;

  return (
    <div className="max-w-2xl">
      <h1 className="text-xl tracking-tight">
        Detalle de la reserva
      </h1>

        {cargando && <Cargando>Cargando...</Cargando>}

        {error && !cargando && (
          <Alerta tono="error" className="mb-4">
            {error}
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

        {reserva && (
          <>
            <Tarjeta className="mb-4 w-full max-w-none p-8">
              <dl className="m-0">
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Estado</dt>
                  <dd className="m-0 text-right">
                    {ESTADO_ETIQUETA[reserva.estado] ?? reserva.estado}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Fecha</dt>
                  <dd className="m-0 text-right">
                    {formatearFecha(reserva.horario)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Horario</dt>
                  <dd className="m-0 text-right">
                    {formatearHora(reserva.horario)}
                  </dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Monto</dt>
                  <dd className="m-0 text-right">
                    {reserva.precio !== null
                      ? formatearPrecio(reserva.precio)
                      : "—"}
                  </dd>
                </div>
                {reserva.motivoCancelacion && (
                  <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                    <dt className="font-semibold">Motivo de cancelación</dt>
                    <dd className="m-0 text-right">
                      {etiqueta(ETIQUETA_MOTIVO_CANCELACION, reserva.motivoCancelacion)}
                    </dd>
                  </div>
                )}
              </dl>
            </Tarjeta>

            <div className="flex flex-col gap-2">
              {puedePagar && <Boton onClick={pagar}>Pagar ahora</Boton>}

              {puedeEntrarAClase && sesion && (
                <Link href={`/aula/${sesion.id}`} className={clasesBoton("primario")}>
                  Entrar a la clase
                </Link>
              )}

              {puedeReprogramar && (
                <Boton variante="secundario" onClick={abrirReprogramar}>
                  {editandoHorario ? "Cancelar edición" : "Reprogramar"}
                </Boton>
              )}

              {puedeCancelar && (
                <Boton
                  variante="peligro"
                  onClick={cancelar}
                  cargando={confirmando}
                  textoCargando="Procesando..."
                >
                  Cancelar reserva
                </Boton>
              )}
            </div>

            {editandoHorario && puedeReprogramar && (
              <form
                onSubmit={reprogramar}
                className="mt-6 flex flex-col gap-4"
              >
                {faltanMenosDe24hs && (
                  <Alerta tono="aviso" className="mb-4">
                    Faltan menos de 24 horas para esta clase. La reprogramación se va a tratar
                    como cancelación tardía.
                  </Alerta>
                )}

                {cargandoFranjas && <Cargando>Cargando franjas...</Cargando>}

                {!cargandoFranjas && franjasPendiente && (
                  <>
                    <Alerta tono="aviso" className="mb-4">
                      No pudimos cargar las franjas del tutor. Probá de nuevo en unos minutos.
                    </Alerta>
                    <Campo
                      id="nuevoHorario"
                      etiqueta="Nuevo horario"
                      type="datetime-local"
                      required
                      value={nuevoHorario}
                      onChange={(e) => setNuevoHorario(e.target.value)}
                    />
                  </>
                )}

                {!cargandoFranjas && !franjasPendiente && franjas?.length === 0 && (
                  <>
                    <Alerta tono="aviso" className="mb-4">
                      Este tutor no publicó disponibilidad todavía. Elegí un horario manual o
                      cerrá el panel.
                    </Alerta>
                    <Campo
                      id="nuevoHorario"
                      etiqueta="Nuevo horario"
                      type="datetime-local"
                      required
                      value={nuevoHorario}
                      onChange={(e) => setNuevoHorario(e.target.value)}
                    />
                  </>
                )}

                {!cargandoFranjas && !franjasPendiente && franjas && franjas.length > 0 && (
                  <>
                    <Campo
                      id="fechaReprogramar"
                      etiqueta="Fecha"
                      type="date"
                      value={fechaElegida}
                      onChange={(e) => {
                        setFechaElegida(e.target.value);
                        setFranjaElegida("");
                        setHoraElegida("");
                        setHoras([]);
                      }}
                    />
                    <div className="flex flex-col gap-1.5">
                      <label htmlFor="franja" className="text-sm font-semibold">Franja del tutor</label>
                      {franjasAplicables.length === 0 ? (
                        <p className="text-sm text-slate-500">
                          Elegi una fecha que corresponda a una franja.
                        </p>
                      ) : (
                        <select
                          id="franja"
                          value={franjaElegida}
                          onChange={(e) => elegirFranja(e.target.value)}
                          className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          <option value="">Elegí una franja</option>
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
                      <CampoSelect
                        id="hora"
                        etiqueta="Horario"
                        value={horaElegida}
                        onChange={(e) => setHoraElegida(e.target.value)}
                      >
                        <option value="">Elegí un horario</option>
                        {horas.map((h) => (
                          <option key={h} value={h}>
                            {h}
                          </option>
                        ))}
                      </CampoSelect>
                    )}
                  </>
                )}

                <Boton
                  type="submit"
                  disabled={!nuevoHorario && !(franjaElegida && horaElegida)}
                  cargando={confirmando}
                  textoCargando="Reprogramando..."
                >
                  Confirmar nuevo horario
                </Boton>
              </form>
            )}

            {resumen?.disponible && (
              <Tarjeta className="mt-6 w-full max-w-none p-6">
                <h2 className="mb-2 text-base font-semibold text-slate-800">Resumen de la clase</h2>
                <p className="whitespace-pre-line text-sm text-slate-700">{resumen.resumenFinal}</p>
              </Tarjeta>
            )}

            {puedeCalificar && sesion && (
              <div className="mt-6">
                <FormularioCalificacion sesionId={sesion.id} />
              </div>
            )}
          </>
        )}
    </div>
  );
}