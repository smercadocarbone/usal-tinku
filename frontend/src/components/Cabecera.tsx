"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearSession } from "@/lib/auth";

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
    <header className="flex items-center justify-between border-b border-borde bg-superficie px-5 py-[0.9rem]">
      <div className="text-[1.05rem] font-bold text-texto">
        Tinku<span className="text-accent">.</span>
      </div>
      <nav className="flex items-center gap-4">
        {enlaces.map((e) => (
          <Link
            key={e.href}
            href={e.href}
            className="text-[0.9rem] font-semibold"
          >
            {e.label}
          </Link>
        ))}
        <button
          type="button"
          className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
          onClick={logout}
        >
          Cerrar sesion
        </button>
      </nav>
    </header>
  );
}