"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { CalendarCheck2, CheckCircle2, Clock, ExternalLink, FlaskConical, ShieldCheck, TimerOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { duracionLegible, fechaHoraLarga, formatearPesos } from "@/lib/formatos";
import { finDe, type Reserva } from "@/lib/reservas";
import { TIEMPOS } from "@/lib/tiempos";
import { useAhora } from "@/lib/useAhora";
import AppShell from "@/components/shell/AppShell";
import FormularioSoporte from "@/components/FormularioSoporte";
import { Alerta, Boton, SkeletonPerfil, Tarjeta, clasesBoton } from "@/components/ui";

interface Preferencia {
  preferenciaId: string;
  initPoint: string;
  bypass: boolean;
}

type Estado = "cargando" | "listo" | "error" | "simulado" | "redirigiendo" | "esperandoAviso";

function Cuenta({ vence }: { vence: string }) {
  const ahora = useAhora(1000);
  if (!ahora) return null;
  const resto = Math.max(0, new Date(vence).getTime() - ahora);
  const m = Math.floor(resto / 60000);
  const s = Math.floor((resto % 60000) / 1000);
  const urgente = resto < 3 * 60000;
  return (
    <p className={`tabular flex items-center gap-2 text-sm font-semibold ${urgente ? "text-peligro" : "text-tinta-suave"}`}>
      <Clock className="size-4" aria-hidden />
      {resto === 0 ? "El tiempo para pagar se agotó" : `Tenés ${m}:${String(s).padStart(2, "0")} para pagar`}
    </p>
  );
}

function Pantalla({ icono, titulo, children, acciones }: { icono: React.ReactNode; titulo: string; children: React.ReactNode; acciones: React.ReactNode }) {
  return (
    <section className="mx-auto max-w-md py-8 text-center motion-safe:animate-subir">
      <span aria-hidden className="mx-auto flex size-20 items-center justify-center rounded-full bg-marca-50 text-marca-700 ring-8 ring-marca-50/50 [&>svg]:size-9">
        {icono}
      </span>
      <h1 className="mt-8 text-[28px] font-extrabold">{titulo}</h1>
      <div className="mt-3 text-[16px] text-tinta-suave">{children}</div>
      <div className="mt-8 flex flex-col gap-3">{acciones}</div>
    </section>
  );
}

function PagarFlujo() {
  const router = useRouter();
  const params = useSearchParams();
  const reservaId = params.get("reserva");
  // Si MercadoPago devuelve al usuario con su estado (collection_status/status).
  const vueltaMp = params.get("collection_status") ?? params.get("status");

  const [estado, setEstado] = useState<Estado>("cargando");
  const [error, setError] = useState<string | null>(null);
  const [reserva, setReserva] = useState<Reserva | null>(null);
  const [preferencia, setPreferencia] = useState<Preferencia | null>(null);

  // B9: sin reserva no hay nada que pagar.
  useEffect(() => {
    if (!reservaId) router.replace("/cuenta/reservas");
  }, [reservaId, router]);

  const cargar = useCallback(async () => {
    if (!reservaId) return;
    setEstado("cargando");
    setError(null);
    let r: Reserva | null = null;
    try {
      r = await api.get<Reserva>(`/api/reservas/${reservaId}`);
      setReserva(r);
    } catch {
      /* sin detalle igual se intenta el pago: el backend valida */
    }
    // Ya confirmada o ya vencida: no se genera un pago nuevo.
    if (r && r.estado && r.estado !== "pendiente_pago") {
      setEstado("listo");
      return;
    }
    // Vuelve de MercadoPago con el pago aprobado o en proceso: el aviso (webhook) puede
    // tardar unos segundos. No se ofrece pagar de nuevo mientras tanto (cobro doble).
    if (vueltaMp === "approved" || vueltaMp === "pending" || vueltaMp === "in_process") {
      setEstado("esperandoAviso");
      // Con el pago aprobado, no se depende solo del aviso de MercadoPago: el backend
      // consulta ese pago a MP y confirma la reserva si corresponde. Si falla, el
      // polling sigue esperando el webhook.
      const pagoId = params.get("payment_id") ?? params.get("collection_id");
      if (vueltaMp === "approved" && pagoId) {
        api.post("/api/pagos/confirmar-retorno", { reservaId, paymentId: pagoId }).catch(() => {});
      }
      return;
    }
    try {
      setPreferencia(await api.post<Preferencia>("/api/pagos/preferencia", { reservaId }));
      setEstado("listo");
    } catch (err) {
      setEstado("error");
      setError(err instanceof ApiError && err.message ? err.message : "No pudimos generar el pago. Probá de nuevo.");
    }
  }, [reservaId, vueltaMp, params]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  // Mientras se espera el aviso de MercadoPago, se consulta la reserva cada 3 s.
  useEffect(() => {
    if (estado !== "esperandoAviso" || !reservaId) return;
    const id = window.setInterval(async () => {
      try {
        const r = await api.get<Reserva>(`/api/reservas/${reservaId}`);
        setReserva(r);
        if (r.estado !== "pendiente_pago") setEstado("listo");
      } catch {
        /* se reintenta en la próxima vuelta */
      }
    }, 3000);
    return () => window.clearInterval(id);
  }, [estado, reservaId]);

  if (!reservaId) return null;
  if (estado === "cargando") return <SkeletonPerfil etiqueta="Preparando el pago…" />;

  const conQuien = reserva?.tutorNombre ? `con ${reserva.tutorNombre}` : "";

  if (reserva?.estado === "confirmada" || reserva?.estado === "en_curso") {
    return (
      <Pantalla
        icono={<CheckCircle2 />}
        titulo="¡Clase reservada!"
        acciones={
          <>
            <Link href={`/cuenta/reservas/${reserva.id}`} className={clasesBoton("primario", "lg", "w-full")}>Ver mi clase</Link>
            <Link href="/cuenta/reservas" className={clasesBoton("secundario", "lg", "w-full")}>Ir a Mis clases</Link>
          </>
        }
      >
        Tu clase {conQuien} es el <span className="font-semibold text-tinta">{fechaHoraLarga(reserva.horario, finDe(reserva))}</span>. El aula se abre {TIEMPOS.salaAbreMinutosAntes} minutos antes.
      </Pantalla>
    );
  }

  // Plazo vencido pero el job todavía no la canceló: igual no se ofrece pagar.
  const vencida = reserva?.estado === "pendiente_pago" && reserva.puedePagar === false;

  if (reserva?.estado === "cancelada" || vencida) {
    return (
      <Pantalla
        icono={<TimerOff />}
        titulo={vencida || reserva.motivoCancelacion === "timeout_pago" ? "El tiempo para pagar se agotó" : "Esta reserva se canceló"}
        acciones={
          <Link href={`/reservar?tutor=${reserva.tutorId}`} className={clasesBoton("primario", "lg", "w-full")}>Elegir otro horario</Link>
        }
      >
        {vencida || reserva.motivoCancelacion === "timeout_pago"
          ? `Pasaron más de ${TIEMPOS.pagoMinutos} minutos sin pago y el horario se liberó. No se te cobró nada.`
          : "No hay nada para pagar."}
      </Pantalla>
    );
  }

  if (estado === "esperandoAviso") {
    return (
      <Pantalla
        icono={<Clock />}
        titulo={vueltaMp === "approved" ? "¡Pago recibido! Confirmando tu clase…" : "MercadoPago está procesando el pago"}
        acciones={<Link href="/cuenta/reservas" className={clasesBoton("secundario", "lg", "w-full")}>Ir a Mis clases</Link>}
      >
        {vueltaMp === "approved"
          ? "Esto tarda unos segundos. No hace falta que pagues de nuevo: esta pantalla se actualiza sola."
          : "Apenas se acredite, tu clase queda confirmada. No hace falta que pagues de nuevo."}
      </Pantalla>
    );
  }

  if (estado === "simulado") {
    return (
      <Pantalla
        icono={<FlaskConical />}
        titulo="Reserva confirmada en modo simulado"
        acciones={<Link href="/cuenta/reservas" className={clasesBoton("primario", "lg", "w-full")}>Ir a Mis clases</Link>}
      >
        No se procesó ningún cobro: la pasarela está en modo de prueba.
      </Pantalla>
    );
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="text-[28px] font-extrabold sm:text-[36px]">Pago de la reserva</h1>

      {vueltaMp === "pending" || vueltaMp === "in_process" ? (
        <Alerta tono="info" className="mt-6" titulo="MercadoPago está procesando el pago">
          Apenas se acredite, tu clase queda confirmada. No hace falta que pagues de nuevo.
        </Alerta>
      ) : vueltaMp === "rejected" || vueltaMp === "failure" ? (
        <Alerta tono="peligro" className="mt-6" titulo="El pago fue rechazado">
          No se te cobró nada. Podés probar de nuevo con otro medio de pago.
        </Alerta>
      ) : null}

      {reserva && (
        <Tarjeta className="mt-6 flex flex-col gap-4">
          <div className="flex items-start gap-3">
            <CalendarCheck2 className="mt-0.5 size-5 shrink-0 text-marca-700" aria-hidden />
            <div>
              <p className="font-bold">Clase {conQuien}</p>
              <p className="text-[15px] text-tinta-suave first-letter:uppercase">{fechaHoraLarga(reserva.horario, finDe(reserva))}</p>
              {reserva.duracionMinutos ? <p className="text-sm text-tinta-tenue">{duracionLegible(reserva.duracionMinutos)}</p> : null}
            </div>
          </div>
          {reserva.precio !== null && (
            <div className="flex flex-col gap-2 border-t border-borde pt-4">
              {reserva.resumenContratado && reserva.precioAdicionalResumen != null && (
                <>
                  <div className="flex justify-between text-[15px] text-tinta-suave">
                    <span>Clase</span>
                    <span className="tabular">{formatearPesos(reserva.precio)}</span>
                  </div>
                  <div className="flex justify-between text-[15px] text-tinta-suave">
                    <span>Resumen automático</span>
                    <span className="tabular">{formatearPesos(reserva.precioAdicionalResumen)}</span>
                  </div>
                </>
              )}
              <div className="flex items-baseline justify-between">
                <span className="text-[15px] text-tinta-suave">Total</span>
                <span className="tabular text-3xl font-extrabold">{formatearPesos(reserva.montoTotal ?? reserva.precio)}</span>
              </div>
            </div>
          )}
          {reserva.pagoVenceAt && <Cuenta vence={reserva.pagoVenceAt} />}
        </Tarjeta>
      )}

      {estado === "error" && (
        <Alerta
          tono="peligro"
          className="mt-6"
          accion={
            <Boton variante="secundario" tamano="sm" onClick={() => void cargar()}>
              Reintentar
            </Boton>
          }
        >
          {error}
          <FormularioSoporte
            origenModulo="M5.pago_fallido"
            asunto={`No se pudo generar el pago de la reserva ${reservaId}`}
            detalleInicial={`Reserva ${reservaId}: ${error ?? "no se pudo generar el pago."}`}
          />
        </Alerta>
      )}

      {estado !== "error" && preferencia && (
        <div className="mt-6 flex flex-col gap-4">
          {preferencia.bypass ? (
            <Alerta tono="aviso" rol="alert" titulo="Modo de prueba">
              La pasarela de pagos está deshabilitada: tu reserva se confirmará sin procesar un cobro real. No se debitará ningún monto.
            </Alerta>
          ) : (
            <p className="flex gap-2.5 text-sm text-tinta-suave">
              <ShieldCheck className="size-5 shrink-0 text-marca-700" aria-hidden />
              El dinero queda retenido y se le libera al tutor {TIEMPOS.liberacionHoras} hs después de la clase. Si cancelás con más de {TIEMPOS.cancelacionSinPenalidadHoras} hs, te lo devolvemos entero.
            </p>
          )}
          <Boton
            tamano="lg"
            anchoCompleto
            icono={preferencia.bypass ? undefined : <ExternalLink />}
            cargando={estado === "redirigiendo"}
            textoCargando="Yendo a MercadoPago…"
            onClick={() => {
              if (preferencia.bypass) {
                setEstado("simulado");
              } else {
                setEstado("redirigiendo");
                window.location.assign(preferencia.initPoint);
              }
            }}
          >
            {preferencia.bypass ? "Confirmar reserva (simulado)" : "Pagar con MercadoPago"}
          </Boton>
          {!preferencia.bypass && (
            <p className="text-center text-[13px] text-tinta-tenue">Vas a salir de Tinku y continuar en el sitio de MercadoPago.</p>
          )}
        </div>
      )}

      <p className="mt-8 text-center text-[15px]">
        <Link href="/cuenta/reservas" className="font-semibold">
          Volver a Mis clases
        </Link>
      </p>
    </div>
  );
}

export default function PagarPage() {
  return (
    <AppShell>
      <Suspense fallback={<SkeletonPerfil etiqueta="Cargando…" />}>
        <PagarFlujo />
      </Suspense>
    </AppShell>
  );
}

