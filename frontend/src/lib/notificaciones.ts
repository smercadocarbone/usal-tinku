/** Bandeja in-app (FASE2-03, `/api/notificaciones`). */
import { api } from "./api";

export type TipoNotificacion =
  | "KILLSWITCH_MENOR"
  | "DENUNCIA_RECIBIDA"
  | "CLASE_CANCELADA_TUTOR_SIN_HABILITACION"
  | "MP_CUENTA_DESCONECTADA"
  | "CLASE_RESERVADA"
  | "CLASE_CANCELADA"
  | "CLASE_REPROGRAMADA"
  | "RECORDATORIO_CLASE"
  | "CLASE_POR_EMPEZAR"
  | "CLASE_EMPEZO"
  | "PAGO_LIBERADO"
  | "CREDENCIAL_REVISADA"
  | "CAP_REVISADO";

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
    case "CLASE_RESERVADA":
      return {
        titulo: "Te reservaron una clase",
        detalle: `${n.datos.horario ? `Para el ${formatear(n.datos.horario)}` : "Ya está paga"}${n.datos.duracion ? `, ${n.datos.duracion} minutos` : ""}.`,
        href: n.datos.reservaId ? `/cuenta/reservas/${n.datos.reservaId}` : undefined,
        accion: "Ver la clase",
        tono: "info",
      };
    case "CLASE_CANCELADA":
      return {
        titulo: n.datos.canceladaPor === "tutor" ? "El tutor canceló una clase" : "Te cancelaron una clase",
        detalle: `${n.datos.horario ? `La clase del ${formatear(n.datos.horario)} se canceló.` : "Se canceló una clase."} La devolución o el pago siguen la política de cancelación.`,
        href: n.datos.reservaId ? `/cuenta/reservas/${n.datos.reservaId}` : undefined,
        accion: "Ver el detalle",
        tono: "aviso",
      };
    case "CLASE_REPROGRAMADA":
      return {
        titulo: "Cambiaron el horario de una clase",
        detalle:
          n.datos.horarioAnterior && n.datos.horario
            ? `Pasó del ${formatear(n.datos.horarioAnterior)} al ${formatear(n.datos.horario)}.`
            : "La clase tiene un horario nuevo.",
        href: n.datos.reservaId ? `/cuenta/reservas/${n.datos.reservaId}` : undefined,
        accion: "Ver la clase",
        tono: "info",
      };
    case "RECORDATORIO_CLASE":
      return {
        titulo: "Mañana tenés una clase",
        detalle: `${n.datos.horario ? `Es el ${formatear(n.datos.horario)}.` : ""} La sala se abre 5 minutos antes.`.trim(),
        href: n.datos.reservaId ? `/cuenta/reservas/${n.datos.reservaId}` : undefined,
        accion: "Ver la clase",
        tono: "info",
      };
    case "CLASE_POR_EMPEZAR":
      return {
        titulo: "Tu clase empieza en 5 minutos",
        detalle: "La sala ya está abierta.",
        href: n.datos.sesionId ? `/aula/${n.datos.sesionId}` : undefined,
        accion: "Entrar a la clase",
        tono: "aviso",
      };
    case "CLASE_EMPEZO":
      return {
        titulo: "Tu clase ya empezó",
        detalle: "Todavía no entraste y te están esperando.",
        href: n.datos.sesionId ? `/aula/${n.datos.sesionId}` : undefined,
        accion: "Entrar ahora",
        tono: "peligro",
      };
    case "PAGO_LIBERADO":
      return {
        titulo: "Te liberamos el pago de una clase",
        detalle: "Ya lo tenés en tu cuenta de MercadoPago.",
        href: "/cuenta/cobros",
        accion: "Ver mis cobros",
        tono: "info",
      };
    case "CREDENCIAL_REVISADA":
      return n.datos.resultado === "aprobada"
        ? { titulo: "Aprobamos tu título", detalle: "Ya podés aparecer en las búsquedas.", href: "/cuenta", accion: "Ver mi cuenta", tono: "info" }
        : { titulo: "No pudimos aprobar tu título", detalle: "Podés cargarlo de nuevo desde tu cuenta.", href: "/cuenta", accion: "Volver a cargarlo", tono: "aviso" };
    case "CAP_REVISADO":
      return n.datos.resultado === "aprobado"
        ? { titulo: "Aprobamos tu certificado de antecedentes", detalle: "Ya podés dar clases a menores.", href: "/cuenta", accion: "Ver mi cuenta", tono: "info" }
        : n.datos.resultado === "rechazado"
          ? { titulo: "No pudimos aprobar tu certificado", detalle: "Podés ver el detalle y volver a cargarlo.", href: "/cuenta", accion: "Ver mi cuenta", tono: "aviso" }
          : { titulo: "Tu certificado necesita una revisión más", detalle: "Te avisamos cuando esté.", tono: "info" };
    default:
      return { titulo: "Aviso de Tinku", detalle: "", tono: "info" };
  }
}
