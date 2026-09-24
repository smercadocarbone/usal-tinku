import type { ReactNode } from "react";
import { cn } from "@/lib/cn";
import Cabecera from "@/components/Cabecera";
import NavInferior from "./NavInferior";

export interface AppShellProps {
  children: ReactNode;
  /** `contenido` 1120 px · `angosto` 720 px · `formulario` 480 px · `completo` sin límite. */
  ancho?: "contenido" | "angosto" | "formulario" | "completo";
  className?: string;
}

const ANCHOS = {
  contenido: "max-w-[1120px]",
  angosto: "max-w-[720px]",
  formulario: "max-w-[480px]",
  completo: "max-w-none",
} as const;

/** Cabecera + contenido + barra inferior mobile. Toda pantalla logueada vive acá. */
export default function AppShell({ children, ancho = "contenido", className }: AppShellProps) {
  return (
    <>
      <Cabecera />
      <main className={cn("pb-nav mx-auto w-full px-4 pt-6 sm:px-6 sm:pt-10 lg:pb-16", ANCHOS[ancho], className)}>
        {children}
      </main>
      <NavInferior />
    </>
  );
}
