"use client";

import { useId } from "react";
import { Star } from "lucide-react";
import { cn } from "@/lib/cn";

export interface EstrellasProps {
  /** Promedio 0–5 (lectura). */
  valor: number;
  cantidad?: number;
  tamano?: "sm" | "md";
  className?: string;
}

/** Calificación de lectura: "4.8 ★ (12)". */
export default function Estrellas({ valor, cantidad, tamano = "sm", className }: EstrellasProps) {
  return (
    <span className={cn("inline-flex items-center gap-1 font-semibold text-tinta", tamano === "sm" ? "text-sm" : "text-base", className)}>
      <Star className={cn("fill-acento-500 text-acento-500", tamano === "sm" ? "size-4" : "size-5")} aria-hidden />
      <span className="tabular">{valor.toFixed(1).replace(".", ",")}</span>
      <span className="sr-only"> de 5 estrellas</span>
      {cantidad !== undefined && (
        <span className="font-normal text-tinta-tenue">
          ({cantidad}
          <span className="sr-only"> {cantidad === 1 ? "calificación" : "calificaciones"}</span>)
        </span>
      )}
    </span>
  );
}

export interface EntradaEstrellasProps {
  valor: number;
  onCambio: (v: number) => void;
  etiqueta?: string;
  disabled?: boolean;
}

const TEXTOS = ["", "Muy mala", "Mala", "Buena", "Muy buena", "Excelente"];

/** Calificación de entrada: radio group accesible (flechas cambian el valor). */
export function EntradaEstrellas({ valor, onCambio, etiqueta = "Calificación", disabled }: EntradaEstrellasProps) {
  const nombre = useId();
  const mostrado = valor;
  return (
    <fieldset disabled={disabled} className="flex flex-col gap-2">
      <legend className="text-sm font-semibold text-tinta">{etiqueta}</legend>
      <div className="flex items-center gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="cursor-pointer rounded-full p-1 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-marca-600">
            <input
              type="radio"
              name={nombre}
              value={n}
              checked={valor === n}
              onChange={() => onCambio(n)}
              className="sr-only"
            />
            <Star
              aria-hidden
              className={cn(
                "size-9 transition-transform duration-150 motion-safe:hover:scale-110",
                n <= mostrado ? "fill-acento-500 text-acento-500" : "fill-transparent text-borde-control"
              )}
            />
            <span className="sr-only">
              {n} {n === 1 ? "estrella" : "estrellas"} — {TEXTOS[n]}
            </span>
          </label>
        ))}
        <span className="ml-2 text-sm font-semibold text-tinta-suave" aria-hidden>
          {TEXTOS[mostrado]}
        </span>
      </div>
    </fieldset>
  );
}
