import Link from "next/link";
import PantallaAuth from "@/components/auth/PantallaAuth";
import WizardRegistro from "@/components/auth/WizardRegistro";

export const metadata = { title: "Dar clases en Tinku" };

export default function RegistroTutorPage() {
  return (
    <PantallaAuth
      ancho="md"
      pie={
        <>
          ¿Ya tenés cuenta? <Link href="/login" className="font-semibold">Ingresá</Link>
        </>
      }
    >
      <p className="mb-6 text-sm font-bold uppercase tracking-wider text-marca-700">Perfil de tutor</p>
      <WizardRegistro tipo="tutor" />
    </PantallaAuth>
  );
}
