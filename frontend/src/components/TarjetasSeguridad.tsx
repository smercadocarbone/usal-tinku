import { CreditCard, IdCard, Video, type LucideIcon } from "lucide-react";
import { Tarjeta } from "@/components/ui";

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
      "Cada Tutor valida su DNI y su credencial académica antes de poder dar clases. Sabés con quién habla tu hijo.",
  },
];

export default function TarjetasSeguridad() {
  return (
    <div className="mt-10 grid grid-cols-1 gap-8 md:grid-cols-3">
      {GARANTIAS.map((g) => (
        <Tarjeta key={g.titulo} as="article" className="p-7">
          <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-teal-600/10 text-teal-700">
            <g.icono className="h-6 w-6" />
          </div>
          <h3 className="text-lg font-bold text-slate-800">{g.titulo}</h3>
          <p className="mt-2 text-slate-500">{g.texto}</p>
        </Tarjeta>
      ))}
    </div>
  );
}