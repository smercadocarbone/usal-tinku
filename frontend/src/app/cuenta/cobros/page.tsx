"use client";

import { Suspense, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { CheckCircle2, CircleDollarSign, Link2, Wallet } from "lucide-react";
import { Alerta, Boton, EstadoVacio, Insignia, ModalConfirmacion, Precio, SkeletonLista, Tarjeta, useToast } from "@/components/ui";
import type { TonoInsignia } from "@/components/ui";
import { mensajeDeError } from "@/lib/api";
import { desconectarMp, getEstadoMp, getMisCobros, urlConectarMp, type EstadoCobro, type EstadoConexionMp, type MisCobros } from "@/lib/cobros";
import { fechaHoraLarga } from "@/lib/formatos";
import { TIEMPOS } from "@/lib/tiempos";

const ESTADOS: Record<EstadoCobro, { texto: string; tono: TonoInsignia }> = {
  retenido: { texto: "Por cobrar", tono: "info" },
  en_revision: { texto: "En revisión", tono: "aviso" },
  liberado: { texto: "Cobrado", tono: "exito" },
  reembolsado: { texto: "Devuelto al alumno", tono: "neutro" },
};

/** Mensaje al volver de MercadoPago (`?mp=`, lo pone el callback del backend). */
const VUELTA_MP: Record<string, { tono: "exito" | "peligro" | "info"; texto: string }> = {
  ok: { tono: "exito", texto: "¡Listo! Tu cuenta de MercadoPago quedó conectada." },
  cancelado: { tono: "info", texto: "Cancelaste la conexión. Podés intentarlo cuando quieras." },
  vencido: { tono: "peligro", texto: "La conexión venció. Probá de nuevo." },
  "otra-cuenta": { tono: "peligro", texto: "Esa cuenta de MercadoPago ya está conectada a otro tutor." },
  error: { tono: "peligro", texto: "No pudimos conectar tu cuenta. Probá de nuevo en unos minutos." },
};

/** "Mis cobros" del Tutor (R5): conexión de MercadoPago (ADR-M5-02) y lo cobrado por clase. */
export default function MisCobrosPage() {
  return (
    <Suspense>
      <MisCobrosContenido />
    </Suspense>
  );
}

function MisCobrosContenido() {
  const params = useSearchParams();
  const toast = useToast();
  const [mp, setMp] = useState<EstadoConexionMp | null>(null);
  const [cobros, setCobros] = useState<MisCobros | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [conectando, setConectando] = useState(false);
  const [confirmarDesconexion, setConfirmarDesconexion] = useState(false);

  const cargar = useCallback(async () => {
    setError(null);
    try {
      const [estado, lista] = await Promise.all([getEstadoMp(), getMisCobros()]);
      setMp(estado);
      setCobros(lista);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos cargar tus cobros."));
    }
  }, []);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  async function conectar() {
    setConectando(true);
    try {
      const { url } = await urlConectarMp();
      window.location.assign(url);
    } catch (err) {
      setConectando(false);
      toast.mostrar(mensajeDeError(err, "No pudimos iniciar la conexión."), { tono: "error" });
    }
  }

  async function desconectar() {
    try {
      await desconectarMp();
      toast.mostrar("Desconectamos tu MercadoPago.");
      await cargar();
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos desconectar tu cuenta."), { tono: "error" });
    } finally {
      setConfirmarDesconexion(false);
    }
  }

  const vuelta = VUELTA_MP[params.get("mp") ?? ""];
  const conectada = mp?.estado === "CONECTADA";

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mis cobros</h1>
      <p className="mt-1 text-[15px] text-tinta-suave">
        Lo que cobrás por cada clase, después de la comisión de Tinku.
      </p>

      {vuelta && (
        <Alerta tono={vuelta.tono} className="mt-6">
          {vuelta.texto}
        </Alerta>
      )}
      {error && (
        <Alerta tono="peligro" className="mt-6">
          {error}
        </Alerta>
      )}

      {mp?.requerida && (
        <Tarjeta className="mt-6 flex flex-col gap-4 sm:flex-row sm:items-center">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-marca-50 text-marca-700" aria-hidden>
            {conectada ? <CheckCircle2 className="size-6" /> : <Link2 className="size-6" />}
          </span>
          <div className="flex-1">
            <h2 className="text-lg font-bold">
              {conectada ? "MercadoPago conectado" : mp.estado === "ERROR" ? "Volvé a conectar tu MercadoPago" : "Conectá tu MercadoPago"}
            </h2>
            <p className="text-[15px] text-tinta-suave">
              {conectada
                ? "Los pagos de tus clases entran directo a tu cuenta. Tinku solo se queda con su comisión."
                : "Los alumnos pagan en tu cuenta de MercadoPago. Hasta que la conectes, no te pueden reservar clases."}
            </p>
          </div>
          {conectada ? (
            <Boton variante="secundario" onClick={() => setConfirmarDesconexion(true)}>
              Desconectar
            </Boton>
          ) : (
            <Boton onClick={() => void conectar()} disabled={conectando}>
              {conectando ? "Abriendo MercadoPago…" : "Conectar MercadoPago"}
            </Boton>
          )}
        </Tarjeta>
      )}

      {!cobros && !error ? (
        <div className="mt-6">
          <SkeletonLista />
        </div>
      ) : cobros ? (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            <Total titulo="Por cobrar" valor={cobros.retenido} />
            <Total titulo="En revisión" valor={cobros.enRevision} />
            <Total titulo="Cobrado" valor={cobros.liberado} />
            <Total titulo="Devuelto" valor={cobros.reembolsado} />
          </div>
          <p className="mt-3 text-sm text-tinta-tenue">
            Cada clase queda &quot;por cobrar&quot; hasta {TIEMPOS.liberacionHoras} hs después de darla. &quot;En revisión&quot; quiere
            decir que el equipo de Tinku está revisando algo de esa clase.
          </p>

          {cobros.cobros.length === 0 ? (
            <EstadoVacio className="mt-6" icono={<Wallet />} titulo="Todavía no tenés cobros">
              Cuando un alumno pague una clase, la vas a ver acá.
            </EstadoVacio>
          ) : (
            <ul className="mt-6 flex list-none flex-col gap-3 p-0">
              {cobros.cobros.map((c) => (
                <li key={c.reservaId}>
                  <Tarjeta className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <Link href={`/cuenta/reservas/${c.reservaId}`} className="font-bold">
                        Clase con {c.alumnoNombre} {c.alumnoApellido}
                      </Link>
                      <p className="text-sm text-tinta-suave first-letter:uppercase">{fechaHoraLarga(c.horario)}</p>
                      <p className="text-sm text-tinta-tenue">
                        Clase <Precio valor={c.precioSesion} tamano="sm" className="font-medium" /> · comisión{" "}
                        <Precio valor={c.comision} tamano="sm" className="font-medium" />
                        {c.simulado && " · pago simulado"}
                      </p>
                    </div>
                    <div className="flex items-center gap-3 sm:flex-col sm:items-end">
                      <Precio valor={c.estado === "reembolsado" ? 0 : c.neto} />
                      <Insignia tono={ESTADOS[c.estado].tono}>{ESTADOS[c.estado].texto}</Insignia>
                    </div>
                  </Tarjeta>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : null}

      <ModalConfirmacion
        abierto={confirmarDesconexion}
        titulo="¿Desconectar MercadoPago?"
        textoConfirmar="Desconectar"
        tono="peligro"
        onConfirmar={desconectar}
        onCerrar={() => setConfirmarDesconexion(false)}
      >
        Mientras esté desconectado no te van a poder reservar clases nuevas.
      </ModalConfirmacion>
    </div>
  );
}

function Total({ titulo, valor }: { titulo: string; valor: number }) {
  return (
    <Tarjeta className="flex flex-col gap-1 p-4">
      <span className="flex items-center gap-1.5 text-sm text-tinta-suave">
        <CircleDollarSign className="size-4" aria-hidden /> {titulo}
      </span>
      <Precio valor={valor} tamano="md" />
    </Tarjeta>
  );
}
