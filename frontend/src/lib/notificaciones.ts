/** Bandeja in-app (FASE2-03, `/api/notificaciones`). */
import { api } from "./api";

export type TipoNotificacion =
  | "KILLSWITCH_MENOR"
  | "DENUNCIA_RECIBIDA"
  | "CLASE_CANCELADA_TUTOR_SIN_HABILITACION"
  | "MP_CUENTA_DESCONECTADA";

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
    case "CLASE_CANCELADA_TUTOR_SIN_HABILITACION":
      return {
        titulo: "Cancelamos una clase de tu hijo o hija",
        detalle: `${n.datos.horario ? `La clase del ${formatear(n.datos.horario)} se canceló` : "Se canceló una clase"} porque el tutor ya no está habilitado para dar clases a menores. Te devolvemos el total de lo que pagaste.`,
        href: "/buscar",
        accion: "Buscar otro tutor",
        tono: "aviso",
      };
    case "MP_CUENTA_DESCONECTADA":
      return {
        titulo: "Volvé a conectar tu MercadoPago",
        detalle:
          "No pudimos renovar la conexión con tu cuenta. Hasta que la conectes de nuevo, no te pueden reservar clases nuevas; las que ya tenés siguen igual.",
        href: "/cuenta/cobros",
        accion: "Conectar MercadoPago",
        tono: "aviso",
      };
    default:
      return { titulo: "Aviso de Tinku", detalle: "", tono: "info" };
  }
}
