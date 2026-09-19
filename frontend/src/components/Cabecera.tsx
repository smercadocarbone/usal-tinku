"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearSession } from "@/lib/auth";
import { Boton } from "@/components/ui";

interface CabeceraProps {
  enlaces?: { href: string; label: string }[];
}

const SIN_ENLACES: { href: string; label: string }[] = [];

export default function Cabecera({ enlaces = SIN_ENLACES }: CabeceraProps) {
  const router = useRouter();

  function logout() {
    clearSession();
    router.replace("/");
  }

  return (
    <header className="flex items-center justify-between border-b border-slate-200 bg-white px-5 py-3.5">
      <div className="text-lg font-bold text-slate-800">
        Tinku<span className="text-teal-700">.</span>
      </div>
      <nav className="flex items-center gap-4">
        {enlaces.map((e) => (
          <Link
            key={e.href}
            href={e.href}
            className="text-sm font-semibold"
          >
            {e.label}
          </Link>
        ))}
        <Boton variante="secundario" onClick={logout}>
          Cerrar sesion
        </Boton>
      </nav>
    </header>
  );
}