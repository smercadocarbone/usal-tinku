import { Check } from "lucide-react";
import { cn } from "@/lib/cn";

export interface PasosProps {
  pasos: string[];
  /** Índice (0-based) del paso actual. */
  actual: number;
  className?: string;
}

/** Stepper. En mobile: "Paso 2 de 4 · Tus datos" + barra; en desktop, los pasos con nombre. */
export default function Pasos({ pasos, actual, className }: PasosProps) {
  const total = pasos.length;
  return (
    <nav aria-label="Progreso" className={className}>
      <div className="sm:hidden">
        <p className="text-[13px] font-semibold text-tinta-tenue">
          Paso {actual + 1} de {total} · <span className="text-tinta">{pasos[actual]}</span>
        </p>
        <div className="mt-2 flex gap-1.5" aria-hidden>
          {pasos.map((p, i) => (
            <span
              key={p}
              className={cn(
                "h-1.5 flex-1 rounded-full transition-colors duration-300",
                i <= actual ? "bg-marca-700" : "bg-superficie-hundida"
              )}
            />
          ))}
        </div>
      </div>
      <ol className="hidden list-none items-center gap-2 p-0 sm:flex">
        {pasos.map((p, i) => {
          const completo = i < actual;
          const esActual = i === actual;
          return (
            <li key={p} className="flex flex-1 items-center gap-2 last:flex-none" aria-current={esActual ? "step" : undefined}>
              <span
                className={cn(
                  "inline-flex size-8 shrink-0 items-center justify-center rounded-full text-sm font-bold transition-colors",
                  completo && "bg-marca-700 text-white",
                  esActual && "bg-tinta text-white ring-4 ring-marca-100",
                  !completo && !esActual && "bg-superficie-hundida text-tinta-tenue"
                )}
              >
                {completo ? <Check className="size-4" aria-hidden /> : i + 1}
                {completo && <span className="sr-only">(completo)</span>}
              </span>
              <span className={cn("whitespace-nowrap text-sm", esActual ? "font-bold text-tinta" : "font-medium text-tinta-tenue")}>
                {p}
              </span>
              {i < total - 1 && (
                <span aria-hidden className={cn("mx-1 h-0.5 min-w-4 flex-1 rounded-full", completo ? "bg-marca-700" : "bg-borde")} />
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
Pasos.displayName = "Pasos";
