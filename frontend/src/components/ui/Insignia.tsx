import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export type TonoInsignia = "neutro" | "exito" | "aviso" | "peligro" | "marca";

const TONOS: Record<TonoInsignia, string> = {
  neutro: "bg-slate-100 text-slate-600",
  exito: "bg-teal-50 text-teal-700",
  aviso: "bg-amber-100 text-amber-800",
  peligro: "bg-red-50 text-red-700",
  marca: "bg-teal-700 text-white",
};

export interface InsigniaProps {
  children: ReactNode;
  tono?: TonoInsignia;
  className?: string;
  title?: string;
}

export default function Insignia({
  children,
  tono = "neutro",
  className,
  title,
}: InsigniaProps) {
  return (
    <span
      title={title}
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold",
        TONOS[tono],
        className
      )}
    >
      {children}
    </span>
  );
}
