import type { ReactNode } from "react";
import Link from "next/link";
import { BadgeCheck, ShieldCheck, WalletCards } from "lucide-react";
import Logo from "@/components/Logo";
import { TIEMPOS } from "@/lib/tiempos";

/**
 * Layout de login y registros (UX-03 §2): formulario + panel de marca en desktop;
 * una sola columna en mobile. El logo siempre lleva a `/`.
 */
export default function PantallaAuth({
  children,
  pie,
  ancho = "sm",
}: {
  children: ReactNode;
  /** Link secundario abajo ("¿No tenés cuenta? Creala"). */
  pie?: ReactNode;
  ancho?: "sm" | "md";
}) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[1fr_minmax(420px,0.8fr)]">
      <div className="flex flex-col">
        <header className="flex h-16 items-center justify-between px-4 sm:px-8">
          <Logo />
          <Link href="/" className="text-sm font-semibold text-tinta-suave no-underline hover:text-tinta">
            Volver al inicio
          </Link>
        </header>
        <main className="flex flex-1 items-start justify-center px-4 pb-12 pt-4 sm:items-center sm:px-8">
          <div className={ancho === "md" ? "w-full max-w-[560px]" : "w-full max-w-[440px]"}>
            {children}
            {pie && <div className="mt-8 text-center text-[15px] text-tinta-suave">{pie}</div>}
          </div>
        </main>
      </div>
      <aside className="relative hidden overflow-hidden bg-marca-950 p-12 text-white lg:flex lg:flex-col lg:justify-end">
        <div aria-hidden className="absolute -right-24 -top-24 size-96 rounded-full bg-marca-700/40 blur-3xl" />
        <div aria-hidden className="absolute -bottom-32 -left-16 size-80 rounded-full bg-acento-500/20 blur-3xl" />
        <div className="relative">
          <p className="max-w-sm text-[34px] font-extrabold leading-tight text-white">
            Las clases particulares, con la tranquilidad de saber con quién.
          </p>
          <ul className="mt-10 flex list-none flex-col gap-5 p-0">
            {[
              { i: BadgeCheck, t: "Tutores con identidad y título verificados" },
              { i: WalletCards, t: `El tutor cobra ${TIEMPOS.liberacionHoras} hs después de la clase` },
              { i: ShieldCheck, t: "Los chicos nunca pagan ni eligen tutores solos" },
            ].map(({ i: Icono, t }) => (
              <li key={t} className="flex items-center gap-4 text-[16px] text-white/85">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-white/10">
                  <Icono className="size-5 text-marca-200" aria-hidden />
                </span>
                {t}
              </li>
            ))}
          </ul>
        </div>
      </aside>
    </div>
  );
}
