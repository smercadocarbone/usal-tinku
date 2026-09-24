import { IdCard, OctagonX, ShieldCheck, WalletCards, type LucideIcon } from "lucide-react";
import { TIEMPOS } from "@/lib/tiempos";

/**
 * Garantías de la landing. Cada una es REAL y verificable en el código (UX-03 §1.4):
 * no se promete lo que no existe (ni emails ni el clasificador automático, T-M3-06).
 */
const GARANTIAS: { icono: LucideIcon; titulo: string; texto: string }[] = [
  {
    icono: IdCard,
    titulo: "Identidades verificadas",
    texto:
      "Todos, tutores y familias, validan su DNI al crear la cuenta. La foto del documento se usa para verificar y no se guarda.",
  },
  {
    icono: WalletCards,
    titulo: "Pagos protegidos",
    texto: `El pago se procesa con MercadoPago y queda retenido: al tutor se le libera ${TIEMPOS.liberacionHoras} hs después de la clase.`,
  },
  {
    icono: ShieldCheck,
    titulo: "Los chicos nunca solos",
    texto:
      "Un menor no paga ni elige tutores por su cuenta: todo pasa por su adulto responsable, que autoriza a cada tutor.",
  },
  {
    icono: OctagonX,
    titulo: "Aulas seguras",
    texto:
      "Las clases son en el aula de Tinku. Si algo no está bien, se corta al instante; con un menor presente, se corta sin preguntar.",
  },
];

export default function TarjetasSeguridad() {
  return (
    <ul className="mt-10 grid list-none grid-cols-1 gap-4 p-0 sm:grid-cols-2 lg:grid-cols-4">
      {GARANTIAS.map((g) => (
        <li key={g.titulo} className="rounded-tarjeta bg-white/[0.06] p-6 ring-1 ring-white/10">
          <span aria-hidden className="mb-5 flex size-12 items-center justify-center rounded-2xl bg-marca-300/15 text-marca-200">
            <g.icono className="size-6" />
          </span>
          <h3 className="text-lg font-bold text-white">{g.titulo}</h3>
          <p className="mt-2 text-[15px] leading-relaxed text-white/75">{g.texto}</p>
        </li>
      ))}
    </ul>
  );
}
