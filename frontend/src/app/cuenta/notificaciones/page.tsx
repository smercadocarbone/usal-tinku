"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { BellOff, ShieldAlert, TriangleAlert } from "lucide-react";
import { getNotificaciones, marcarLeida, textoDe, type Notificacion } from "@/lib/notificaciones";
import { Alerta, Boton, EstadoVacio, SkeletonLista, Tarjeta, clasesBoton } from "@/components/ui";
import { cn } from "@/lib/cn";

function formatear(iso: string): string {
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    timeZone: "America/Argentina/Buenos_Aires",
  });
}

/** Bandeja in-app (FASE2-03). Al abrirla, los avisos nuevos quedan leídos. */
export default function NotificacionesPage() {
  const [avisos, setAvisos] = useState<Notificacion[] | null>(null);
  const [error, setError] = useState(false);
  // Marcar leída es idempotente en el backend; esto solo evita POST repetidos.
  const marcadas = useRef(new Set<string>());

  const cargar = useCallback(() => {
    setError(false);
    getNotificaciones()
      .then((lista) => {
        setAvisos(lista);
        const nuevas = lista.filter((n) => !n.leida && !marcadas.current.has(n.id));
        nuevas.forEach((n) => marcadas.current.add(n.id));
        // La campana vuelve a contar cuando el backend ya las tiene como leídas.
        void Promise.allSettled(nuevas.map((n) => marcarLeida(n.id))).then(() =>
          window.dispatchEvent(new Event("tinku:notificaciones-leidas"))
        );
      })
      .catch(() => setError(true));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  return (
    <section className="mx-auto max-w-[720px]">
      <h1 className="text-[28px] font-extrabold sm:text-[36px]">Avisos</h1>
      <p className="mt-2 text-[16px] text-tinta-suave">Lo importante de tu cuenta, en un solo lugar.</p>
      <div className="mt-8">
        {error ? (
          <Alerta tono="peligro" accion={<Boton variante="secundario" tamano="sm" onClick={cargar}>Probar de nuevo</Boton>}>
            No pudimos cargar tus avisos.
          </Alerta>
        ) : avisos === null ? (
          <SkeletonLista />
        ) : avisos.length === 0 ? (
          <EstadoVacio icono={<BellOff />} titulo="No tenés avisos">
            Cuando pase algo importante en tu cuenta, te lo contamos acá y por email.
          </EstadoVacio>
        ) : (
          <ul className="flex list-none flex-col gap-3 p-0">
            {avisos.map((n) => {
              const t = textoDe(n, formatear);
              const Icono = t.tono === "peligro" ? ShieldAlert : TriangleAlert;
              return (
                <li key={n.id}>
                  <Tarjeta className={cn("flex gap-4 p-4 sm:p-5", !n.leida && "ring-2 ring-marca-100")}>
                    <span
                      aria-hidden
                      className={cn(
                        "flex size-10 shrink-0 items-center justify-center rounded-full",
                        t.tono === "peligro" ? "bg-peligro-suave text-peligro" : "bg-aviso-suave text-aviso"
                      )}
                    >
                      <Icono className="size-5" />
                    </span>
                    <div className="min-w-0 flex-1">
                      <p className="flex flex-wrap items-baseline justify-between gap-x-3 font-bold">
                        <span>
                          {!n.leida && <span className="sr-only">Nuevo: </span>}
                          {t.titulo}
                        </span>
                        <span className="text-[13px] font-normal text-tinta-tenue">{formatear(n.creadaAt)}</span>
                      </p>
                      {t.detalle && <p className="mt-1 text-[15px] text-tinta-suave">{t.detalle}</p>}
                      {t.href && t.accion && (
                        <Link href={t.href} className={clasesBoton("secundario", "sm", "mt-3")}>
                          {t.accion}
                        </Link>
                      )}
                    </div>
                  </Tarjeta>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </section>
  );
}
