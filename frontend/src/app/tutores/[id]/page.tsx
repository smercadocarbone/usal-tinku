"use client";

import { useParams } from "next/navigation";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { BadgeCheck, CalendarDays, ChevronLeft, Flag, IdCard, ShieldCheck, UserX } from "lucide-react";
import { api, ApiError, autorizarTutor, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import { diaCorto } from "@/lib/formatos";
import { TIEMPOS } from "@/lib/tiempos";
import { getTutor, nombreCorto, useFotoTutor, type TutorPerfil } from "@/lib/tutores";
import { hhmm, proximosDias, type Franja } from "@/lib/agenda";
import AppShell from "@/components/shell/AppShell";
import FormularioDenuncia from "@/components/FormularioDenuncia";
import {
  Alerta,
  Avatar,
  Boton,
  EstadoVacio,
  Estrellas,
  Insignia,
  Interruptor,
  Menu,
  Precio,
  Selector,
  Skeleton,
  SkeletonPerfil,
  Tarjeta,
  clasesBoton,
  useToast,
} from "@/components/ui";

function Seccion({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <section className="border-t border-borde pt-8">
      <h2 className="text-xl font-bold">{titulo}</h2>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export default function TutorPerfilPage() {
  const params = useParams<{ id: string }>();
  const sesion = useSesion();
  const payload = sesion?.payload;
  const toast = useToast();

  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [reportar, setReportar] = useState(false);

  const cargar = useCallback(() => {
    setCargando(true);
    setError(null);
    getTutor(params.id)
      .then(setPerfil)
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 404
            ? "No encontramos a este tutor. Puede que ya no esté dando clases en Tinku."
            : "No pudimos cargar el perfil. Revisá tu conexión y probá de nuevo."
        )
      )
      .finally(() => setCargando(false));
    api
      .get<Franja[]>(`/api/tutores/${params.id}/franjas`)
      .then(setFranjas)
      .catch(() => setFranjas([]));
  }, [params.id]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const foto = useFotoTutor(perfil?.id, perfil?.tieneFoto);
  const esMenor = payload?.tipo === "MENOR";
  const esAR = !esMenor && payload?.cap_ar === true;
  const nuevo = !perfil || perfil.calificacionPromedio === null || perfil.cantidadCalificaciones < TIEMPOS.minimoCalificaciones;
  const dias = franjas ? proximosDias(franjas, 7).filter((d) => d.franjas.length > 0) : null;
  const hrefReservar = `/reservar?tutor=${params.id}`;

  if (cargando) {
    return (
      <AppShell>
        <SkeletonPerfil />
      </AppShell>
    );
  }

  if (error || !perfil) {
    return (
      <AppShell>
        <EstadoVacio
          icono={<UserX />}
          titulo="No pudimos mostrar este perfil"
          accion={
            <div className="flex gap-2">
              <Link href="/buscar" className={clasesBoton("secundario")}>
                Volver a buscar
              </Link>
              <Boton onClick={cargar}>Probar de nuevo</Boton>
            </div>
          }
        >
          {error}
        </EstadoVacio>
      </AppShell>
    );
  }

  const nombre = `${perfil.nombre} ${perfil.apellido}`.trim();
  const cta = esMenor ? "Pedir esta clase" : "Reservar clase";

  return (
    <AppShell>
      <Link
        href="/buscar"
        className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida"
      >
        <ChevronLeft className="size-5" aria-hidden /> Volver a los resultados
      </Link>

      <div className="grid grid-cols-1 gap-10 lg:grid-cols-[1fr_340px] lg:items-start">
        <div className="flex flex-col gap-8">
          {/* Encabezado */}
          <header className="flex flex-col gap-5 sm:flex-row sm:items-center">
            <Avatar nombre={perfil.nombre} apellido={perfil.apellido} semilla={perfil.id} foto={foto} tamano="xl" verificado={perfil.verificado} />
            <div className="min-w-0 flex-1">
              <div className="flex items-start justify-between gap-3">
                <h1 className="text-[28px] font-extrabold sm:text-[36px]">{nombre}</h1>
                {!esMenor && (
                  <Menu
                    etiqueta="Más opciones de este perfil"
                    items={[{ texto: "Reportar este perfil", icono: <Flag />, peligro: true, onClick: () => setReportar(true) }]}
                  />
                )}
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                {nuevo ? (
                  <Insignia tono="acento">Tutor nuevo</Insignia>
                ) : (
                  <Estrellas valor={perfil.calificacionPromedio!} cantidad={perfil.cantidadCalificaciones} tamano="md" />
                )}
                {perfil.nivel && <Insignia>{perfil.nivel.charAt(0).toUpperCase() + perfil.nivel.slice(1)}</Insignia>}
              </div>
              <ul className="mt-4 flex list-none flex-wrap gap-x-5 gap-y-2 p-0 text-sm font-semibold">
                <li className="flex items-center gap-1.5 text-exito">
                  <IdCard className="size-4" aria-hidden /> Identidad verificada
                </li>
                {perfil.verificado ? (
                  <li className="flex items-center gap-1.5 text-exito">
                    <BadgeCheck className="size-4" aria-hidden /> Título verificado
                  </li>
                ) : (
                  <li className="flex items-center gap-1.5 text-tinta-tenue">
                    <BadgeCheck className="size-4" aria-hidden /> Título en revisión
                  </li>
                )}
                {perfil.habilitadoParaMenores && (
                  <li className="flex items-center gap-1.5 text-exito">
                    <ShieldCheck className="size-4" aria-hidden /> Habilitado para clases con menores
                  </li>
                )}
              </ul>
            </div>
          </header>

          {esMenor && (
            <Alerta tono="aviso" titulo="Las clases para menores se habilitan al finalizar el piloto.">
              Pedile a tu adulto responsable que te autorice a este tutor desde su cuenta.
            </Alerta>
          )}

          {perfil.bio && (
            <Seccion titulo="Sobre mí">
              <p className="whitespace-pre-line text-[16px] leading-relaxed text-tinta-suave">{perfil.bio}</p>
            </Seccion>
          )}

          {perfil.materias.length > 0 && (
            <Seccion titulo="Materias que enseña">
              <ul className="flex list-none flex-wrap gap-2 p-0">
                {perfil.materias.map((m) => (
                  <li key={m}>
                    <Insignia className="px-3 py-1.5 text-sm">{m}</Insignia>
                  </li>
                ))}
              </ul>
            </Seccion>
          )}

          <Seccion titulo="Disponibilidad esta semana">
            {dias === null ? (
              <div role="status" className="flex gap-2">
                <span className="sr-only">Cargando horarios…</span>
                {[1, 2, 3].map((i) => (
                  <Skeleton key={i} className="h-20 w-28 rounded-2xl" />
                ))}
              </div>
            ) : dias.length === 0 ? (
              <p className="flex items-center gap-2 text-[15px] text-tinta-suave">
                <CalendarDays className="size-5" aria-hidden /> No tiene horarios publicados en los próximos 7 días.
              </p>
            ) : (
              <ul className="no-scrollbar -mx-4 flex list-none gap-2 overflow-x-auto px-4 pb-1 sm:mx-0 sm:flex-wrap sm:px-0">
                {dias.map((d) => (
                  <li key={d.fecha} className="min-w-28 shrink-0 rounded-2xl border border-borde bg-superficie p-3">
                    <p className="text-sm font-bold capitalize">{diaCorto(d.referencia)}</p>
                    <p className="mt-1 text-[13px] text-tinta-suave">
                      {d.franjas.map((f) => `${hhmm(f.horaInicio)}–${hhmm(f.horaFin)}`).join(" · ")}
                    </p>
                  </li>
                ))}
              </ul>
            )}
          </Seccion>

          {esAR && <ParaTusChicos tutorId={perfil.id} nombreTutor={nombreCorto(perfil.nombre, perfil.apellido)} />}
        </div>

        {/* CTA: tarjeta lateral fija en desktop */}
        <aside className="hidden lg:sticky lg:top-24 lg:block">
          <Tarjeta className="flex flex-col gap-5">
            <div>
              <Precio valor={perfil.precioHora} tamano="lg" />
              {perfil.precioHora !== null && <p className="text-sm text-tinta-tenue">por hora</p>}
            </div>
            <Link href={hrefReservar} className={clasesBoton("primario", "lg", "w-full")}>
              {cta}
            </Link>
            <ul className="flex list-none flex-col gap-3 p-0 text-sm text-tinta-suave">
              <li className="flex gap-2.5">
                <ShieldCheck className="size-5 shrink-0 text-marca-700" aria-hidden />
                Pagás al reservar; al tutor se le libera {TIEMPOS.liberacionHoras} hs después de la clase.
              </li>
              <li className="flex gap-2.5">
                <CalendarDays className="size-5 shrink-0 text-marca-700" aria-hidden />
                Cancelás gratis hasta {TIEMPOS.cancelacionSinPenalidadHoras} hs antes.
              </li>
            </ul>
          </Tarjeta>
        </aside>
      </div>

      {/* CTA: barra inferior en mobile, encima de la navegación */}
      <div className="fixed inset-x-0 bottom-[calc(4rem+env(safe-area-inset-bottom))] z-20 border-t border-borde bg-superficie/95 px-4 py-3 shadow-barra backdrop-blur-md lg:hidden">
        <div className="mx-auto flex max-w-lg items-center justify-between gap-4">
          <div>
            <Precio valor={perfil.precioHora} tamano="md" />
            {perfil.precioHora !== null && <p className="text-[13px] text-tinta-tenue">por hora</p>}
          </div>
          <Link href={hrefReservar} className={clasesBoton("primario", "lg", "flex-1 max-w-56")}>
            {cta}
          </Link>
        </div>
      </div>
      <div aria-hidden className="h-20 lg:hidden" />

      {!esMenor && (
        <FormularioDenuncia
          abierto={reportar}
          onCerrar={() => setReportar(false)}
          onEnviada={() => toast.mostrar("Denuncia registrada. La vamos a revisar.")}
          denunciadoId={perfil.id}
          nombre={nombreCorto(perfil.nombre, perfil.apellido)}
        />
      )}
    </AppShell>
  );
}

/** Adulto Responsable: autorizar a este tutor para un hijo, o marcarlo como no confiable (FR-ID-009). */
function ParaTusChicos({ tutorId, nombreTutor }: { tutorId: string; nombreTutor: string }) {
  const toast = useToast();
  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [errorMenores, setErrorMenores] = useState<string | null>(null);
  const [menorElegido, setMenorElegido] = useState("");
  const [autorizando, setAutorizando] = useState(false);
  const [mensaje, setMensaje] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [noConfiable, setNoConfiable] = useState(false);
  const [guardandoConfianza, setGuardandoConfianza] = useState(false);

  useEffect(() => {
    getMenores()
      .then((lista) => {
        setMenores(lista);
        if (lista[0]) setMenorElegido(lista[0].id);
      })
      .catch((err) => setErrorMenores(mensajeDeError(err, "No pudimos cargar a tus chicos.")));
  }, []);

  async function autorizar() {
    if (!menorElegido) return;
    setAutorizando(true);
    setMensaje(null);
    setError(null);
    try {
      await autorizarTutor(menorElegido, tutorId);
      const m = menores?.find((x) => x.id === menorElegido);
      setMensaje(m ? `Autorizaste a este tutor para ${m.nombre}.` : "Tutor autorizado.");
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos autorizar al tutor."));
    } finally {
      setAutorizando(false);
    }
  }

  async function cambiarConfianza(valor: boolean) {
    setGuardandoConfianza(true);
    try {
      await api.patch("/api/autorizaciones/no-confiable", { tutorId, noConfiable: valor });
      setNoConfiable(valor);
      toast.mostrar(valor ? "Listo: ya no aparece en las búsquedas de tus chicos." : "Volvió a aparecer en las búsquedas de tus chicos.");
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar el cambio."), { tono: "error" });
    } finally {
      setGuardandoConfianza(false);
    }
  }

  return (
    <Seccion titulo="Para tus chicos">
      <div className="flex flex-col gap-6 rounded-tarjeta border border-borde bg-superficie p-5">
        <div>
          <p className="text-[15px] text-tinta-suave">
            Tus hijos solo pueden pedir clases a tutores que vos autorizaste.
          </p>
          <div className="mt-4">
            {errorMenores && <Alerta tono="peligro">{errorMenores}</Alerta>}
            {!errorMenores && menores === null && <Skeleton className="h-12 w-full rounded-control" />}
            {menores !== null && menores.length === 0 && (
              <p className="text-[15px] text-tinta-suave">
                Todavía no diste de alta a ningún menor.{" "}
                <Link href="/cuenta/menores" className="font-semibold">
                  Sumá a tu hijo o hija
                </Link>
                .
              </p>
            )}
            {menores !== null && menores.length > 0 && (
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="flex-1">
                  <Selector id="menorAAutorizar" etiqueta="Menor" value={menorElegido} onChange={(e) => setMenorElegido(e.target.value)}>
                    {menores.map((m) => (
                      <option key={m.id} value={m.id}>
                        {m.nombre} {m.apellido}
                      </option>
                    ))}
                  </Selector>
                </div>
                <Boton cargando={autorizando} textoCargando="Autorizando…" onClick={autorizar}>
                  Autorizar para este menor
                </Boton>
              </div>
            )}
            {mensaje && <Alerta tono="exito" className="mt-3">{mensaje}</Alerta>}
            {error && <Alerta tono="peligro" className="mt-3">{error}</Alerta>}
          </div>
        </div>
        <div className="border-t border-borde pt-5">
          <Interruptor
            id="no-confiable"
            etiqueta="Marcar como no confiable"
            descripcion={`${nombreTutor} deja de aparecer en las búsquedas de tus chicos. Es privado: no se le avisa a nadie.`}
            activo={noConfiable}
            disabled={guardandoConfianza}
            onCambio={cambiarConfianza}
          />
        </div>
      </div>
    </Seccion>
  );
}

