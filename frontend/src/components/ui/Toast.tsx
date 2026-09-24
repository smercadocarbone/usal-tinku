"use client";

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import { cn } from "@/lib/cn";

interface ToastItem {
  id: number;
  texto: ReactNode;
  tono: "exito" | "error";
  accion?: { texto: string; onClick: () => void };
}

interface ContextoToast {
  mostrar: (texto: ReactNode, opciones?: { tono?: "exito" | "error"; accion?: ToastItem["accion"] }) => void;
}

const Contexto = createContext<ContextoToast | null>(null);

/**
 * Confirmaciones no bloqueantes ("Guardado"). La región `aria-live` existe SIEMPRE
 * en el DOM (vacía): si se crea junto con el mensaje, varios lectores de pantalla
 * no lo anuncian.
 */
export function ProveedorToast({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const seq = useRef(0);

  const mostrar = useCallback<ContextoToast["mostrar"]>((texto, opciones) => {
    const id = ++seq.current;
    setItems((prev) => [...prev.slice(-2), { id, texto, tono: opciones?.tono ?? "exito", accion: opciones?.accion }]);
    window.setTimeout(() => setItems((prev) => prev.filter((t) => t.id !== id)), opciones?.accion ? 6000 : 3500);
  }, []);

  const valor = useMemo(() => ({ mostrar }), [mostrar]);

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div
        aria-live="polite"
        role="status"
        className="pointer-events-none fixed inset-x-0 bottom-[calc(5.5rem+env(safe-area-inset-bottom))] z-[60] flex flex-col items-center gap-2 px-4 lg:bottom-6"
      >
        {items.map((t) => (
          <div
            key={t.id}
            className={cn(
              "pointer-events-auto flex min-h-12 w-full max-w-sm items-center gap-3 rounded-control bg-tinta px-4 py-3 text-sm font-medium text-white shadow-flotante motion-safe:animate-subir"
            )}
          >
            {t.tono === "exito" ? (
              <CheckCircle2 className="size-5 shrink-0 text-marca-300" aria-hidden />
            ) : (
              <AlertCircle className="size-5 shrink-0 text-acento-300" aria-hidden />
            )}
            <span className="flex-1">{t.texto}</span>
            {t.accion && (
              <button
                type="button"
                onClick={() => {
                  t.accion?.onClick();
                  setItems((prev) => prev.filter((x) => x.id !== t.id));
                }}
                className="cursor-pointer rounded-md px-2 py-1 font-bold text-marca-200 hover:bg-white/10"
              >
                {t.accion.texto}
              </button>
            )}
          </div>
        ))}
      </div>
    </Contexto.Provider>
  );
}

/** Sin proveedor (tests aislados, páginas de error) no rompe: no hace nada. */
export function useToast(): ContextoToast {
  return useContext(Contexto) ?? { mostrar: () => undefined };
}
