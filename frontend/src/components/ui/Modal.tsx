"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";
import { X } from "lucide-react";
import { cn } from "@/lib/cn";

export interface ModalProps {
  abierto: boolean;
  onCerrar: () => void;
  titulo: ReactNode;
  descripcion?: ReactNode;
  children?: ReactNode;
  /** Botones al pie (fijos abajo en mobile). */
  pie?: ReactNode;
  /** `hoja`: en mobile sale desde abajo (bottom sheet); en desktop es un modal centrado. */
  variante?: "centrado" | "hoja";
  ancho?: "sm" | "md" | "lg";
  className?: string;
}

/**
 * Modal sobre `<dialog>` nativo con `showModal()`: el navegador ya da foco atrapado
 * (el resto de la página queda inerte), `Escape` para cerrar y la capa superior. No
 * reimplementamos nada de eso a mano.
 */
export default function Modal({
  abierto,
  onCerrar,
  titulo,
  descripcion,
  children,
  pie,
  variante = "centrado",
  ancho = "md",
  className,
}: ModalProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const idTitulo = useId();
  const idDesc = useId();

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (abierto && !d.open) {
      // jsdom/navegadores viejos: sin showModal, el diálogo igual se ve.
      if (typeof d.showModal === "function") d.showModal();
      else d.setAttribute("open", "");
    } else if (!abierto && d.open) {
      d.close();
    }
  }, [abierto]);

  const hoja = variante === "hoja";

  return (
    <dialog
      ref={ref}
      aria-labelledby={idTitulo}
      aria-describedby={descripcion ? idDesc : undefined}
      onCancel={(e) => {
        e.preventDefault();
        onCerrar();
      }}
      className={cn(
        "m-0 max-h-none max-w-none bg-transparent p-0 text-tinta backdrop:bg-tinta/50 backdrop:backdrop-blur-[2px] open:flex",
        "fixed inset-0 h-full w-full items-end justify-center sm:items-center sm:p-6",
        !hoja && "items-center p-4"
      )}
    >
      {/* Clic en el fondo cierra. Es un atajo de mouse: el teclado ya tiene Escape y el botón Cerrar. */}
      {abierto && <div role="presentation" className="absolute inset-0" onClick={onCerrar} />}
      {abierto && (
        <div
          className={cn(
            "relative flex max-h-[92dvh] w-full flex-col overflow-hidden bg-superficie shadow-flotante",
            hoja
              ? "rounded-t-[28px] motion-safe:animate-hoja sm:rounded-tarjeta sm:motion-safe:animate-subir"
              : "rounded-tarjeta motion-safe:animate-subir",
            ancho === "sm" && "sm:max-w-md",
            ancho === "md" && "sm:max-w-lg",
            ancho === "lg" && "sm:max-w-2xl",
            className
          )}
        >
          {hoja && <div aria-hidden className="mx-auto mt-2.5 h-1.5 w-10 rounded-full bg-borde-fuerte sm:hidden" />}
          <div className="flex items-start justify-between gap-4 px-6 pb-2 pt-5">
            <div>
              <h2 id={idTitulo} className="text-xl font-bold">
                {titulo}
              </h2>
              {descripcion && (
                <p id={idDesc} className="mt-1.5 text-[15px] leading-relaxed text-tinta-suave">
                  {descripcion}
                </p>
              )}
            </div>
            <button
              type="button"
              onClick={onCerrar}
              aria-label="Cerrar"
              className="-mr-2 inline-flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-full text-tinta-suave hover:bg-superficie-hundida hover:text-tinta"
            >
              <X className="size-5" aria-hidden />
            </button>
          </div>
          {children && <div className="overflow-y-auto px-6 py-3">{children}</div>}
          {pie && (
            <div className="safe-bottom flex flex-col-reverse gap-2 border-t border-borde px-6 py-4 sm:flex-row sm:justify-end">
              {pie}
            </div>
          )}
        </div>
      )}
    </dialog>
  );
}
Modal.displayName = "Modal";
