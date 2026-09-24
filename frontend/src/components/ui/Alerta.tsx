"use client";

import { useState, type ReactNode } from "react";
import { AlertTriangle, CheckCircle2, Info, OctagonAlert, Sparkles, X } from "lucide-react";
import { cn } from "@/lib/cn";

/** `error` y `dato` son alias históricos de `peligro` e `info`. */
export type TonoAlerta = "info" | "exito" | "aviso" | "peligro" | "error" | "dato";

const TONOS: Record<"info" | "exito" | "aviso" | "peligro", { caja: string; icono: typeof Info }> = {
  info: { caja: "border-info/20 bg-info-suave text-[#1e3a8a]", icono: Info },
  exito: { caja: "border-exito/20 bg-exito-suave text-[#14532d]", icono: CheckCircle2 },
  aviso: { caja: "border-aviso/25 bg-aviso-suave text-[#7c2d12]", icono: AlertTriangle },
  peligro: { caja: "border-peligro/20 bg-peligro-suave text-[#7f1d1d]", icono: OctagonAlert },
};

function normalizar(t: TonoAlerta): keyof typeof TONOS {
  if (t === "error") return "peligro";
  if (t === "dato") return "info";
  return t;
}

export interface AlertaProps {
  tono?: TonoAlerta;
  titulo?: ReactNode;
  children?: ReactNode;
  /** Acción opcional (botón o enlace) debajo del texto. */
  accion?: ReactNode;
  /** Muestra la X de cierre. */
  cerrable?: boolean;
  onCerrar?: () => void;
  /** Sin ícono (para usos en línea muy compactos). */
  sinIcono?: boolean;
  className?: string;
  /**
   * `alert` interrumpe al lector de pantalla; `status` no. Por defecto solo
   * `peligro` interrumpe.
   */
  rol?: "alert" | "status";
}

export default function Alerta({
  tono = "info",
  titulo,
  children,
  accion,
  cerrable,
  onCerrar,
  sinIcono,
  className,
  rol,
}: AlertaProps) {
  const [cerrada, setCerrada] = useState(false);
  const t = normalizar(tono);
  const Icono = tono === "dato" ? Sparkles : TONOS[t].icono;
  if (cerrada) return null;

  return (
    <div
      role={rol ?? (t === "peligro" ? "alert" : "status")}
      className={cn(
        "flex gap-3 rounded-control border px-4 py-3.5 text-sm leading-relaxed motion-safe:animate-aparecer",
        TONOS[t].caja,
        className
      )}
    >
      {!sinIcono && <Icono className="mt-0.5 size-[18px] shrink-0" aria-hidden />}
      <div className="min-w-0 flex-1">
        {titulo && <p className="font-semibold">{titulo}</p>}
        {children && <div className={cn(titulo && "mt-0.5 opacity-90")}>{children}</div>}
        {accion && <div className="mt-3 flex flex-wrap gap-2">{accion}</div>}
      </div>
      {cerrable && (
        <button
          type="button"
          onClick={() => {
            setCerrada(true);
            onCerrar?.();
          }}
          className="-m-1.5 inline-flex size-9 shrink-0 cursor-pointer items-center justify-center rounded-full opacity-70 hover:bg-black/5 hover:opacity-100"
          aria-label="Cerrar aviso"
        >
          <X className="size-4" aria-hidden />
        </button>
      )}
    </div>
  );
}
Alerta.displayName = "Alerta";
