"use client";

import BannerCredencial from "@/components/BannerCredencial";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Tarjeta } from "@/components/ui";

/** Mi cuenta → Credenciales: título o certificado que respalda lo que enseñás. */
export default function CredencialesPage() {
  return (
    <SubpaginaTutor
      titulo="Credenciales académicas"
      descripcion="Tu título o certificado. Una persona del equipo lo revisa; mientras tanto podés completar el resto."
    >
      <Tarjeta>
        <BannerCredencial />
      </Tarjeta>
    </SubpaginaTutor>
  );
}
