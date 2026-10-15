"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import AdminInfrastructurePanel from "@/components/admin/AdminInfrastructurePanel";
import { Alerta, Cargando } from "@/components/ui";
import { useAdmin } from "@/components/admin/ContextoAdmin";
import {
  getPasarelaEstado,
  getSaludSistema,
  mensajeDeError,
  setPasarelaEstado,
  type SystemHealthDTO,
} from "@/lib/api";

export default function AdminSaludPage() {
  const [salud, setSalud] = useState<SystemHealthDTO | null>(null);
  const [pasarela, setPasarela] = useState<boolean | null>(null);
  const [bypassPermitido, setBypassPermitido] = useState(true);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { rol, rolResuelto } = useAdmin();
  // R-F F-2 (T4-H5): la pasarela es de Soporte Financiero; para Moderación no se pide (daba 403
  // y rompía toda la pantalla).
  const vePasarela = rol === "soporte_financiero";

  useEffect(() => {
    if (!rolResuelto) return;
    let activo = true;
    Promise.all([getSaludSistema(), vePasarela ? getPasarelaEstado() : Promise.resolve(null)])
      .then(([saludSistema, pasarelaEstado]) => {
        if (!activo) return;
        setSalud(saludSistema);
        setPasarela(pasarelaEstado ? pasarelaEstado.habilitada : true);
        setBypassPermitido(pasarelaEstado ? pasarelaEstado.bypassPermitido !== false : true);
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
  }, [rolResuelto, vePasarela]);

  async function alternarPasarela(habilitada: boolean) {
    const resultado = await setPasarelaEstado(habilitada);
    setPasarela(resultado.habilitada);
  }

  return (
    <section>
      <h2 className="text-2xl font-bold">Salud de Infraestructura</h2>
      <div className="mt-4">
        {cargando ? (
          <Cargando>Consultando infraestructura…</Cargando>
        ) : error || salud === null || pasarela === null ? (
          <Alerta tono="error" className="flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>
              {error ?? "La infraestructura no respondió. Volvé a intentar más tarde."}
            </span>
          </Alerta>
        ) : (
          <AdminInfrastructurePanel
            healthData={salud}
            isPaymentGatewayEnabled={pasarela}
            bypassPermitido={bypassPermitido}
            mostrarPasarela={vePasarela}
            onTogglePaymentGateway={alternarPasarela}
          />
        )}
      </div>
    </section>
  );
}
