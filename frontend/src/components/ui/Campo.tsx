import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

const CONTROL =
  "w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60";

interface Envoltorio {
  id: string;
  etiqueta: ReactNode;
  /** Mensaje de error del campo: marca `aria-invalid` y se enlaza por `aria-describedby`. */
  error?: string | null;
  /** Texto de ayuda, también enlazado por `aria-describedby`. */
  ayuda?: ReactNode;
  className?: string;
  /**
   * Oculta la etiqueta visualmente pero la deja para lectores de pantalla
   * (buscadores con ícono, por ejemplo). Es preferible a un `aria-label`
   * suelto: sigue siendo un `<label>` real asociado al control, así que
   * también funciona el clic sobre la etiqueta.
   */
  etiquetaOculta?: boolean;
}

function idsDescripcion(id: string, error?: string | null, ayuda?: ReactNode) {
  const ids = [ayuda ? `${id}-ayuda` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length > 0 ? ids.join(" ") : undefined;
}

function Etiqueta({
  id,
  oculta,
  children,
}: {
  id: string;
  oculta?: boolean;
  children: ReactNode;
}) {
  return (
    <label
      htmlFor={id}
      className={oculta ? "sr-only" : "text-sm font-semibold text-slate-800"}
    >
      {children}
    </label>
  );
}

function Descripciones({ id, error, ayuda }: Pick<Envoltorio, "id" | "error" | "ayuda">) {
  return (
    <>
      {ayuda && (
        <span id={`${id}-ayuda`} className="text-xs text-slate-500">
          {ayuda}
        </span>
      )}
      {error && (
        <span id={`${id}-error`} className="text-xs text-red-700">
          {error}
        </span>
      )}
    </>
  );
}

export type CampoProps = Envoltorio & InputHTMLAttributes<HTMLInputElement>;

/** Campo de formulario: etiqueta + input + ayuda/error enlazados para lectores de pantalla. */
export default function Campo({
  id,
  etiqueta,
  error,
  ayuda,
  className,
  etiquetaOculta,
  ...props
}: CampoProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Etiqueta id={id} oculta={etiquetaOculta}>
        {etiqueta}
      </Etiqueta>
      <input
        id={id}
        className={cn(CONTROL, error && "border-red-300", className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={idsDescripcion(id, error, ayuda)}
        {...props}
      />
      <Descripciones id={id} error={error} ayuda={ayuda} />
    </div>
  );
}

export type CampoSelectProps = Envoltorio & SelectHTMLAttributes<HTMLSelectElement>;

export function CampoSelect({
  id,
  etiqueta,
  error,
  ayuda,
  className,
  etiquetaOculta,
  children,
  ...props
}: CampoSelectProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <Etiqueta id={id} oculta={etiquetaOculta}>
        {etiqueta}
      </Etiqueta>
      <select
        id={id}
        className={cn(CONTROL, error && "border-red-300", className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={idsDescripcion(id, error, ayuda)}
        {...props}
      >
        {children}
      </select>
      <Descripciones id={id} error={error} ayuda={ayuda} />
    </div>
  );
}

export type CampoCheckboxProps = Omit<Envoltorio, "etiquetaOculta"> &
  InputHTMLAttributes<HTMLInputElement>;

/**
 * Checkbox con la etiqueta AL LADO, no arriba: un `<Campo>` normal pone el
 * label encima del control, y en una casilla eso rompe la asociación visual
 * (la persona no sabe qué está tildando). El área de clic cubre texto +
 * casilla, que en mobile es la diferencia entre poder tildarlo y no.
 */
export function CampoCheckbox({
  id,
  etiqueta,
  error,
  ayuda,
  className,
  ...props
}: CampoCheckboxProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
        <input
          id={id}
          type="checkbox"
          className={cn("mt-0.5 size-4 accent-teal-700", className)}
          aria-invalid={error ? true : undefined}
          aria-describedby={idsDescripcion(id, error, ayuda)}
          {...props}
        />
        <span>{etiqueta}</span>
      </label>
      <Descripciones id={id} error={error} ayuda={ayuda} />
    </div>
  );
}

export { CONTROL as clasesControl };
