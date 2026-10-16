"use client";

import { useEffect, useState } from "react";
import { api, type CredencialPropia } from "./api";

/**
 * ADR-M1-06 (T-ESP-01..04): especialidades del Tutor y verificación de títulos. Todo depende del
 * flag del backend (`tinku.especialidades.habilitado`, apagado por defecto): si el backend no lo
 * informa encendido, la UI nueva no aparece.
 */

export type NivelEspecialidad = "PRIMARIO" | "SECUNDARIO" | "UNIVERSITARIO";
export type EstadoEspecialidad = "VERIFICADA" | "RESPALDO_DOCUMENTAL" | "DECLARADA";
export type TipoCredencialEspecialidades =
  | "SECUNDARIO"
  | "TERCIARIO_NO_DOCENTE"
  | "PROFESORADO"
  | "UNIVERSITARIO"
  | "ALUMNO_UNIVERSITARIO"
  | "MATRICULA_PROFESIONAL"
  | "OTRO";
export type MetodoVerificacion = "REGISTRO_OFICIAL" | "SOLO_DOCUMENTO";
export type MotivoRechazoCredencial =
  | "ILEGIBLE"
  | "NO_ES_TITULO"
  | "NOMBRE_NO_COINCIDE"
  | "NO_FIGURA_EN_REGISTRO"
  | "DATOS_NO_COINCIDEN"
  | "ENLACE_INVALIDO"
  | "OTRO";

export interface Especialidad {
  id: string;
  materia: string;
  nivelMaximo: NivelEspecialidad;
  estado: EstadoEspecialidad;
}

export const NIVELES: { value: NivelEspecialidad; label: string }[] = [
  { value: "PRIMARIO", label: "Primario" },
  { value: "SECUNDARIO", label: "Secundario" },
  { value: "UNIVERSITARIO", label: "Universitario" },
];

const ORDEN_NIVEL: Record<NivelEspecialidad, number> = { PRIMARIO: 0, SECUNDARIO: 1, UNIVERSITARIO: 2 };

export function nivelHasta(nivel: NivelEspecialidad, tope: NivelEspecialidad): boolean {
  return ORDEN_NIVEL[nivel] <= ORDEN_NIVEL[tope];
}

/** "Matemática · secundario" */
export function nombreEspecialidad(e: Pick<Especialidad, "materia" | "nivelMaximo">): string {
  return `${e.materia} · ${e.nivelMaximo.toLowerCase()}`;
}

export const ETIQUETA_ESTADO: Record<EstadoEspecialidad, string> = {
  VERIFICADA: "Verificada",
  RESPALDO_DOCUMENTAL: "Con respaldo documental",
  DECLARADA: "Declarada",
};

export const TIPOS_CREDENCIAL: { value: TipoCredencialEspecialidades; label: string }[] = [
  { value: "SECUNDARIO", label: "Título secundario" },
  { value: "TERCIARIO_NO_DOCENTE", label: "Terciario (no docente)" },
  { value: "PROFESORADO", label: "Profesorado" },
  { value: "UNIVERSITARIO", label: "Título universitario" },
  { value: "ALUMNO_UNIVERSITARIO", label: "Alumno universitario (certificado de alumno regular)" },
  { value: "MATRICULA_PROFESIONAL", label: "Matrícula profesional" },
  { value: "OTRO", label: "Otro" },
];

export const MOTIVOS_RECHAZO: { value: MotivoRechazoCredencial; label: string }[] = [
  { value: "ILEGIBLE", label: "No se puede leer el documento" },
  { value: "NO_ES_TITULO", label: "No es un título ni un certificado" },
  { value: "NOMBRE_NO_COINCIDE", label: "El nombre no coincide con el del DNI" },
  { value: "NO_FIGURA_EN_REGISTRO", label: "No figura en el registro oficial" },
  { value: "DATOS_NO_COINCIDEN", label: "Los datos no coinciden con el documento" },
  { value: "ENLACE_INVALIDO", label: "El enlace o código no funciona" },
  { value: "OTRO", label: "Otro (contalo en el detalle)" },
];

/** Campos extra de la cola del Admin; solo vienen con el flag encendido. */
export interface DatosVerificacionCola {
  tutorDni?: string;
  institucion?: string;
  denominacion?: string;
  anioEgreso?: number;
  esDigital?: boolean;
  enlaceVerificacion?: string;
  /** false = no coincide; ausente = no se pudo comparar. */
  nombreCoincide?: boolean;
  pasoASeguir?: string;
  /** Ausente = este tipo no respalda especialidades. */
  nivelMaximoQueRespalda?: NivelEspecialidad;
  especialidades?: Especialidad[];
}

export type DetalleDecisionCredencial =
  | { metodoVerificacion: MetodoVerificacion; especialidadesCubiertas: string[] }
  | { motivoRechazo: MotivoRechazoCredencial; detalleRechazo?: string };

export interface DatosCredencialEstructurada {
  tipoDocumento: TipoCredencialEspecialidades;
  institucion: string;
  denominacion: string;
  anioEgreso: number;
  esDigital: boolean;
  enlaceVerificacion?: string;
  aceptaConsultaRegistros: boolean;
}

export interface Prellenado {
  institucion: string | null;
  denominacion: string | null;
  anioEgreso: number | null;
}

/** Cualquier error (incluido un backend sin el endpoint) = apagado. */
export async function getEspecialidadesHabilitadas(): Promise<boolean> {
  try {
    const r = await api.get<{ habilitado?: boolean }>("/api/especialidades/config");
    return r?.habilitado === true;
  } catch {
    return false;
  }
}

/** `null` mientras se consulta; después, si el backend informa el flag encendido. */
export function useEspecialidadesHabilitadas(): boolean | null {
  const [habilitado, setHabilitado] = useState<boolean | null>(null);
  useEffect(() => {
    let vivo = true;
    getEspecialidadesHabilitadas().then((h) => vivo && setHabilitado(h));
    return () => {
      vivo = false;
    };
  }, []);
  return habilitado;
}

export function getMateriasCatalogo(): Promise<string[]> {
  return api.get("/api/especialidades/materias");
}

export function getMisEspecialidades(): Promise<Especialidad[]> {
  return api.get("/api/tutores/me/especialidades");
}

export function guardarEspecialidades(
  especialidades: { materia: string; nivelMaximo: NivelEspecialidad }[]
): Promise<Especialidad[]> {
  return api.put("/api/tutores/me/especialidades", { especialidades });
}

export function prellenarCredencial(archivo: File): Promise<Prellenado> {
  const form = new FormData();
  form.append("archivo", archivo);
  return api.post("/api/tutores/credenciales/prellenar", form);
}

export function subirCredencialEstructurada(
  datos: DatosCredencialEstructurada,
  archivo: File
): Promise<CredencialPropia> {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
  form.append("archivo", archivo);
  return api.post("/api/tutores/credenciales", form);
}
