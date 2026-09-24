import { cn } from "@/lib/cn";

/** Fondos de avatar: todos pasan 4.5:1 con texto blanco. */
const FONDOS = ["#146251", "#1d4ed8", "#7c3aed", "#b45309", "#be185d", "#0f766e", "#4d7c0f", "#475a55"];

function hash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function iniciales(nombre: string, apellido?: string): string {
  const partes = [nombre, apellido].filter(Boolean).join(" ").trim().split(/\s+/);
  const a = partes[0]?.[0] ?? "";
  const b = partes.length > 1 ? (partes[partes.length - 1]?.[0] ?? "") : "";
  return (a + b).toUpperCase() || "?";
}

const TAMANOS = {
  xs: "size-8 text-xs",
  sm: "size-10 text-sm",
  md: "size-12 text-base",
  lg: "size-16 text-xl",
  xl: "size-24 text-3xl",
} as const;

export interface AvatarProps {
  nombre: string;
  apellido?: string;
  /** Semilla del color (el id): mismo usuario, mismo color en toda la app. */
  semilla?: string;
  /** URL de la foto (U1). Si no hay, iniciales. */
  foto?: string | null;
  tamano?: keyof typeof TAMANOS;
  /** Sello de verificado sobre el avatar. */
  verificado?: boolean;
  className?: string;
}

/** Foto o iniciales. Decorativo: el nombre siempre está al lado en texto. */
export default function Avatar({ nombre, apellido, semilla, foto, tamano = "md", verificado, className }: AvatarProps) {
  const color = FONDOS[hash(semilla ?? `${nombre}${apellido ?? ""}`) % FONDOS.length];
  return (
    <span aria-hidden className={cn("relative inline-flex h-fit w-fit shrink-0 self-start", className)}>
      {foto ? (
        // eslint-disable-next-line @next/next/no-img-element -- blob/URL del backend autenticado, sin optimizador
        <img src={foto} alt="" className={cn("rounded-full object-cover ring-2 ring-superficie", TAMANOS[tamano])} />
      ) : (
        <span
          className={cn(
            "inline-flex items-center justify-center rounded-full font-bold tracking-tight text-white ring-2 ring-superficie",
            TAMANOS[tamano]
          )}
          style={{ backgroundColor: color }}
        >
          {iniciales(nombre, apellido)}
        </span>
      )}
      {verificado && (
        <span className="absolute -bottom-0.5 -right-0.5 inline-flex size-[38%] min-h-4 min-w-4 items-center justify-center rounded-full bg-marca-700 ring-2 ring-superficie">
          <svg viewBox="0 0 16 16" className="size-[65%] text-white" fill="none" stroke="currentColor" strokeWidth={2.5}>
            <path d="M3.5 8.5l3 3 6-7" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
      )}
    </span>
  );
}
Avatar.displayName = "Avatar";
