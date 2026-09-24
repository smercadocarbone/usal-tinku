"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  getColaAlertas,
  getColaCredenciales,
  getColaDenuncias,
  getColaPagosFallidos,
  getPasarelaEstado,
  getTicketsAdmin,
  type RolAdmin,
} from "@/lib/api";
import { useRolAdmin } from "@/lib/usePerfil";
import { TIEMPOS } from "@/lib/tiempos";

export interface Cola {
  clave: "alertas" | "denuncias" | "credenciales" | "pagos" | "tickets";
  cantidad: number;
  /** Plazo más próximo de la cola (ISO), si la cola tiene plazos. */
  proximoVence: string | null;
  /** Hay algo con el plazo por vencer. */
  urgente: boolean;
}

interface Estado {
  rol: RolAdmin | null;
  colas: Partial<Record<Cola["clave"], Cola>>;
  /** Pasarela apagada = Modo Bypass (solo lo puede leer Soporte Financiero). */
  bypass: boolean;
  cargando: boolean;
}

const Contexto = createContext<Estado>({ rol: null, colas: {}, bypass: false, cargando: true });

const HORA = 3600000;

function cola(clave: Cola["clave"], plazos: (string | null)[], umbralMs: number): Cola {
  const validos = plazos.filter((p): p is string => !!p).sort();
  const proximo = validos[0] ?? null;
  const ahora = Date.now();
  return {
    clave,
    cantidad: plazos.length,
    proximoVence: proximo,
    urgente: proximo !== null && new Date(proximo).getTime() - ahora < umbralMs,
  };
}

/**
 * Contadores de pendientes por cola, según el rol (UX-08 §1). Las colas ya vienen
 * ordenadas por urgencia del backend; acá solo se cuentan y se toma el plazo más
 * próximo. Una Alerta de kill-switch tiene 12 hs de revisión desde que se creó.
 */
export function ProveedorAdmin({ children }: { children: ReactNode }) {
  const rol = useRolAdmin(true);
  const [colas, setColas] = useState<Estado["colas"]>({});
  const [bypass, setBypass] = useState(false);
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    if (!rol) return;
    let vivo = true;
    const tareas: Promise<unknown>[] = [];
    if (rol === "moderacion_seguridad") {
      tareas.push(
        getColaAlertas().then((l) => {
          const plazos = l.map((a) => new Date(new Date(a.createdAt).getTime() + TIEMPOS.revisionAlertaHoras * HORA).toISOString());
          if (vivo) setColas((c) => ({ ...c, alertas: cola("alertas", plazos, 3 * HORA) }));
        }),
        getColaDenuncias().then((l) => {
          if (vivo) setColas((c) => ({ ...c, denuncias: cola("denuncias", l.map((d) => d.slaResolucionVenceAt), 24 * HORA) }));
        }),
        getColaCredenciales().then((l) => {
          const plazos = l.map((x) => new Date(new Date(x.createdAt).getTime() + TIEMPOS.revisionCredencialHoras * HORA).toISOString());
          if (vivo) setColas((c) => ({ ...c, credenciales: cola("credenciales", plazos, 6 * HORA) }));
        })
      );
    } else {
      tareas.push(
        getColaPagosFallidos().then((l) => {
          if (vivo) setColas((c) => ({ ...c, pagos: cola("pagos", l.map(() => null), 0) }));
        }),
        getPasarelaEstado().then((p) => vivo && setBypass(!p.habilitada))
      );
    }
    tareas.push(
      getTicketsAdmin().then((l) => {
        const abiertos = l.filter((t) => t.estado === "abierto" || t.estado === "en_proceso");
        if (vivo) setColas((c) => ({ ...c, tickets: cola("tickets", abiertos.map(() => null), 0) }));
      })
    );
    void Promise.allSettled(tareas).then(() => vivo && setCargando(false));
    return () => {
      vivo = false;
    };
  }, [rol]);

  const valor = useMemo(() => ({ rol, colas, bypass, cargando }), [rol, colas, bypass, cargando]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useAdmin(): Estado {
  return useContext(Contexto);
}
