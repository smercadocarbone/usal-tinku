import { fechaHoraCorta, fechaHoraLarga } from "@/lib/formatos";

export interface FechaHoraProps {
  inicio: string;
  fin?: string | null;
  formato?: "corto" | "largo";
  className?: string;
}

/** Siempre en hora argentina (UX-02 B3). `<time>` con el ISO para máquinas. */
export default function FechaHora({ inicio, fin, formato = "corto", className }: FechaHoraProps) {
  return (
    <time dateTime={inicio} className={className} suppressHydrationWarning>
      {formato === "largo" ? fechaHoraLarga(inicio, fin) : fechaHoraCorta(inicio)}
    </time>
  );
}
FechaHora.displayName = "FechaHora";
