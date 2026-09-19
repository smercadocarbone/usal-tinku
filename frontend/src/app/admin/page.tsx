"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";

import Cabecera from "@/components/Cabecera";
import { Alerta, Cargando, PanelTab, Tabs } from "@/components/ui";
import AdminInfrastructurePanel from "@/components/admin/AdminInfrastructurePanel";
import ColaAlertas from "@/components/admin/ColaAlertas";
import ColaCredenciales from "@/components/admin/ColaCredenciales";
import ColaDenuncias from "@/components/admin/ColaDenuncias";
import ColaPagosFallidos from "@/components/admin/ColaPagosFallidos";
import PreciosRegionales from "@/components/admin/PreciosRegionales";
import TicketsSoporte from "@/components/admin/TicketsSoporte";
import {
  getPasarelaEstado,
  getSaludSistema,
  mensajeDeError,
  setPasarelaEstado,
  type SystemHealthDTO,
} from "@/lib/api";

// Orden por el Artículo II de la Constitución (seguridad del menor primero),
// no por módulo ni alfabético: Alertas de kill-switch > Denuncias >
// Credenciales (bloquea a un Tutor de dar clases) > lo financiero > soporte
// general > infraestructura.
const TABS = [
  { id: "alertas", label: "Alertas de Seguridad" },
  { id: "denuncias", label: "Denuncias" },
  { id: "credenciales", label: "Credenciales" },
  { id: "pagos", label: "Pagos fallidos" },
  { id: "precios", label: "Precios regionales" },
  { id: "tickets", label: "Tickets de soporte" },
  { id: "salud", label: "Salud de Infraestructura" },
] as const;

type IdTab = (typeof TABS)[number]["id"];

function SaludTab() {
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
    return <Cargando>Consultando infraestructura…</Cargando>;
  }

  if (error || salud === null || pasarela === null) {
    return (
      <Alerta tono="error" className="flex items-start gap-2">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
        <span>
          {error ?? "La infraestructura no respondió. Volvé a intentar más tarde."}
        </span>
      </Alerta>
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

export default function AdminPage() {
  const [tab, setTab] = useState<IdTab>("alertas");

  return (
    <>
      <Cabecera />

      <main className="mx-auto max-w-6xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Panel de Administración</h1>
        <p className="mt-1 text-sm text-slate-500">
          Esta sección solo responde si tu cuenta tiene rol de Admin — cada
          cola valida el permiso del lado del servidor.
        </p>

        <Tabs
          className="mt-6"
          opciones={TABS}
          activo={tab}
          onCambio={setTab}
          etiqueta="Secciones del panel de administración"
        />

        <div className="mt-6">
          {tab === "alertas" && (
            <PanelTab id="alertas">
              <ColaAlertas />
            </PanelTab>
          )}
          {tab === "denuncias" && (
            <PanelTab id="denuncias">
              <ColaDenuncias />
            </PanelTab>
          )}
          {tab === "credenciales" && (
            <PanelTab id="credenciales">
              <ColaCredenciales />
            </PanelTab>
          )}
          {tab === "pagos" && (
            <PanelTab id="pagos">
              <ColaPagosFallidos />
            </PanelTab>
          )}
          {tab === "precios" && (
            <PanelTab id="precios">
              <PreciosRegionales />
            </PanelTab>
          )}
          {tab === "tickets" && (
            <PanelTab id="tickets">
              <TicketsSoporte />
            </PanelTab>
          )}
          {tab === "salud" && (
            <PanelTab id="salud">
              <SaludTab />
            </PanelTab>
          )}
        </div>
      </main>
    </>
  );
}
