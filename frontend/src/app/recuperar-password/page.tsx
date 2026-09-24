import { KeyRound } from "lucide-react";
import PantallaAuth from "@/components/auth/PantallaAuth";
import FormularioRecuperar from "@/components/auth/FormularioRecuperar";

export const metadata = { title: "Recuperar contraseña" };

/** FASE2-03: con email transaccional (ADR-000-06) la recuperación ya es autoservicio. */
export default function RecuperarPasswordPage() {
  return (
    <PantallaAuth>
      <span aria-hidden className="flex size-14 items-center justify-center rounded-2xl bg-marca-50 text-marca-700">
        <KeyRound className="size-7" />
      </span>
      <h1 className="mt-6 text-[32px] font-extrabold">Recuperar contraseña</h1>
      <p className="mt-2 text-[16px] text-tinta-suave">Ingresá tu DNI y te mandamos un enlace al email de tu cuenta.</p>
      <FormularioRecuperar />
    </PantallaAuth>
  );
}
