import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export type TonoInsignia = "neutro" | "exito" | "aviso" | "peligro" | "marca" | "info" | "acento";

const TONOS: Record<TonoInsignia, string> = {
  neutro: "bg-superficie-hundida text-tinta-suave",
  exito: "bg-exito-suave text-exito ring-1 ring-inset ring-exito/15",
  aviso: "bg-aviso-suave text-aviso ring-1 ring-inset ring-aviso/20",
  peligro: "bg-peligro-suave text-peligro ring-1 ring-inset ring-peligro/15",
  info: "bg-info-suave text-info ring-1 ring-inset ring-info/15",
  marca: "bg-marca-700 text-white",
  // acento-500 SOLO como relleno con texto tinta (7.6:1).
  acento: "bg-acento-100 text-tinta ring-1 ring-inset ring-acento-300",
};

export interface InsigniaProps {
  children: ReactNode;
  tono?: TonoInsignia;
  /** Ícono decorativo: la insignia nunca comunica solo con color. */
  icono?: ReactNode;
  tamano?: "sm" | "md";
  className?: string;
  title?: string;
}

export default function Insignia({ children, tono = "neutro", icono, tamano = "md", className, title }: InsigniaProps) {
  return (
    <span
      title={title}
      className={cn(
        "inline-flex w-fit shrink-0 items-center gap-1.5 whitespace-nowrap rounded-pastilla font-semibold",
        tamano === "sm" ? "px-2 py-0.5 text-[11px]" : "px-2.5 py-1 text-xs",
        TONOS[tono],
        className
      )}
    >
      {icono && <span aria-hidden className="inline-flex [&>svg]:size-3.5">{icono}</span>}
      {children}
    </span>
  );
}
Insignia.displayName = "Insignia";
