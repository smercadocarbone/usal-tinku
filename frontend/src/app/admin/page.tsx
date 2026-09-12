"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";

import AdminInfrastructurePanel from "@/components/admin/AdminInfrastructurePanel";
import {
  getPasarelaEstado,
  getSaludSistema,
  mensajeDeError,
  setPasarelaEstado,
  type SystemHealthDTO,
} from "@/lib/api";

export default function AdminPage() {
  const [salud, setSalud] = useState<SystemHealthDTO | null>(null);
  const [pasarela, setPasarela] = useState<boolean | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let activo = true;
    Promise.all([getSaludSistema(), getPasarelaEstado()])
      .then(([saludSistema, pasarelaEstado]) => {
        if (!activo) return;
        setSalud(saludSistema);
        setPasarela(pasarelaEstado.habilitada);
      })
      .catch((err) => {
        if (activo) setError(mensajeDeError(err, "No se pudo consultar el estado de infraestructura."));
      })
      .finally(() => {
        if (activo) setCargando(false);
      });
    return () => {
      activo = false;
    };
  }, []);

  async function alternarPasarela(habilitada: boolean) {
    const resultado = await setPasarelaEstado(habilitada);
    setPasarela(resultado.habilitada);
  }

  if (cargando) {
    return (
      <p className="flex items-center gap-2 text-sm text-gray-500">
        <Loader2 className="animate-spin" size={16} aria-hidden />
        Consultando infraestructura…
      </p>
    );
  }

  if (error || salud === null || pasarela === null) {
    return (
      <div
        className="flex items-start gap-2 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
        role="alert"
      >
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
        <span>
          {error ??
            "La infraestructura no respondió. Volvé a intentar más tarde."}
        </span>
      </div>
    );
  }

  return (
    <AdminInfrastructurePanel
      healthData={salud}
      isPaymentGatewayEnabled={pasarela}
      onTogglePaymentGateway={alternarPasarela}
    />
  );
}