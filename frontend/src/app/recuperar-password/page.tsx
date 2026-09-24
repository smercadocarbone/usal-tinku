import Link from "next/link";
import { KeyRound } from "lucide-react";
import PantallaAuth from "@/components/auth/PantallaAuth";
import { Alerta, clasesBoton } from "@/components/ui";

export const metadata = { title: "Recuperar contraseña" };

/**
 * El envío de emails todavía no existe (AUD-008/014, P5): el backend solo escribe
 * el token de recuperación en el log. Prometer un enlace que nunca sale es
 * mentirle al usuario, así que la pantalla explica el canal real (UX-03 §5).
 */
export default function RecuperarPasswordPage() {
  return (
    <PantallaAuth>
      <span aria-hidden className="flex size-14 items-center justify-center rounded-2xl bg-marca-50 text-marca-700">
        <KeyRound className="size-7" />
      </span>
      <h1 className="mt-6 text-[32px] font-extrabold">Recuperar contraseña</h1>
      <p className="mt-2 text-[16px] text-tinta-suave">¿No podés entrar a tu cuenta?</p>
      <Alerta tono="aviso" className="mt-6" titulo="Todavía no mandamos emails de recuperación">
        Si no podés entrar, escribinos a soporte de Tinku y una persona del equipo te ayuda a recuperar tu cuenta.
      </Alerta>
      <Link href="/login" className={clasesBoton("secundario", "lg", "mt-8 w-full")}>
        Volver a ingresar
      </Link>
    </PantallaAuth>
  );
}
