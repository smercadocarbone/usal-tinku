"use client";

import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface InterruptorProps {
  id: string;
  etiqueta: ReactNode;
  descripcion?: ReactNode;
  activo: boolean;
  onCambio: (activo: boolean) => void;
  disabled?: boolean;
}

/** Switch (`role="switch"`): para ajustes que se aplican al toque. */
export default function Interruptor({ id, etiqueta, descripcion, activo, onCambio, disabled }: InterruptorProps) {
  return (
    <div className="flex items-start justify-between gap-4">
      <div className="min-w-0">
        <label id={`${id}-label`} htmlFor={id} className="cursor-pointer text-[15px] font-semibold text-tinta">
          {etiqueta}
        </label>
        {descripcion && (
          <p id={`${id}-desc`} className="mt-0.5 text-sm leading-relaxed text-tinta-suave">
            {descripcion}
          </p>
        )}
      </div>
      <button
        id={id}
        type="button"
        role="switch"
        aria-checked={activo}
        aria-labelledby={`${id}-label`}
        aria-describedby={descripcion ? `${id}-desc` : undefined}
        disabled={disabled}
        onClick={() => onCambio(!activo)}
        className={cn(
          "relative mt-0.5 inline-flex h-8 w-[52px] shrink-0 cursor-pointer items-center rounded-full border-2 transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50",
          activo ? "border-marca-700 bg-marca-700" : "border-borde-control bg-superficie-hundida"
        )}
      >
        <span
          aria-hidden
          className={cn(
            "inline-block size-6 rounded-full bg-white shadow-elevado transition-transform duration-200 ease-out",
            activo ? "translate-x-[22px]" : "translate-x-0.5"
          )}
        />
      </button>
    </div>
  );
}
Interruptor.displayName = "Interruptor";
