import { CheckCircle2, CircleDashed, CircleSlash, Clock, Radio, Flag } from "lucide-react";
import { ETIQUETA_ESTADO_RESERVA, TONO_ESTADO_RESERVA, etiqueta } from "@/lib/etiquetas";
import Insignia from "./Insignia";

const ICONOS: Record<string, typeof Clock> = {
  pendiente_pago: Clock,
  confirmada: CheckCircle2,
  en_curso: Radio,
  finalizada: Flag,
  cancelada: CircleSlash,
};

/** Pastilla de estado: color + ícono + texto (nunca solo color). */
export default function EstadoReserva({ estado, className }: { estado: string; className?: string }) {
  const Icono = ICONOS[estado] ?? CircleDashed;
  return (
    <Insignia tono={TONO_ESTADO_RESERVA[estado] ?? "neutro"} icono={<Icono />} className={className}>
      {etiqueta(ETIQUETA_ESTADO_RESERVA, estado)}
    </Insignia>
  );
}
EstadoReserva.displayName = "EstadoReserva";
