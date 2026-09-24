/** Bandeja in-app (FASE2-03, `/api/notificaciones`). */
import { api } from "./api";

export type TipoNotificacion = "KILLSWITCH_MENOR" | "DENUNCIA_RECIBIDA";

export interface Notificacion {
  id: string;
  tipo: TipoNotificacion | string;
  datos: Record<string, string>;
  creadaAt: string;
  leida: boolean;
}

export function getNotificaciones(pagina = 0): Promise<Notificacion[]> {
  return api.get<Notificacion[]>(`/api/notificaciones?pagina=${pagina}`);
}

export function getNoLeidas(): Promise<{ cantidad: number }> {
  return api.get<{ cantidad: number }>("/api/notificaciones/no-leidas");
}

export function marcarLeida(id: string): Promise<Notificacion> {
  return api.post<Notificacion>(`/api/notificaciones/${id}/leida`);
}

export interface TextoNotificacion {
  titulo: string;
  detalle: string;
  href?: string;
  accion?: string;
  tono: "peligro" | "aviso" | "info";
}

/** Texto de cada aviso. Mismo criterio que el email: nada del contenido detectado ni
 *  del denunciante; el detalle está en la pantalla que corresponde. */
export function textoDe(n: Notificacion, formatear: (iso: string) => string): TextoNotificacion {
  switch (n.tipo) {
    case "KILLSWITCH_MENOR":
      return {
        titulo: "Cortamos una clase de tu hijo o hija por seguridad",
        detalle:
          "Se activó el corte de seguridad y la clase terminó en ese momento. El equipo de Tinku ya está revisando lo que pasó; no tenés que hacer nada ahora.",
        tono: "peligro",
      };
    case "DENUNCIA_RECIBIDA":
      return {
        titulo: "Recibiste una denuncia",
        detalle: n.datos.descargoVenceAt
          ? `Podés contar tu versión hasta el ${formatear(n.datos.descargoVenceAt)}.`
          : "Podés contar tu versión antes de que se decida.",
        href: "/cuenta/seguridad",
        accion: "Presentar mi descargo",
        tono: "aviso",
      };
    default:
      return { titulo: "Aviso de Tinku", detalle: "", tono: "info" };
  }
}
