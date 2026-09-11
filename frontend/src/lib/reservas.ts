export const ESTADO_ETIQUETA: Record<string, string> = {
  pendiente_pago: "Pendiente de pago",
  confirmada: "Confirmada",
  en_curso: "En curso",
  finalizada: "Finalizada",
  cancelada: "Cancelada",
  no_show_estudiante: "No se presento el estudiante",
  no_show_tutor: "No se presento el tutor",
  no_show_doble: "No se presentaron",
};

export interface Reserva {
  id: string;
  pagadorId: string;
  beneficiarioId: string | null;
  tutorId: string;
  horario: string;
  precio: number | null;
  estado: string;
  motivoCancelacion: string | null;
}