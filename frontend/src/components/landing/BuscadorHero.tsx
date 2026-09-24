"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { ArrowRight, Search } from "lucide-react";
import { useSesion } from "@/lib/useSesion";

/**
 * Buscador de una línea del hero. Buscar exige cuenta (decisión U2 = no, 2026-09-24):
 * sin sesión, lleva a crear la cuenta con la búsqueda preservada para después del
 * login; con sesión, directo a los resultados.
 */
export default function BuscadorHero() {
  const [texto, setTexto] = useState("");
  const router = useRouter();
  const sesion = useSesion();

  function buscar(e: React.FormEvent) {
    e.preventDefault();
    const q = texto.trim();
    const destino = q ? `/buscar?q=${encodeURIComponent(q)}` : "/buscar";
    router.push(sesion ? destino : `/registro?siguiente=${encodeURIComponent(destino)}`);
  }

  return (
    <form onSubmit={buscar} role="search" className="w-full max-w-xl">
      <label htmlFor="hero-buscar" className="sr-only">
        ¿Qué necesitás aprender?
      </label>
      <div className="flex items-center gap-2 rounded-[18px] bg-superficie p-2 shadow-flotante ring-1 ring-borde focus-within:ring-2 focus-within:ring-marca-600">
        <Search className="ml-3 size-5 shrink-0 text-tinta-tenue" aria-hidden />
        <input
          id="hero-buscar"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          placeholder="¿Qué necesitás aprender?"
          className="min-h-12 w-full min-w-0 bg-transparent px-1 text-[17px] text-tinta placeholder:text-tinta-tenue focus:outline-none"
          autoComplete="off"
        />
        <button
          type="submit"
          className="inline-flex min-h-12 shrink-0 cursor-pointer items-center gap-2 rounded-control bg-tinta px-5 text-[15px] font-bold text-white transition-colors hover:bg-black"
        >
          <span className="hidden sm:inline">Buscar tutor</span>
          <ArrowRight className="size-5 sm:hidden" aria-hidden />
          <span className="sr-only sm:hidden">Buscar tutor</span>
        </button>
      </div>
      <p className="mt-3 text-sm text-tinta-suave">
        Para ver tutores y reservar te pedimos una cuenta: así sabemos que del otro lado hay una persona verificada.
      </p>
    </form>
  );
}
