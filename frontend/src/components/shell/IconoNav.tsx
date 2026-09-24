import { CalendarDays, GraduationCap, LayoutDashboard, Search, UserRound, UsersRound, BookOpenCheck } from "lucide-react";
import type { IconoNav as TipoIcono } from "@/lib/navegacion";

const MAPA = {
  buscar: Search,
  clases: BookOpenCheck,
  chicos: UsersRound,
  cuenta: UserRound,
  agenda: CalendarDays,
  perfil: GraduationCap,
  panel: LayoutDashboard,
} as const;

export default function IconoNav({ icono, className }: { icono: TipoIcono; className?: string }) {
  const I = MAPA[icono];
  return <I className={className} aria-hidden />;
}
