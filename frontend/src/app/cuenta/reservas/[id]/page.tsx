"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { CalendarClock, ChevronLeft, CircleSlash, Flag, Sparkles, Video, WalletCards } from "lucide-react";
import {
  api,
  ApiError,
  getResumenSesion,
  getSesionPorReserva,
  mensajeDeError,
  type ResumenSesionInfo,
  type SesionInfo,
} from "@/lib/api";
import { finDe, type Reserva } from "@/lib/reservas";
import { ETIQUETA_MOTIVO_CANCELACION, etiqueta } from "@/lib/etiquetas";
import { diaCorto, duracionLegible, fechaHoraLarga, formatearPesos } from "@/lib/formatos";
import { TIEMPOS } from "@/lib/tiempos";
import { useSesion } from "@/lib/useSesion";
import { useAhora } from "@/lib/useAhora";
import { hhmm, inicioISO, proximosDias, type Franja } from "@/lib/agenda";
import { nombreCorto } from "@/lib/tutores";
import { cn } from "@/lib/cn";
import FormularioCalificacion from "@/components/FormularioCalificacion";
import FormularioDenuncia from "@/components/FormularioDenuncia";
import {
  Alerta,
  Avatar,
  Boton,
  EstadoReserva,
  EstadoVacio,
  Menu,
  Modal,
  ModalConfirmacion,
  SkeletonPerfil,
  Tarjeta,
  clasesBoton,
  useToast,
} from "@/components/ui";

/** Pasos de la línea de tiempo de una clase (UX-05 §4). Sin fechas inventadas: solo estado. */
function pasosLinea(r: Reserva, sesion: SesionInfo | null) {
  const orden = ["pendiente_pago", "confirmada", "en_curso", "finalizada"];
  const i = orden.indexOf(r.estado);
  return [
    { titulo: "Reservada", hecho: true },
    { titulo: "Pagada y confirmada", hecho: i >= 1 },
    { titulo: "Clase", hecho: i >= 3 || sesion?.estado === "finalizada", actual: r.estado === "en_curso" },
    {
      titulo: `Pago al tutor (${TIEMPOS.liberacionHoras} hs después)`,
      hecho: false,
      detalle: i >= 3 ? `Se libera ${TIEMPOS.liberacionHoras} hs después de la clase si no hay reclamos.` : undefined,
    },
  ];
}

export default function ReservaDetallePage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const toast = useToast();
  const sesionUsuario = useSesion();
  const payload = sesionUsuario?.payload;
  const esMenor = payload?.tipo === "MENOR";
  const esTutor = payload?.tipo === "TUTOR";
  const ahora = useAhora();

  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [sesion, setSesion] = useState<SesionInfo | null>(null);
  const [resumen, setResumen] = useState<ResumenSesionInfo | null>(null);
  const [confirmarCancelar, setConfirmarCancelar] = useState(false);
  const [cancelando, setCancelando] = useState(false);
  const [reprogramar, setReprogramar] = useState(false);
  const [reportar, setReportar] = useState(false);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    let r: Reserva;
    try {
      r = await api.get<Reserva>(`/api/reservas/${params.id}`);
      setReserva(r);
    } catch (err) {
      setError(
        err instanceof ApiError && err.status === 404
          ? "No encontramos esta clase."
          : "No pudimos cargar la clase. Revisá tu conexión y probá de nuevo."
      );
      setCargando(false);
      return;
    }
    setCargando(false);
    // B11: sin Sesión por construcción (pendiente/cancelada) no se pide.
    if (r.estado === "pendiente_pago" || r.estado === "cancelada") return;
    try {
      const s = await getSesionPorReserva(params.id);
      setSesion(s);
      try {
        setResumen(await getResumenSesion(s.id));
      } catch {
        setResumen(null);
      }
    } catch {
      setSesion(null);
    }
  }, [params.id]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function cancelar() {
    if (!reserva) return;
    setCancelando(true);
    try {
      const r = await api.post<Reserva>(`/api/reservas/${reserva.id}/cancelar`);
      setReserva(r);
      setSesion(null);
      setResumen(null);
      setConfirmarCancelar(false);
      toast.mostrar("Cancelaste la clase");
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos cancelar la clase."), { tono: "error" });
    } finally {
      setCancelando(false);
    }
  }

  if (cargando) return <SkeletonPerfil etiqueta="Cargando la clase…" />;
  if (error || !reserva) {
    return (
      <EstadoVacio
        icono={<CircleSlash />}
        titulo="No pudimos mostrar esta clase"
        accion={<Boton onClick={() => void cargar()}>Probar de nuevo</Boton>}
      >
        {error}
      </EstadoVacio>
    );
  }

  const r = reserva;
  const fin = finDe(r);
  const tutorNombre = r.tutorNombre ? `${r.tutorNombre} ${r.tutorApellido ?? ""}`.trim() : "tu tutor";
  const beneficiario = r.beneficiarioNombre && r.beneficiarioId !== r.pagadorId ? r.beneficiarioNombre : null;
  const titulo = esTutor
    ? `Clase con ${r.beneficiarioNombre ? nombreCorto(r.beneficiarioNombre, r.beneficiarioApellido) : "tu alumno"}`
    : beneficiario
      ? `Clase de ${beneficiario} con ${r.tutorNombre ?? "su tutor"}`
      : `Clase con ${r.tutorNombre ?? "tu tutor"}`;
  const puedePagar = r.puedePagar ?? r.estado === "pendiente_pago";
  const puedeCancelar = r.puedeCancelar ?? (r.estado === "pendiente_pago" || r.estado === "confirmada");
  const puedeEntrar = sesion !== null && (sesion.estado === "no_iniciada" || sesion.estado === "en_curso");
  // AUD-028: califica quien pagó; en la clase de un menor, su Adulto Responsable.
  const puedeCalificar = sesion !== null && sesion.estado === "finalizada" && !esMenor;
  const puedeReprogramar = r.estado === "confirmada" && !esMenor;
  const cancelada = r.estado === "cancelada";
  const empiezaPronto = ahora > 0 && new Date(r.horario).getTime() - ahora < 60 * 60000;

  return (
    <div className="mx-auto max-w-2xl">
      <Link
        href="/cuenta/reservas"
        className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida"
      >
        <ChevronLeft className="size-5" aria-hidden /> Mis clases
      </Link>

      <header className="flex items-start gap-4">
        <Avatar
          nombre={(esTutor ? r.beneficiarioNombre : r.tutorNombre) ?? "?"}
          apellido={(esTutor ? r.beneficiarioApellido : r.tutorApellido) ?? undefined}
          semilla={esTutor ? (r.beneficiarioId ?? r.id) : r.tutorId}
          tamano="lg"
        />
        <div className="min-w-0 flex-1">
          <p className="sr-only">Detalle de la reserva</p>
          <h1 className="text-[26px] font-extrabold sm:text-[32px]">{titulo}</h1>
          <p className="mt-1 text-[16px] text-tinta-suave first-letter:uppercase">{fechaHoraLarga(r.horario, fin)}</p>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <EstadoReserva estado={r.estado} />
            {r.duracionMinutos ? <span className="text-sm text-tinta-tenue">{duracionLegible(r.duracionMinutos)}</span> : null}
            {r.precio !== null && <span className="tabular text-sm font-semibold">{formatearPesos(r.precio)}</span>}
          </div>
        </div>
        {!esMenor && !esTutor && !cancelada && (
          <Menu
            etiqueta="Más opciones de esta clase"
            items={[{ texto: "Reportar un problema", icono: <Flag />, peligro: true, onClick: () => setReportar(true) }]}
          />
        )}
      </header>

      {!esTutor && r.tutorNombre && (
        <p className="mt-4 text-sm">
          <Link href={`/tutores/${r.tutorId}`} className="font-semibold">
            Ver el perfil de {tutorNombre}
          </Link>
        </p>
      )}

      {cancelada && (
        <Alerta tono="info" className="mt-6" titulo={etiqueta(ETIQUETA_MOTIVO_CANCELACION, r.motivoCancelacion)}>
          {r.motivoCancelacion === "timeout_pago"
            ? "No se te cobró nada y el horario se liberó."
            : "Si ya se había pagado, la devolución sigue la política de cancelación."}
        </Alerta>
      )}

      {/* Acción principal según el estado */}
      <div className="mt-8 flex flex-col gap-3">
        {puedePagar && (
          <Boton tamano="lg" anchoCompleto icono={<WalletCards />} onClick={() => router.push(`/pagar?reserva=${r.id}`)}>
            Pagar ahora
          </Boton>
        )}
        {puedeEntrar && sesion && (
          <Link href={`/aula/${sesion.id}`} className={clasesBoton("primario", "lg", "w-full")}>
            <Video className="size-5" aria-hidden /> Entrar a la clase
          </Link>
        )}
        {r.estado === "confirmada" && !puedeEntrar && !empiezaPronto && (
          <p className="flex items-center gap-2 text-sm text-tinta-suave">
            <CalendarClock className="size-4" aria-hidden /> El aula se abre {TIEMPOS.salaAbreMinutosAntes} minutos antes de la clase.
          </p>
        )}
        {(puedeReprogramar || puedeCancelar) && (
          <div className="flex flex-col gap-2 sm:flex-row">
            {puedeReprogramar && (
              <Boton variante="secundario" className="flex-1" onClick={() => setReprogramar(true)}>
                Cambiar horario
              </Boton>
            )}
            {puedeCancelar && (
              <Boton variante="secundario" className="flex-1 text-peligro" onClick={() => setConfirmarCancelar(true)}>
                Cancelar clase
              </Boton>
            )}
          </div>
        )}
      </div>

      {!cancelada && (
        <section className="mt-10" aria-labelledby="linea">
          <h2 id="linea" className="text-lg font-bold">
            Cómo va
          </h2>
          <ol className="mt-4 flex list-none flex-col p-0">
            {pasosLinea(r, sesion).map((p, i, arr) => (
              <li key={p.titulo} className="relative flex gap-4 pb-6 last:pb-0">
                {i < arr.length - 1 && (
                  <span aria-hidden className={cn("absolute left-[11px] top-7 h-[calc(100%-1.5rem)] w-0.5", p.hecho ? "bg-marca-700" : "bg-borde")} />
                )}
                <span
                  aria-hidden
                  className={cn(
                    "relative z-10 mt-0.5 size-6 shrink-0 rounded-full border-2",
                    p.hecho ? "border-marca-700 bg-marca-700" : p.actual ? "border-info bg-info-suave" : "border-borde-fuerte bg-superficie"
                  )}
                />
                <div>
                  <p className={cn("text-[15px] font-semibold", !p.hecho && !p.actual && "text-tinta-tenue")}>
                    {p.titulo}
                    <span className="sr-only">{p.hecho ? " (hecho)" : p.actual ? " (en curso)" : " (pendiente)"}</span>
                  </p>
                  {p.detalle && <p className="text-sm text-tinta-tenue">{p.detalle}</p>}
                </div>
              </li>
            ))}
          </ol>
        </section>
      )}

      {resumen?.disponible && (
        <Tarjeta className="mt-10">
          <h2 className="flex items-center gap-2 text-lg font-bold">
            <Sparkles className="size-5 text-acento-700" aria-hidden /> Resumen de la clase
          </h2>
          <p className="mt-3 whitespace-pre-line text-[15px] leading-relaxed text-tinta-suave">{resumen.resumenFinal}</p>
        </Tarjeta>
      )}

      {puedeCalificar && sesion && (
        <div className="mt-10">
          <FormularioCalificacion sesionId={sesion.id} />
        </div>
      )}

      <ModalConfirmacion
        abierto={confirmarCancelar}
        onCerrar={() => setConfirmarCancelar(false)}
        onConfirmar={cancelar}
        cargando={cancelando}
        titulo="¿Cancelar esta clase?"
        textoConfirmar="Cancelar la clase"
      >
        {r.estado === "pendiente_pago" ? (
          <p>Todavía no pagaste, así que no se te cobra nada. El horario vuelve a quedar libre.</p>
        ) : r.cancelarReembolsaTotal === false ? (
          <p>
            <strong className="text-tinta">Faltan menos de {TIEMPOS.cancelacionSinPenalidadHoras} hs:</strong> si cancelás ahora, el pago se le libera
            al tutor y no hay devolución.
          </p>
        ) : esTutor ? (
          <p>Si cancelás, le devolvemos el total a quien pagó la clase.</p>
        ) : (
          <p>Como falta más de {TIEMPOS.cancelacionSinPenalidadHoras} hs, te devolvemos el total por el mismo medio de pago.</p>
        )}
      </ModalConfirmacion>

      {puedeReprogramar && (
        <CambiarHorario
          abierto={reprogramar}
          onCerrar={() => setReprogramar(false)}
          reserva={r}
          onCambiado={(nueva) => {
            setReserva(nueva);
            setReprogramar(false);
            toast.mostrar("Cambiamos el horario");
          }}
        />
      )}

      {!esMenor && !esTutor && (
        <FormularioDenuncia
          abierto={reportar}
          onCerrar={() => setReportar(false)}
          onEnviada={() => toast.mostrar("Recibimos tu reporte. Lo vamos a revisar.")}
          denunciadoId={r.tutorId}
          nombre={r.tutorNombre ? nombreCorto(r.tutorNombre, r.tutorApellido) : undefined}
          sesionId={sesion?.id}
        />
      )}
    </div>
  );
}

function CambiarHorario({
  abierto,
  onCerrar,
  reserva,
  onCambiado,
}: {
  abierto: boolean;
  onCerrar: () => void;
  reserva: Reserva;
  onCambiado: (r: Reserva) => void;
}) {
  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [elegido, setElegido] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ahora = useAhora();

  useEffect(() => {
    if (!abierto || franjas) return;
    api
      .get<Franja[]>(`/api/tutores/${reserva.tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => setFranjas([]));
  }, [abierto, franjas, reserva.tutorId]);

  const opciones = useMemo(() => {
    if (!franjas) return [];
    const limite = ahora + TIEMPOS.ventanaMinimaReservaMinutos * 60000;
    return proximosDias(franjas, 14).flatMap((d) =>
      d.franjas
        .map((f) => ({ id: `${d.fecha}-${f.id}`, inicio: inicioISO(d.fecha, f.horaInicio), dia: d.referencia, f }))
        .filter((o) => new Date(o.inicio).getTime() > limite && o.inicio !== new Date(reserva.horario).toISOString())
    );
  }, [franjas, ahora, reserva.horario]);

  const menosDe24 = ahora > 0 && new Date(reserva.horario).getTime() - ahora < TIEMPOS.cancelacionSinPenalidadHoras * 3600000;

  async function guardar() {
    const o = opciones.find((x) => x.id === elegido);
    if (!o) return;
    setGuardando(true);
    setError(null);
    try {
      onCambiado(await api.post<Reserva>(`/api/reservas/${reserva.id}/reprogramar`, { nuevoHorario: o.inicio }));
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos cambiar el horario."));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      variante="hoja"
      titulo="Cambiar horario"
      descripcion="Elegí otro horario publicado por el tutor."
      pie={
        <>
          <Boton variante="secundario" onClick={onCerrar}>
            Volver
          </Boton>
          <Boton disabled={!elegido} cargando={guardando} textoCargando="Guardando…" onClick={guardar}>
            Confirmar nuevo horario
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-3 pb-2">
        {menosDe24 && (
          <Alerta tono="aviso">
            Faltan menos de {TIEMPOS.cancelacionSinPenalidadHoras} horas para esta clase: el cambio se trata como cancelación tardía.
          </Alerta>
        )}
        {franjas === null ? (
          <p role="status" className="text-sm text-tinta-tenue">
            Cargando horarios…
          </p>
        ) : opciones.length === 0 ? (
          <p className="text-[15px] text-tinta-suave">El tutor no tiene otros horarios publicados en las próximas dos semanas.</p>
        ) : (
          <ul className="flex list-none flex-col gap-2 p-0" aria-label="Horarios disponibles">
            {opciones.map((o) => (
              <li key={o.id}>
                <button
                  type="button"
                  aria-pressed={elegido === o.id}
                  onClick={() => setElegido(o.id)}
                  className={cn(
                    "flex min-h-14 w-full cursor-pointer items-center justify-between rounded-2xl border-2 px-4 text-left",
                    elegido === o.id ? "border-tinta" : "border-borde hover:border-borde-fuerte"
                  )}
                >
                  <span className="font-semibold capitalize">{diaCorto(o.dia)}</span>
                  <span className="text-tinta-suave">
                    {hhmm(o.f.horaInicio)} a {hhmm(o.f.horaFin)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {error && <Alerta tono="peligro">{error}</Alerta>}
      </div>
    </Modal>
  );
}
