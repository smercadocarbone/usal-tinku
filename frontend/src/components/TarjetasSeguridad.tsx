"use client";

import { CreditCard, IdCard, Video, type LucideIcon } from "lucide-react";
import { useIntersectionObserver } from "@/lib/useIntersectionObserver";

const GARANTIAS: { icono: LucideIcon; titulo: string; texto: string }[] = [
  {
    icono: CreditCard,
    titulo: "Pagos protegidos",
    texto:
      "El pago se procesa con MercadoPago y queda en escrow: el dinero solo se libera al finalizar la clase.",
  },
  {
    icono: Video,
    titulo: "Aulas seguras",
    texto:
      "Videollamada integrada con supervisión y resumen automático por IA: la clase queda documentada para el adulto responsable.",
  },
  {
    icono: IdCard,
    titulo: "Identidades verificadas",
    texto:
      "Cada Tutor valida su DNI y sus antecedentes antes de publicar su perfil. Sabés con quién habla tu hijo.",
  },
];

export default function TarjetasSeguridad() {
  const { ref, inView } = useIntersectionObserver<HTMLDivElement>();

  return (
    <div ref={ref} className="mt-10 grid grid-cols-1 gap-8 md:grid-cols-3">
      {GARANTIAS.map((g, i) => (
        <article
          key={g.titulo}
          className="rounded-tarjeta border border-borde bg-superficie p-7 shadow-tarjeta transition-transform duration-700 ease-out motion-reduce:transition-none"
          style={{
            opacity: inView ? 1 : 0,
            transform: inView ? "none" : "translateY(24px)",
            transitionDelay: inView ? `${i * 150}ms` : "0ms",
          }}
        >
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-accent/10 text-accent">
            <g.icono className="h-6 w-6" />
          </div>
          <h3 className="text-lg font-bold text-texto">{g.titulo}</h3>
          <p className="mt-2 text-texto-suave">{g.texto}</p>
        </article>
      ))}
    </div>
  );
}