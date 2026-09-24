import type { ReactNode } from "react";
import { ChevronDown } from "lucide-react";
import { cn } from "@/lib/cn";

export interface ItemAcordeon {
  pregunta: string;
  respuesta: ReactNode;
}

/** Acordeón sobre `<details>`: accesible y operable con teclado sin JS. */
export default function Acordeon({ items, className }: { items: ItemAcordeon[]; className?: string }) {
  return (
    <div className={cn("divide-y divide-borde overflow-hidden rounded-tarjeta border border-borde bg-superficie", className)}>
      {items.map((it) => (
        <details key={it.pregunta} className="group">
          <summary className="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 px-5 py-4 text-[16px] font-semibold text-tinta hover:bg-fondo [&::-webkit-details-marker]:hidden">
            {it.pregunta}
            <ChevronDown aria-hidden className="size-5 shrink-0 text-tinta-tenue transition-transform duration-200 group-open:rotate-180" />
          </summary>
          <div className="px-5 pb-5 text-[15px] leading-relaxed text-tinta-suave">{it.respuesta}</div>
        </details>
      ))}
    </div>
  );
}
