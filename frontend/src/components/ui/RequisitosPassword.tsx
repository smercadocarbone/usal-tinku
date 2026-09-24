import { Check } from "lucide-react";
import { cn } from "@/lib/cn";
import { requisitosPassword } from "@/lib/password";

/** Checklist en vivo de la política de contraseña (UX-03 §3). */
export default function RequisitosPassword({ password, dni, className }: { password: string; dni?: string | null; className?: string }) {
  return (
    <ul className={cn("flex list-none flex-col gap-1.5 p-0 text-sm", className)} aria-live="polite">
      {requisitosPassword(password, dni).map((r) => (
        <li key={r.texto} className={cn("flex items-center gap-2", r.cumple ? "text-exito" : "text-tinta-tenue")}>
          <span
            aria-hidden
            className={cn("flex size-5 items-center justify-center rounded-full", r.cumple ? "bg-exito text-white" : "border border-borde-control")}
          >
            {r.cumple && <Check className="size-3.5" />}
          </span>
          {r.texto}
          <span className="sr-only">{r.cumple ? "(cumplido)" : "(pendiente)"}</span>
        </li>
      ))}
    </ul>
  );
}
