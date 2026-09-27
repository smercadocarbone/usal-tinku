/** Cobros del Tutor y conexión de MercadoPago (R3/R5, ADR-M5-02). */
import { api, guardarArchivo } from "./api";

export type EstadoCobro = "retenido" | "en_revision" | "liberado" | "reembolsado";

export interface Cobro {
  reservaId: string;
  horario: string;
  alumnoNombre: string;
  alumnoApellido: string;
  precioSesion: number;
  comision: number;
  neto: number;
  estado: EstadoCobro;
  liberaAt: string | null;
  simulado: boolean;
}

export interface MisCobros {
  retenido: number;
  enRevision: number;
  liberado: number;
  reembolsado: number;
  cobros: Cobro[];
}

export interface EstadoConexionMp {
  /** true si la plataforma cobra con OAuth por Tutor (hay que conectar para ser reservable). */
  requerida: boolean;
  estado: "CONECTADA" | "REVOCADA" | "ERROR" | null;
  conectadaAt: string | null;
}

export function getMisCobros(): Promise<MisCobros> {
  return api.get<MisCobros>("/api/pagos/mis-cobros");
}

export function getEstadoMp(): Promise<EstadoConexionMp> {
  return api.get<EstadoConexionMp>("/api/pagos/mp/estado");
}

export function urlConectarMp(): Promise<{ url: string }> {
  return api.get<{ url: string }>("/api/pagos/mp/conectar");
}

export function desconectarMp(): Promise<void> {
  return api.delete("/api/pagos/mp/conexion");
}

/** Los últimos `cantidad` meses (el actual primero) como AAAA-MM con su nombre para mostrar. */
export function ultimosMeses(cantidad = 12, hoy = new Date()): { valor: string; texto: string }[] {
  return Array.from({ length: cantidad }, (_, i) => {
    const fecha = new Date(hoy.getFullYear(), hoy.getMonth() - i, 1);
    const valor = `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}`;
    const texto = fecha.toLocaleDateString("es-AR", { month: "long", year: "numeric" });
    return { valor, texto: texto.charAt(0).toUpperCase() + texto.slice(1) };
  });
}

/** FR-PAG-020: CSV de los cobros del mes para facturar en ARCA. */
export async function descargarCobrosDelMes(mes: string): Promise<void> {
  const csv = await api.blob(`/api/pagos/cobros/export?mes=${encodeURIComponent(mes)}`);
  guardarArchivo(csv, `tinku-cobros-${mes}.csv`);
}
