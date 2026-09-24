"use client";

import { Suspense, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { CalendarX2, Check, ChevronLeft, Clock, Copy, Send, ShieldCheck, UserRound, UsersRound } from "lucide-react";
import { api, ApiError, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import { useAhora } from "@/lib/useAhora";
import { diaCorto, duracionLegible, fechaHoraLarga } from "@/lib/formatos";
import { TIEMPOS } from "@/lib/tiempos";
import { getTutor, nombreCorto, useFotoTutor, type TutorPerfil } from "@/lib/tutores";
import { duracionFranja, hhmm, inicioISO, proximosDias, type Franja } from "@/lib/agenda";
import { cn } from "@/lib/cn";
import AppShell from "@/components/shell/AppShell";
import { Alerta, Avatar, Boton, EstadoVacio, Pasos, Precio, Skeleton, SkeletonPerfil, Tarjeta, clasesBoton } from "@/components/ui";

interface Horario {
  franja: Franja;
  inicio: string;
  disponible: boolean;
}

interface TimeSlot {
  startTime: string;
  isAvailable: boolean;
}

function ReservarFlujo() {
  const router = useRouter();
  const tutorId = useSearchParams().get("tutor");
  const sesion = useSesion();
  const payload = sesion?.payload;
  const esMenor = payload?.tipo === "MENOR";
  const esAR = !esMenor && payload?.cap_ar === true;

  const [tutor, setTutor] = useState<TutorPerfil | null>(null);
  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [errorCarga, setErrorCarga] = useState<string | null>(null);

  const [paso, setPaso] = useState(0);
  const [dia, setDia] = useState<string | null>(null);
  const [ocupacion, setOcupacion] = useState<Record<string, TimeSlot[] | null>>({});
  const [elegido, setElegido] = useState<Horario | null>(null);
  const [tomados, setTomados] = useState<Set<string>>(new Set());

  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [paraQuien, setParaQuien] = useState<string>("yo");

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pedidoEnviado, setPedidoEnviado] = useState(false);

  // B9: sin tutor no hay nada que reservar — a buscar.
  useEffect(() => {
    if (!tutorId) router.replace("/buscar");
  }, [tutorId, router]);

  const cargar = useCallback(() => {
    if (!tutorId) return;
    setErrorCarga(null);
    getTutor(tutorId)
      .then(setTutor)
      .catch((err) =>
        setErrorCarga(err instanceof ApiError && err.status === 404 ? "No encontramos a este tutor." : "No pudimos cargar al tutor.")
      );
    api
      .get<Franja[]>(`/api/tutores/${tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => setFranjas([]));
  }, [tutorId]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  useEffect(() => {
    if (!esAR) return;
    getMenores()
      .then(setMenores)
      .catch(() => setMenores([]));
  }, [esAR]);

  const ahora = useAhora();
  const dias = useMemo(() => (franjas ? proximosDias(franjas, 14) : []), [franjas]);
  const diasConHorario = dias.filter((d) => d.franjas.length > 0);

  // Primer día con disponibilidad, elegido de entrada.
  useEffect(() => {
    if (!dia && diasConHorario[0]) setDia(diasConHorario[0].fecha);
  }, [dia, diasConHorario]);

  // Ocupación real del día (reservas existentes + ventana mínima): la calcula el backend.
  useEffect(() => {
    if (!dia || !tutorId || dia in ocupacion) return;
    setOcupacion((o) => ({ ...o, [dia]: null }));
    api
      .get<TimeSlot[]>(`/api/tutores/${tutorId}/horarios?fecha=${dia}&duracionMinutos=30`)
      .then((slots) => setOcupacion((o) => ({ ...o, [dia]: slots })))
      .catch(() => setOcupacion((o) => ({ ...o, [dia]: [] })));
  }, [dia, tutorId, ocupacion]);

  const horariosDelDia: Horario[] = useMemo(() => {
    const d = dias.find((x) => x.fecha === dia);
    if (!d) return [];
    const slots = ocupacion[dia ?? ""];
    const limite = ahora + TIEMPOS.ventanaMinimaReservaMinutos * 60000;
    return d.franjas.map((f) => {
      const inicio = inicioISO(d.fecha, f.horaInicio);
      const slot = slots?.find((s) => new Date(s.startTime).getTime() === new Date(inicio).getTime());
      const disponible =
        new Date(inicio).getTime() > limite && !tomados.has(inicio) && (slot ? slot.isAvailable : true);
      return { franja: f, inicio, disponible };
    });
  }, [dias, dia, ocupacion, tomados, ahora]);

  const pasos = esMenor ? ["Cuándo", "Pedido"] : esAR ? ["Cuándo", "Para quién", "Confirmar"] : ["Cuándo", "Confirmar"];
  const pasoConfirmar = pasos.length - 1;
  const beneficiario = paraQuien === "yo" ? null : (menores?.find((m) => m.id === paraQuien) ?? null);
  const puedeParaMi = payload?.cap_est !== false;

  useEffect(() => {
    if (esAR && !puedeParaMi && menores?.[0] && paraQuien === "yo") setParaQuien(menores[0].id);
  }, [esAR, puedeParaMi, menores, paraQuien]);

  async function confirmar() {
    if (!elegido || !tutorId) return;
    setEnviando(true);
    setError(null);
    try {
      if (esMenor) {
        await api.post("/api/solicitudes", { tutorId, horarioPropuesto: elegido.inicio });
        setPedidoEnviado(true);
      } else {
        const reserva = await api.post<{ id: string }>("/api/reservas", {
          tutorId,
          horario: elegido.inicio,
          ...(beneficiario ? { beneficiarioId: beneficiario.id } : {}),
        });
        router.replace(`/pagar?reserva=${reserva.id}`);
      }
    } catch (err) {
      if (err instanceof ApiError && err.status === 409 && !/menores/i.test(err.message)) {
        // El horario se ocupó mientras decidía: vuelve al paso 1 con ese horario tachado.
        setTomados((t) => new Set(t).add(elegido.inicio));
        setOcupacion((o) => {
          const copia = { ...o };
          if (dia) delete copia[dia];
          return copia;
        });
        setElegido(null);
        setPaso(0);
        setError("Ese horario se acaba de ocupar. Elegí otro.");
      } else {
        setError(mensajeDeError(err, "No pudimos completar la reserva. Probá de nuevo."));
      }
    } finally {
      setEnviando(false);
    }
  }

  if (!tutorId) return null;

  if (errorCarga) {
    return (
      <EstadoVacio
        icono={<CalendarX2 />}
        titulo="No podemos reservar ahora"
        accion={
          <div className="flex gap-2">
            <Link href="/buscar" className={clasesBoton("secundario")}>Volver a buscar</Link>
            <Boton onClick={cargar}>Probar de nuevo</Boton>
          </div>
        }
      >
        {errorCarga}
      </EstadoVacio>
    );
  }

  if (!tutor || !franjas) return <SkeletonPerfil etiqueta="Cargando horarios…" />;

  if (pedidoEnviado) {
    return (
      <section className="mx-auto max-w-md py-10 text-center motion-safe:animate-subir">
        <span aria-hidden className="mx-auto flex size-20 items-center justify-center rounded-full bg-marca-50 text-marca-700 ring-8 ring-marca-50/50">
          <Send className="size-9" />
        </span>
        <h1 className="mt-8 text-[28px] font-extrabold">¡Pedido enviado!</h1>
        <p className="mt-3 text-[16px] text-tinta-suave">
          Le avisamos a tu adulto responsable. Vas a ver la clase en Mis clases cuando la apruebe.
        </p>
        <Link href="/cuenta/reservas" className={clasesBoton("primario", "lg", "mt-8 w-full")}>
          Ir a Mis clases
        </Link>
      </section>
    );
  }

  const nombreTutor = `${tutor.nombre} ${tutor.apellido}`.trim();

  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-[1fr_340px] lg:items-start">
      <div className="min-w-0">
        <Link
          href={`/tutores/${tutor.id}`}
          className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida"
        >
          <ChevronLeft className="size-5" aria-hidden /> {nombreTutor}
        </Link>
        <h1 className="text-[28px] font-extrabold sm:text-[36px]">{esMenor ? "Pedir una clase" : "Reservar una clase"}</h1>
        <Pasos pasos={pasos} actual={paso} className="mt-6" />

        {esMenor && <MensajeParaAdulto nombreTutor={nombreTutor} />}

        {error && (
          <Alerta tono="peligro" className="mt-6">
            {error}
          </Alerta>
        )}

        {/* ---------- Cuándo ---------- */}
        {paso === 0 && (
          <section className="mt-8">
            <h2 className="text-xl font-bold">¿Qué día?</h2>
            {diasConHorario.length === 0 ? (
              <EstadoVacio icono={<CalendarX2 />} titulo="Sin horarios por ahora" className="py-8">
                {nombreCorto(tutor.nombre, tutor.apellido)} no tiene horarios publicados en las próximas dos semanas.
              </EstadoVacio>
            ) : (
              <>
                <div role="radiogroup" aria-label="Día de la clase" className="no-scrollbar -mx-4 mt-4 flex gap-2 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
                  {dias.map((d) => {
                    const activo = d.fecha === dia;
                    const hay = d.franjas.length > 0;
                    const [nombreDia, num, mes] = diaCorto(d.referencia).split(" ");
                    return (
                      <button
                        key={d.fecha}
                        type="button"
                        role="radio"
                        aria-checked={activo}
                        disabled={!hay}
                        aria-label={`${fechaHoraLarga(d.referencia).split(",")[0]}${hay ? "" : ", sin horarios"}`}
                        onClick={() => {
                          setDia(d.fecha);
                          setElegido(null);
                        }}
                        className={cn(
                          "flex min-h-20 w-16 shrink-0 cursor-pointer flex-col items-center justify-center gap-0.5 rounded-2xl border text-center transition-colors disabled:cursor-not-allowed disabled:opacity-40",
                          activo ? "border-tinta bg-tinta text-white" : "border-borde bg-superficie hover:border-borde-fuerte"
                        )}
                      >
                        <span className="text-[12px] font-semibold uppercase">{nombreDia}</span>
                        <span className="text-xl font-extrabold">{num}</span>
                        <span className="text-[12px]">{mes}</span>
                      </button>
                    );
                  })}
                </div>

                <h2 className="mt-8 text-xl font-bold">¿A qué hora?</h2>
                {dia && ocupacion[dia] === null ? (
                  <div role="status" className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
                    <span className="sr-only">Cargando horarios…</span>
                    <Skeleton className="h-16 rounded-2xl" />
                    <Skeleton className="h-16 rounded-2xl" />
                  </div>
                ) : (
                  <ul className="mt-4 grid list-none grid-cols-1 gap-2 p-0 sm:grid-cols-2">
                    {horariosDelDia.map((h) => {
                      const activo = elegido?.inicio === h.inicio;
                      const minutosFranja = duracionFranja(h.franja);
                      return (
                        <li key={h.franja.id}>
                          <button
                            type="button"
                            aria-pressed={activo}
                            disabled={!h.disponible}
                            onClick={() => setElegido(h)}
                            className={cn(
                              "flex min-h-16 w-full cursor-pointer items-center justify-between gap-3 rounded-2xl border-2 px-4 text-left transition-colors disabled:cursor-not-allowed",
                              activo ? "border-tinta bg-superficie shadow-elevado" : "border-borde bg-superficie hover:border-borde-fuerte",
                              !h.disponible && "bg-superficie-hundida text-tinta-tenue"
                            )}
                          >
                            <span>
                              <span className={cn("block text-[16px] font-bold", !h.disponible && "line-through")}>
                                {hhmm(h.franja.horaInicio)} a {hhmm(h.franja.horaFin)}
                              </span>
                              <span className="block text-sm text-tinta-tenue">
                                {h.disponible ? duracionLegible(minutosFranja) : "No disponible"}
                              </span>
                            </span>
                            {activo && (
                              <span aria-hidden className="flex size-6 items-center justify-center rounded-full bg-tinta text-white">
                                <Check className="size-4" />
                              </span>
                            )}
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </>
            )}
            <Boton tamano="lg" anchoCompleto className="mt-8" disabled={!elegido} onClick={() => setPaso(1)}>
              Continuar
            </Boton>
          </section>
        )}

        {/* ---------- Para quién (Adulto Responsable) ---------- */}
        {esAR && paso === 1 && (
          <section className="mt-8">
            <h2 className="text-xl font-bold">¿Para quién es la clase?</h2>
            <div role="radiogroup" aria-label="Para quién es la clase" className="mt-4 flex flex-col gap-2">
              {puedeParaMi && (
                <OpcionBeneficiario activo={paraQuien === "yo"} onClick={() => setParaQuien("yo")} icono={<UserRound />} titulo="Para mí" />
              )}
              {menores === null && <Skeleton className="h-16 rounded-2xl" />}
              {(menores ?? []).map((m) => (
                <OpcionBeneficiario
                  key={m.id}
                  activo={paraQuien === m.id}
                  onClick={() => setParaQuien(m.id)}
                  icono={<UsersRound />}
                  titulo={`Para ${m.nombre}`}
                  detalle={`${nombreCorto(tutor.nombre, tutor.apellido)} tiene que estar autorizado para ${m.nombre}.`}
                />
              ))}
            </div>
            {menores !== null && menores.length === 0 && !puedeParaMi && (
              <Alerta tono="info" className="mt-4" accion={<Link href="/cuenta/menores" className={clasesBoton("secundario", "sm")}>Sumar a mi hijo o hija</Link>}>
                Todavía no sumaste a ningún hijo o hija.
              </Alerta>
            )}
            <div className="mt-8 flex gap-3">
              <Boton variante="secundario" tamano="lg" onClick={() => setPaso(0)} aria-label="Volver al paso anterior">
                <ChevronLeft className="size-5" aria-hidden />
              </Boton>
              <Boton tamano="lg" className="flex-1" disabled={!puedeParaMi && !beneficiario} onClick={() => setPaso(2)}>
                Continuar
              </Boton>
            </div>
          </section>
        )}

        {/* ---------- Confirmar ---------- */}
        {paso === pasoConfirmar && elegido && (
          <section className="mt-8">
            <h2 className="text-xl font-bold">{esMenor ? "Revisá tu pedido" : "Revisá y confirmá"}</h2>
            <Tarjeta className="mt-4 flex flex-col gap-4">
              <dl className="flex flex-col gap-3 text-[15px]">
                <Dato titulo="Tutor">{nombreTutor}</Dato>
                <Dato titulo="Cuándo">
                  <span className="first-letter:uppercase">{fechaHoraLarga(elegido.inicio, inicioISO(elegido.franja.fechaEspecifica?.slice(0, 10) ?? dia!, elegido.franja.horaFin))}</span>
                </Dato>
                <Dato titulo="Duración">{duracionLegible(duracionFranja(elegido.franja))}</Dato>
                {esAR && <Dato titulo="Para">{beneficiario ? beneficiario.nombre : "Vos"}</Dato>}
                <Dato titulo="Precio">
                  <Precio valor={tutor.precioSesion} sinValor="Se calcula al reservar" />
                </Dato>
              </dl>
              {!esMenor && (
                <p className="flex gap-2.5 rounded-2xl bg-fondo p-3.5 text-sm text-tinta-suave">
                  <ShieldCheck className="size-5 shrink-0 text-marca-700" aria-hidden />
                  Pagás ahora (tenés {TIEMPOS.pagoMinutos} minutos); el dinero queda retenido y se le libera al tutor {TIEMPOS.liberacionHoras} hs después de la clase.
                </p>
              )}
            </Tarjeta>
            <div className="mt-8 flex gap-3">
              <Boton variante="secundario" tamano="lg" onClick={() => setPaso(paso - 1)} aria-label="Volver al paso anterior">
                <ChevronLeft className="size-5" aria-hidden />
              </Boton>
              <Boton tamano="lg" className="flex-1" cargando={enviando} textoCargando={esMenor ? "Enviando…" : "Reservando…"} onClick={confirmar}>
                {esMenor ? "Enviarle el pedido a mi adulto responsable" : "Confirmar y pagar"}
              </Boton>
            </div>
          </section>
        )}
      </div>

      <ResumenLateral tutor={tutor} elegido={elegido} />
    </div>
  );
}

function Dato({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-tinta-tenue">{titulo}</dt>
      <dd className="m-0 text-right font-semibold text-tinta">{children}</dd>
    </div>
  );
}

function OpcionBeneficiario({
  activo,
  onClick,
  icono,
  titulo,
  detalle,
}: {
  activo: boolean;
  onClick: () => void;
  icono: React.ReactNode;
  titulo: string;
  detalle?: string;
}) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={activo}
      onClick={onClick}
      className={cn(
        "flex min-h-16 cursor-pointer items-center gap-4 rounded-2xl border-2 bg-superficie p-4 text-left",
        activo ? "border-tinta shadow-elevado" : "border-borde hover:border-borde-fuerte"
      )}
    >
      <span aria-hidden className="flex size-10 shrink-0 items-center justify-center rounded-full bg-marca-50 text-marca-700 [&>svg]:size-5">
        {icono}
      </span>
      <span className="flex-1">
        <span className="block font-bold">{titulo}</span>
        {detalle && <span className="block text-sm text-tinta-suave">{detalle}</span>}
      </span>
      {activo && <Check className="size-5" aria-hidden />}
    </button>
  );
}

function ResumenLateral({ tutor, elegido }: { tutor: TutorPerfil; elegido: Horario | null }) {
  const foto = useFotoTutor(tutor.id, tutor.tieneFoto);
  return (
    <aside className="hidden lg:sticky lg:top-24 lg:block">
      <Tarjeta className="flex flex-col gap-4">
        <div className="flex items-center gap-3">
          <Avatar nombre={tutor.nombre} apellido={tutor.apellido} semilla={tutor.id} foto={foto} verificado={tutor.verificado} />
          <div>
            <p className="font-bold">{`${tutor.nombre} ${tutor.apellido}`}</p>
            <p className="text-sm text-tinta-tenue">{tutor.materias.slice(0, 2).join(" · ")}</p>
          </div>
        </div>
        <div className="flex items-baseline justify-between border-t border-borde pt-4">
          <span className="text-sm text-tinta-tenue">Precio por clase</span>
          <Precio valor={tutor.precioSesion} />
        </div>
        {elegido && (
          <p className="flex items-center gap-2 text-sm font-semibold">
            <Clock className="size-4 text-marca-700" aria-hidden />
            <span className="first-letter:uppercase">{fechaHoraLarga(elegido.inicio).replace(/, /, " · ")}</span>
          </p>
        )}
      </Tarjeta>
    </aside>
  );
}

/**
 * B5: el menor no se queda en un callejón. Durante el piloto (T-TES-10) las clases
 * para menores están cerradas: le queda un mensaje listo para mandarle a su adulto.
 */
function MensajeParaAdulto({ nombreTutor }: { nombreTutor: string }) {
  const [copiado, setCopiado] = useState(false);
  const mensaje = `Hola, encontré a ${nombreTutor} en Tinku para tomar clases. ¿Me la reservás?`;
  return (
    <Alerta
      tono="aviso"
      className="mt-6"
      titulo="Las clases para menores se habilitan al finalizar el piloto."
      accion={
        <Boton
          variante="secundario"
          tamano="sm"
          icono={copiado ? <Check /> : <Copy />}
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(mensaje);
              setCopiado(true);
            } catch {
              setCopiado(false);
            }
          }}
        >
          Copiar este mensaje
        </Boton>
      }
    >
      Mientras tanto, mandale este mensaje a tu adulto responsable: “{mensaje}”
      {copiado && (
        <span role="status" className="mt-1 block font-semibold">
          Mensaje copiado
        </span>
      )}
    </Alerta>
  );
}

export default function ReservarPage() {
  return (
    <AppShell>
      <Suspense fallback={<SkeletonPerfil etiqueta="Cargando…" />}>
        <ReservarFlujo />
      </Suspense>
    </AppShell>
  );
}
