"use client";

import { useEffect, useState } from "react";
import { api, type PerfilPropio } from "@/lib/api";
import TabHorarios from "@/components/tutor/TabHorarios";
import { Alerta, Cargando, Tarjeta } from "@/components/ui";

export default function CuentaHorariosPage() {
  const [tutorId, setTutorId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // B4: el `sub` del JWT es hoy el DNI, no el UUID del Tutor — mandarlo como
  // `tutorId` hace que GET /api/tutores/{dni}/franjas devuelva 403 y la
  // agenda quede vacía. El id del Tutor sale de GET /api/usuarios/me.
  useEffect(() => {
    api
      .get<PerfilPropio>("/api/usuarios/me")
      .then((p) => setTutorId(p.id))
      .catch(() => setError("No se pudo cargar tu agenda de tutor."));
  }, []);

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Mis Horarios</h2>
      <Tarjeta className="mt-4 w-full">
        {error ? (
          <Alerta tono="error">{error}</Alerta>
        ) : tutorId === null ? (
          <Cargando>Cargando tu agenda…</Cargando>
        ) : (
          <TabHorarios tutorId={tutorId} />
        )}
      </Tarjeta>
    </section>
  );
}