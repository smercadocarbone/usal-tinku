import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export type TonoAlerta = "error" | "aviso" | "exito" | "info" | "dato";

const TONOS: Record<TonoAlerta, string> = {
  error: "border-red-200 bg-red-50 text-red-700",
  aviso: "border-amber-200 bg-amber-50 text-amber-800",
  exito: "border-teal-200 bg-teal-50 text-teal-700",
  info: "border-slate-200 bg-slate-50 text-slate-600",
  // Para un dato que conviene destacar sin que sea aviso ni error — ej. una
  // sugerencia de precio calculada. Azul se reserva para esto: si "aviso" o
  // "info" también fueran azules, se pierde la distinción semántica.
  dato: "border-blue-100 bg-blue-50 text-blue-800",
};

export interface AlertaProps {
  tono?: TonoAlerta;
  children: ReactNode;
  className?: string;
  /**
   * `alert` interrumpe al lector de pantalla; `status` no. Por defecto solo el
   * tono `error` interrumpe — un aviso o una confirmación no deberían cortar
   * lo que la persona está leyendo.
   */
  rol?: "alert" | "status";
}

export default function Alerta({ tono = "info", children, className, rol }: AlertaProps) {
  return (
    <div
      role={rol ?? (tono === "error" ? "alert" : "status")}
      className={cn("rounded-lg border px-3.5 py-3 text-sm", TONOS[tono], className)}
    >
      {children}
    </div>
  );
}
