"use client";

import {
  useState,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from "react";
import { ChevronDown, Eye, EyeOff } from "lucide-react";
import { cn } from "@/lib/cn";

/** El borde de un control usa `borde-control` (3.4:1): WCAG 1.4.11 pide 3:1. */
const CONTROL =
  "w-full min-h-12 rounded-control border border-borde-control bg-superficie px-3.5 py-2.5 text-base text-tinta " +
  "placeholder:text-tinta-tenue transition-[border-color,box-shadow] duration-150 " +
  "hover:border-tinta-suave focus:border-marca-700 focus:outline-none focus:ring-4 focus:ring-marca-100 " +
  "disabled:cursor-not-allowed disabled:bg-superficie-hundida disabled:opacity-70 " +
  "aria-[invalid=true]:border-peligro aria-[invalid=true]:focus:ring-peligro-suave";

interface Envoltorio {
  id: string;
  etiqueta: ReactNode;
  /** Error del campo: marca `aria-invalid` y se enlaza por `aria-describedby`. */
  error?: string | null;
  ayuda?: ReactNode;
  className?: string;
  /** Oculta la etiqueta visualmente pero la deja como `<label>` real (buscadores con ícono). */
  etiquetaOculta?: boolean;
}

function idsDescripcion(id: string, error?: string | null, ayuda?: ReactNode) {
  const ids = [ayuda ? `${id}-ayuda` : null, error ? `${id}-error` : null].filter(Boolean);
  return ids.length > 0 ? ids.join(" ") : undefined;
}

function Etiqueta({ id, oculta, children }: { id: string; oculta?: boolean; children: ReactNode }) {
  return (
    <label htmlFor={id} className={oculta ? "sr-only" : "text-sm font-semibold text-tinta"}>
      {children}
    </label>
  );
}

function Descripciones({ id, error, ayuda }: Pick<Envoltorio, "id" | "error" | "ayuda">) {
  return (
    <>
      {ayuda && (
        <span id={`${id}-ayuda`} className="text-[13px] text-tinta-tenue">
          {ayuda}
        </span>
      )}
      {error && (
        <span id={`${id}-error`} className="text-[13px] font-medium text-peligro">
          {error}
        </span>
      )}
    </>
  );
}

export interface CampoProps extends Envoltorio, Omit<InputHTMLAttributes<HTMLInputElement>, "id"> {
  /**
   * `password`: botón "Mostrar" dentro del campo.
   * `dni`: teclado numérico y máscara `00.000.000`; `onValor` recibe solo los dígitos.
   */
  variante?: "texto" | "password" | "dni";
  /** Ícono decorativo a la izquierda. */
  icono?: ReactNode;
  /** Para `dni`: recibe los dígitos sin puntos. */
  onValor?: (valor: string) => void;
}

/** Formatea dígitos como DNI argentino: 12345678 → 12.345.678. */
export function formatearDni(digitos: string): string {
  const d = digitos.replace(/\D/g, "").slice(0, 8);
  return d.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

/** Etiqueta + input + ayuda/error enlazados. Label siempre visible (nunca solo placeholder). */
export default function Campo({
  id,
  etiqueta,
  error,
  ayuda,
  className,
  etiquetaOculta,
  variante = "texto",
  icono,
  onValor,
  type,
  value,
  onChange,
  ...props
}: CampoProps) {
  const [visible, setVisible] = useState(false);
  const esPassword = variante === "password";
  const esDni = variante === "dni";

  const input = (
    <input
      id={id}
      type={esPassword ? (visible ? "text" : "password") : esDni ? "text" : type}
      inputMode={esDni ? "numeric" : props.inputMode}
      autoComplete={esDni ? "off" : props.autoComplete}
      value={esDni && typeof value === "string" ? formatearDni(value) : value}
      onChange={(e) => {
        if (esDni) {
          const digitos = e.target.value.replace(/\D/g, "").slice(0, 8);
          onValor?.(digitos);
        }
        onChange?.(e);
      }}
      className={cn(CONTROL, icono && "pl-11", esPassword && "pr-24", esDni && "tabular tracking-wide", className)}
      aria-invalid={error ? true : undefined}
      aria-describedby={idsDescripcion(id, error, ayuda)}
      {...props}
    />
  );

  return (
    <div className="flex flex-col gap-1.5">
      <Etiqueta id={id} oculta={etiquetaOculta}>
        {etiqueta}
      </Etiqueta>
      {icono || esPassword ? (
        <div className="relative">
          {icono && (
            <span
              aria-hidden
              className="pointer-events-none absolute inset-y-0 left-3.5 flex items-center text-tinta-tenue [&>svg]:size-5"
            >
              {icono}
            </span>
          )}
          {input}
          {esPassword && (
            <button
              type="button"
              onClick={() => setVisible((v) => !v)}
              aria-pressed={visible}
              aria-controls={id}
              className="absolute inset-y-1 right-1 inline-flex min-w-11 cursor-pointer items-center gap-1.5 rounded-[10px] px-3 text-sm font-semibold text-tinta-suave hover:bg-superficie-hundida hover:text-tinta"
            >
              {visible ? <EyeOff className="size-4" aria-hidden /> : <Eye className="size-4" aria-hidden />}
              {visible ? "Ocultar" : "Mostrar"}
            </button>
          )}
        </div>
      ) : (
        input
      )}
      <Descripciones id={id} error={error} ayuda={ayuda} />
    </div>
  );
}
Campo.displayName = "Campo";

export type CampoSelectProps = Envoltorio & SelectHTMLAttributes<HTMLSelectElement>;

/** `Selector`: `<select>` nativo (el más accesible que existe) con el estilo del sistema. */
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
      <div className="relative">
        <select
          id={id}
          className={cn(CONTROL, "cursor-pointer appearance-none pr-10", className)}
          aria-invalid={error ? true : undefined}
          aria-describedby={idsDescripcion(id, error, ayuda)}
          {...props}
        >
          {children}
        </select>
        <ChevronDown
          aria-hidden
          className="pointer-events-none absolute right-3.5 top-1/2 size-5 -translate-y-1/2 text-tinta-tenue"
        />
      </div>
      <Descripciones id={id} error={error} ayuda={ayuda} />
    </div>
  );
}
CampoSelect.displayName = "Selector";
export { CampoSelect as Selector };

export interface AreaTextoProps extends Envoltorio, Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "id"> {
  /** Muestra "120/300" debajo, anunciado sin interrumpir. */
  contador?: boolean;
}

export function AreaTexto({
  id,
  etiqueta,
  error,
  ayuda,
  className,
  etiquetaOculta,
  contador,
  maxLength,
  value,
  ...props
}: AreaTextoProps) {
  const largo = typeof value === "string" ? value.length : 0;
  return (
    <div className="flex flex-col gap-1.5">
      <Etiqueta id={id} oculta={etiquetaOculta}>
        {etiqueta}
      </Etiqueta>
      <textarea
        id={id}
        value={value}
        maxLength={maxLength}
        className={cn(CONTROL, "min-h-28 resize-y leading-relaxed", className)}
        aria-invalid={error ? true : undefined}
        aria-describedby={idsDescripcion(id, error, ayuda)}
        {...props}
      />
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <Descripciones id={id} error={error} ayuda={ayuda} />
        </div>
        {contador && maxLength && (
          <span className="tabular shrink-0 text-[13px] text-tinta-tenue" aria-live="polite">
            {largo}/{maxLength}
          </span>
        )}
      </div>
    </div>
  );
}
AreaTexto.displayName = "AreaTexto";

export type CampoCheckboxProps = Omit<Envoltorio, "etiquetaOculta"> &
  Omit<InputHTMLAttributes<HTMLInputElement>, "id">;

/**
 * Checkbox con la etiqueta AL LADO: el área de clic cubre texto + casilla, que en
 * mobile es la diferencia entre poder tildarlo y no.
 */
export function CampoCheckbox({ id, etiqueta, error, ayuda, className, ...props }: CampoCheckboxProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="flex cursor-pointer items-start gap-3 text-[15px] text-tinta-suave">
        <input
          id={id}
          type="checkbox"
          className={cn("mt-0.5 size-5 shrink-0 cursor-pointer accent-marca-700", className)}
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
CampoCheckbox.displayName = "CampoCheckbox";

export { CONTROL as clasesControl };
