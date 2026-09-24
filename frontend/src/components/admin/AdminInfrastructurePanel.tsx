"use client";

import { useState } from "react";
import {
  AlertTriangle,
  BrainCircuit,
  CreditCard,
  Database,
  FlaskConical,
  ScanText,
  Video,
  type LucideIcon,
} from "lucide-react";
import { Alerta, Cargando, Insignia, Tarjeta } from "@/components/ui";

export type ServiceStatusLevel = "operational" | "degraded" | "offline";

export interface ServiceStatus {
  name: string;
  status: ServiceStatusLevel;
  latencyMs?: number;
  lastChecked: string;
}

export interface SystemHealthDTO {
  isTestMode: boolean;
  hasSeedData: boolean;
  ocrEngine: ServiceStatus;
  mercadoPago: ServiceStatus;
  liveKit: ServiceStatus;
  iaMatching: ServiceStatus;
  database: ServiceStatus;
}

export interface Props {
  healthData: SystemHealthDTO;
  isPaymentGatewayEnabled: boolean;
  /** FASE2-07: false en producción — el Modo Bypass solo se activa fuera de `prod`. */
  bypassPermitido?: boolean;
  onTogglePaymentGateway: (enabled: boolean) => Promise<void>;
}

const STATUS_META = {
  operational: { nombre: "Operativo", dot: "bg-green-500 animate-pulse" },
  degraded: { nombre: "Degradado", dot: "bg-yellow-500" },
  offline: { nombre: "Falla de conexión", dot: "bg-red-500" },
} as const;

const SERVICIOS: ReadonlyArray<{
  campo: "ocrEngine" | "mercadoPago" | "liveKit" | "iaMatching" | "database";
  Icono: LucideIcon;
}> = [
  { campo: "ocrEngine", Icono: ScanText },
  { campo: "mercadoPago", Icono: CreditCard },
  { campo: "liveKit", Icono: Video },
  { campo: "iaMatching", Icono: BrainCircuit },
  { campo: "database", Icono: Database },
];

function IndicadorEstado({ nivel }: { nivel: ServiceStatusLevel }) {
  const meta = STATUS_META[nivel];
  return (
    <span className="inline-flex shrink-0 items-center gap-1.5">
      <span aria-hidden className={`h-2.5 w-2.5 rounded-full ${meta.dot}`} />
      <span className="text-xs font-medium text-slate-600">{meta.nombre}</span>
    </span>
  );
}

function TarjetaServicio({
  Icono,
  estado,
}: {
  Icono: LucideIcon;
  estado: ServiceStatus;
}) {
  return (
    <Tarjeta as="article" className="flex flex-col gap-3 shadow-none">
      <header className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-700">
            <Icono size={20} aria-hidden />
          </span>
          <h3 className="text-sm font-semibold text-slate-800">{estado.name}</h3>
        </div>
        <IndicadorEstado nivel={estado.status} />
      </header>
      <footer className="mt-auto flex flex-wrap gap-x-3 gap-y-1 text-xs text-slate-500">
        <span>
          Latencia: {estado.latencyMs != null ? `${estado.latencyMs} ms` : "—"}
        </span>
        <span aria-hidden>·</span>
        <span>Última comprobación: {estado.lastChecked}</span>
      </footer>
    </Tarjeta>
  );
}

export default function AdminInfrastructurePanel({
  healthData,
  isPaymentGatewayEnabled,
  bypassPermitido = true,
  onTogglePaymentGateway,
}: Props) {
  const [pagosHabilitados, setPagosHabilitados] = useState(isPaymentGatewayEnabled);
  const [togglingPagos, setTogglingPagos] = useState(false);
  const [errorToggle, setErrorToggle] = useState<string | null>(null);

  async function alternarPasarela() {
    const objetivo = !pagosHabilitados;
    setTogglingPagos(true);
    setErrorToggle(null);
    try {
      await onTogglePaymentGateway(objetivo);
      setPagosHabilitados(objetivo);
    } catch {
      setErrorToggle(
        "No se pudo actualizar la pasarela de pagos. Intentá de nuevo."
      );
    } finally {
      setTogglingPagos(false);
    }
  }

  return (
    <section aria-label="Salud de Infraestructura" className="flex flex-col gap-6">
      <header className="flex flex-col gap-4">
        <div>
          <h2 className="text-lg font-semibold text-slate-800">
            Salud de Infraestructura
          </h2>
          <p className="text-sm text-slate-500">
            Estado actual de los microservicios y APIs externas de la plataforma.
          </p>
        </div>
        {(healthData.isTestMode || healthData.hasSeedData) && (
          <div className="flex flex-wrap gap-3">
            {healthData.isTestMode && (
              <Insignia tono="aviso" className="py-1.5">
                <FlaskConical size={14} aria-hidden />
                Modo de prueba
              </Insignia>
            )}
            {healthData.hasSeedData && (
              // Púrpura no está en los tonos de Insignia: "base con datos de
              // seed" no es éxito/aviso/peligro, es una marca de entorno.
              <Insignia tono="neutro" className="bg-purple-100 py-1.5 text-purple-800">
                <Database size={14} aria-hidden />
                Base con datos de seed
              </Insignia>
            )}
          </div>
        )}
      </header>

      <Tarjeta as="section" className="shadow-none">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h3 className="text-sm font-semibold text-slate-800">
              Pasarela de pagos (MercadoPago)
            </h3>
            <p className="text-xs text-slate-500">
              Habilita o deshabilita el procesamiento de cobros reales.
            </p>
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={pagosHabilitados}
            aria-label="Habilitar la pasarela de pagos"
            // En producción solo se puede reactivar (apagarla daría el marketplace gratis).
            disabled={togglingPagos || (pagosHabilitados && !bypassPermitido)}
            onClick={alternarPasarela}
            className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer items-center rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${pagosHabilitados ? "bg-teal-600" : "bg-slate-300"}`}
          >
            <span
              aria-hidden
              className={`inline-block h-5 w-5 transform rounded-full bg-white shadow transition-transform ${pagosHabilitados ? "translate-x-6" : "translate-x-1"}`}
            />
          </button>
        </div>
        {!bypassPermitido && pagosHabilitados && (
          <p className="mt-3 text-sm text-tinta-suave">
            En producción la pasarela no se puede apagar: el Modo Bypass solo existe para entornos de prueba.
          </p>
        )}
        {togglingPagos && <Cargando className="mt-3">Aplicando cambio…</Cargando>}
        {!pagosHabilitados && (
          // `rol="alert"` explícito: que la plataforma esté cobrando en falso
          // sí amerita interrumpir al lector de pantalla, a diferencia de un
          // aviso común.
          <Alerta tono="aviso" rol="alert" className="mt-3 flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>
              Atención: La pasarela de pagos está en modo Bypass. Las reservas se
              confirmarán sin procesar cobros reales.
            </span>
          </Alerta>
        )}
        {errorToggle && (
          <Alerta tono="error" className="mt-3 flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>{errorToggle}</span>
          </Alerta>
        )}
      </Tarjeta>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        {SERVICIOS.map(({ campo, Icono }) => (
          <TarjetaServicio
            key={campo}
            Icono={Icono}
            estado={healthData[campo]}
          />
        ))}
      </div>
    </section>
  );
}