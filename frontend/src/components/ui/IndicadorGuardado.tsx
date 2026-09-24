import type { ReactNode } from "react";
import { AlertCircle, CloudCheck, Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";
import Alerta from "./Alerta";

export type EstadoGuardado = "guardando" | "ok" | "error";

export interface IndicadorGuardadoProps {
  estado: EstadoGuardado;
  /** Solo se usa cuando `estado === "error"`. */
  mensajeError?: ReactNode;
  textoGuardando?: ReactNode;
  textoOk?: ReactNode;
  className?: string;
}

/**
 * Indicador de autoguardado. "Guardando…" y "Guardado automático" son estados
 * transitorios de bajo impacto — van en línea, sin caja, para no competir
 * visualmente con el campo que la persona sigue editando. Un error SÍ
 * interrumpe: usa `Alerta` (misma superficie que cualquier otro error de la
 * app), con `role="alert"` siempre — antes uno de los dos formularios que
 * tenía esto solo anunciaba algunos errores como `alert` y otros como
 * `status` según el texto exacto del mensaje; acá cualquier error interrumpe.
 */
export default function IndicadorGuardado({
  estado,
  mensajeError,
  textoGuardando = "Guardando…",
  textoOk = "Guardado automático",
  className,
}: IndicadorGuardadoProps) {
  if (estado === "error") {
    return (
      <Alerta tono="error" className={cn("flex w-fit items-center gap-2", className)}>
        <AlertCircle size={16} aria-hidden /> {mensajeError}
      </Alerta>
    );
  }

  return (
    <span role="status" className={cn("flex items-center gap-1.5 text-sm text-tinta-tenue", className)}>
      {estado === "guardando" ? (
        <>
          <Loader2 className="animate-spin" size={16} aria-hidden /> {textoGuardando}
        </>
      ) : (
        <>
          <CloudCheck className="text-marca-700" size={16} aria-hidden /> {textoOk}
        </>
      )}
    </span>
  );
}
