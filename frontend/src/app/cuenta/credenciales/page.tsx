"use client";

import BannerCredencial from "@/components/BannerCredencial";
import CredencialGuiada from "@/components/tutor/CredencialGuiada";
import EditorEspecialidades from "@/components/tutor/EditorEspecialidades";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Cargando, Tarjeta } from "@/components/ui";
import { useEspecialidadesHabilitadas } from "@/lib/especialidades";

/**
 * Mi cuenta → Credenciales: título o certificado que respalda lo que enseñás. Con el flag de
 * ADR-M1-06 encendido en el backend, suma las especialidades y la credencial guiada por tipo; si no,
 * es la pantalla de siempre.
 */
export default function CredencialesPage() {
  const especialidades = useEspecialidadesHabilitadas();
  return (
    <SubpaginaTutor
      titulo="Credenciales académicas"
      descripcion="Tu título o certificado. Una persona del equipo lo revisa; mientras tanto podés completar el resto."
    >
      {especialidades === null ? (
        <Cargando>Cargando…</Cargando>
      ) : especialidades ? (
        <>
          <Tarjeta>
            <EditorEspecialidades />
          </Tarjeta>
          <Tarjeta>
            <CredencialGuiada />
          </Tarjeta>
        </>
      ) : (
        <Tarjeta>
          <BannerCredencial />
        </Tarjeta>
      )}
    </SubpaginaTutor>
  );
}
