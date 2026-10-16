import { BadgeCheck } from "lucide-react";
import { Insignia, type TonoInsignia } from "@/components/ui";
import { cn } from "@/lib/cn";
import { ETIQUETA_ESTADO, nombreEspecialidad, type Especialidad, type EstadoEspecialidad } from "@/lib/especialidades";

const TONO: Record<EstadoEspecialidad, TonoInsignia> = {
  VERIFICADA: "exito",
  RESPALDO_DOCUMENTAL: "info",
  DECLARADA: "neutro",
};

/**
 * ADR-M1-06: una insignia por especialidad ("Matemática · secundario — Verificada") en vez del
 * "Título verificado" genérico. Las declaradas también se muestran: quien elige es el alumno o
 * su adulto responsable.
 */
export default function InsigniasEspecialidad({
  especialidades,
  className,
}: {
  especialidades: Especialidad[];
  className?: string;
}) {
  if (especialidades.length === 0) return null;
  return (
    <ul aria-label="Especialidades" className={cn("flex list-none flex-wrap gap-2 p-0", className)}>
      {especialidades.map((e) => (
        <li key={e.id} className="flex items-center gap-1.5 text-sm">
          <span className="font-semibold text-tinta">{nombreEspecialidad(e)}</span>
          <Insignia
            tono={TONO[e.estado]}
            tamano="sm"
            icono={e.estado === "VERIFICADA" ? <BadgeCheck aria-hidden /> : undefined}
          >
            {ETIQUETA_ESTADO[e.estado]}
          </Insignia>
        </li>
      ))}
    </ul>
  );
}
