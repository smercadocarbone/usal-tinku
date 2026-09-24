import Link from "next/link";
import PantallaAuth from "@/components/auth/PantallaAuth";
import WizardRegistro from "@/components/auth/WizardRegistro";

export const metadata = { title: "Crear cuenta" };

export default function RegistroPage() {
  return (
    <PantallaAuth
      ancho="md"
      pie={
        <>
          ¿Ya tenés cuenta? <Link href="/login" className="font-semibold">Ingresá</Link>
        </>
      }
    >
      <WizardRegistro tipo="adulto" />
    </PantallaAuth>
  );
}
