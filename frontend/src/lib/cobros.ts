/** Cobros del Tutor y conexión de MercadoPago (R3/R5, ADR-M5-02). */
import { api } from "./api";

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
