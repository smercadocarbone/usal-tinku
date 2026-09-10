"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { clearSession } from "@/lib/auth";

interface CabeceraProps {
  enlaces?: { href: string; label: string }[];
}

export default function Cabecera({ enlaces = [] }: CabeceraProps) {
  const router = useRouter();

  function logout() {
    clearSession();
    router.replace("/");
  }

  return (
    <header className="cabecera">
      <div className="marca" style={{ marginBottom: 0 }}>
        Tinku<span>.</span>
      </div>
      <nav style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
        {enlaces.map((e) => (
          <Link key={e.href} href={e.href} style={{ fontSize: "0.9rem" }}>
            {e.label}
          </Link>
        ))}
        <button
          type="button"
          className="boton boton--secundario"
          onClick={logout}
        >
          Cerrar sesion
        </button>
      </nav>
    </header>
  );
}
