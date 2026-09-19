/**
 * Cliente HTTP centralizado hacia tinku-backend.
 *
 * Regla: ningún componente hace `fetch` directo a la API — todo pasa por
 * acá, para que el manejo de JWT, errores y la URL base vivan en un solo
 * lugar (mismo espíritu que "una sola función de reembolso" en el backend:
 * un único punto de verdad por responsabilidad transversal).
 */

import { clearSession, TOKEN_KEY } from "./auth";
import { catalogoMock } from "./catalogoMock";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detalles?: Record<string, unknown>
  ) {
    super(message);
  }
}

type Cuerpo = Record<string, unknown> | FormData | string | undefined;

async function request<T>(
  path: string,
  options: { method: string; body?: Cuerpo }
): Promise<T> {
  const token =
    typeof window !== "undefined" ? window.localStorage.getItem(TOKEN_KEY) : null;

  const esMultipart =
    typeof FormData !== "undefined" && options.body instanceof FormData;

  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (options.body !== undefined && !esMultipart && typeof options.body !== "string") {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    body:
      options.body instanceof FormData
        ? options.body
        : typeof options.body === "string"
          ? options.body
          : options.body === undefined
            ? undefined
            : JSON.stringify(options.body),
  });

  if (!res.ok) {
    const [mensaje, detalles] = await leerError(res);
    throw new ApiError(res.status, mensaje, detalles);
  }

  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

/** Extrae un mensaje legible y el detalle estructurado del cuerpo de error
 * del backend (contrato M1: `{"error": "...", ...extra}`). */
async function leerError(res: Response): Promise<[string, Record<string, unknown>?]> {
  let texto = "";
  try {
    texto = await res.text();
  } catch {
    return [res.statusText];
  }

  if (!texto) return [res.statusText];

  try {
    const json = JSON.parse(texto) as Record<string, unknown>;
    const mensaje =
      (typeof json.error === "string" && json.error) ||
      (typeof json.message === "string" && json.message) ||
      (typeof json.detail === "string" && json.detail);
    return [mensaje || res.statusText, json];
  } catch {
    return [texto, { body: texto }];
  }
}

async function manageSesion<T>(peticion: () => Promise<T>): Promise<T> {
  try {
    return await peticion();
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      const ruta = typeof window !== "undefined" ? window.location.pathname : "";
      if (ruta !== "/login" && ruta !== "/registro") {
        clearSession();
        if (typeof window !== "undefined") {
          window.location.assign("/login?expirado=1");
        }
      }
    }
    throw err;
  }
}

export const api = {
  get: <T>(path: string) =>
    manageSesion(() => request<T>(path, { method: "GET" })),
  post: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => request<T>(path, { method: "POST", body })),
  put: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => request<T>(path, { method: "PUT", body })),
  patch: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => request<T>(path, { method: "PATCH", body })),
  delete: <T>(path: string) =>
    manageSesion(() => request<T>(path, { method: "DELETE" })),
};

/* ---- Contrato 2b (M2): catálogo de temas + búsqueda ---- */

/** Árbol nivel → curso/carrera → materia → tema (contrato 2b, claves ya
 *  independientes de snake_case; solo `tema_ids`/`tutor_id` se mapean). */
export interface TemaCatalogo {
  id: string;
  nombre: string;
  descripcion: string;
}

export interface MateriaCatalogo {
  nombre: string;
  temas: TemaCatalogo[];
}

export interface CursoCatalogo {
  nombre: string;
  materias: MateriaCatalogo[];
}

export interface NivelCatalogo {
  nivel: string;
  cursos: CursoCatalogo[];
}

export interface FiltrosCatalogos {
  nivel?: string;
  curso?: string;
  materia?: string;
}

export interface MisTemas {
  temaIds: string[];
}

export interface ResultadoBusqueda {
  tutorId: string;
  score: number;
  noAutorizado: boolean;
}

export function getCatalogos(filtros?: FiltrosCatalogos): Promise<NivelCatalogo[]> {
  const params = new URLSearchParams();
  if (filtros?.nivel) params.set("nivel", filtros.nivel);
  if (filtros?.curso) params.set("curso", filtros.curso);
  if (filtros?.materia) params.set("materia", filtros.materia);
  const qs = params.toString();
  return api.get<NivelCatalogo[]>(`/api/catalogos${qs ? `?${qs}` : ""}`).catch((err) => {
    if (err instanceof ApiError && err.status !== 404) throw err;
    // ponytail: fallback local hasta que el backend aterrice en FASE 3
    // (orquestador). El GET real es la fuente; este fixture solo cubre
    // red caída / endpoint 404. Los filtros no aplican al fixture: nadie
    // los usa en este chunk todavía.
    return catalogoMock;
  });
}

interface MisTemasRaw {
  tema_ids: string[];
}

export function getMisTemas(): Promise<MisTemas> {
  return api.get<MisTemasRaw>("/api/tutores/me/temas").then((r) => ({
    temaIds: r.tema_ids,
  }));
}

export function setMisTemas(temaIds: string[]): Promise<void> {
  return api.put<void>("/api/tutores/me/temas", { tema_ids: temaIds });
}

export interface CuerpoBusqueda {
  textoBusqueda?: string;
  nombre?: string;
  filtroMateria?: string;
}

export function buscarTutores(body: CuerpoBusqueda): Promise<ResultadoBusqueda[]> {
  return api.post<ResultadoBusqueda[]>("/api/busquedas", {
    texto_busqueda: body.textoBusqueda || undefined,
    nombre: body.nombre || undefined,
    filtro_materia: body.filtroMateria || undefined,
  });
}

/** Mensaje legible desde un error de red o un ApiError del backend ({error}). */
export function mensajeDeError(err: unknown, fallback: string): string {
  return err instanceof ApiError && err.message ? err.message : fallback;
}

/* ---- M8 — Panel de administración: salud de infraestructura + pasarela ---- */

export type EstadoServicio = "operational" | "degraded" | "offline";

export interface ServiceStatus {
  name: string;
  status: EstadoServicio;
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

export interface PasarelaEstado {
  habilitada: boolean;
}

export function getSaludSistema(): Promise<SystemHealthDTO> {
  return api.get<SystemHealthDTO>("/api/admin/salud");
}

export function getPasarelaEstado(): Promise<PasarelaEstado> {
  return api.get<PasarelaEstado>("/api/admin/financiero/pasarela");
}

export function setPasarelaEstado(habilitada: boolean): Promise<PasarelaEstado> {
  return api.patch<PasarelaEstado>("/api/admin/financiero/pasarela", { habilitada });
}

/* ---- M8 — Colas del Admin de Moderación y Seguridad (US-1/2/3) ---- */

export type EstadoCredencial = "PENDIENTE" | "APROBADA" | "RECHAZADA";
export type TipoCredencial = "TITULO" | "CERTIFICADO_ANALITICO" | "MATRICULA";
export type DecisionCredencial = "APROBAR" | "RECHAZAR";

export interface CredencialCola {
  id: string;
  tutorId: string;
  tutorNombre: string;
  tutorApellido: string;
  tipoDocumento: TipoCredencial;
  estado: EstadoCredencial;
  numeroIntento: number;
  cicloEsperaHasta: string | null;
  createdAt: string;
}

export function getColaCredenciales(): Promise<CredencialCola[]> {
  return api.get("/api/admin/moderacion/credenciales");
}

export function resolverCredencial(
  id: string,
  decision: DecisionCredencial
): Promise<CredencialCola> {
  return api.post(`/api/admin/moderacion/credenciales/${id}/resolver`, { decision });
}

export type DecisionAlerta = "reactivar" | "sancionar";
export type TipoSancion =
  | "advertencia"
  | "suspension_temporal"
  | "suspension_definitiva"
  | "baneo_autoridades";

export interface AlertaSeguridadCola {
  id: string;
  sesionId: string;
  rama: string;
  detectadoId: string;
  estado: string;
  descargoTexto: string | null;
  descargoRecibidoAt: string | null;
  clipRetencionHasta: string | null;
  createdAt: string;
}

export interface ResolverAlertaBody {
  decision: DecisionAlerta;
  tipoSancion?: TipoSancion;
  diasSuspension?: number;
}

export function getColaAlertas(): Promise<AlertaSeguridadCola[]> {
  return api.get("/api/admin/moderacion/alertas");
}

export function resolverAlerta(
  id: string,
  body: ResolverAlertaBody
): Promise<AlertaSeguridadCola> {
  return api.post(`/api/admin/moderacion/alertas-seguridad/${id}/resolver`, body);
}

export type ResolucionDenuncia = "infundada" | "fundada" | "escalada";

export interface DenunciaCola {
  id: string;
  denunciadoId: string;
  estado: string;
  motivo: string;
  sesionId: string | null;
  descargoTexto: string | null;
  descargoVenceAt: string | null;
  slaResolucionVenceAt: string | null;
  prioridadAlta: boolean;
  resueltaAt: string | null;
  createdAt: string;
}

export interface ResolverDenunciaBody {
  resolucion: ResolucionDenuncia;
  tipoSancion?: TipoSancion;
  diasSuspension?: number;
}

export function getColaDenuncias(): Promise<DenunciaCola[]> {
  return api.get("/api/admin/moderacion/denuncias");
}

export function resolverDenuncia(
  id: string,
  body: ResolverDenunciaBody
): Promise<DenunciaCola> {
  return api.post(`/api/admin/moderacion/denuncias/${id}/resolver`, body);
}

/* ---- M8 — Colas del Admin de Soporte Financiero (US-5/US-8) ---- */

export interface PagoFallido {
  id: string;
  reservaId: string;
  montoBruto: number;
  estado: string;
  intentosLiberacion: number;
  liberarAt: string | null;
  createdAt: string;
}

export function getColaPagosFallidos(): Promise<PagoFallido[]> {
  return api.get("/api/admin/financiero/pagos-fallidos");
}

export function reintentarLiberacion(id: string): Promise<PagoFallido> {
  return api.post(`/api/admin/financiero/pagos-fallidos/${id}/reintentar`);
}

export function reembolsarParcial(id: string, monto: number): Promise<PagoFallido> {
  return api.post(`/api/admin/financiero/transacciones/${id}/reembolso-parcial`, { monto });
}

export interface PrecioRegional {
  provincia: string;
  valorSugerido: number;
  version: number;
  vigenteDesde: string;
}

export function actualizarPrecioRegional(
  provincia: string,
  valorSugerido: number
): Promise<PrecioRegional> {
  return api.post("/api/admin/financiero/precios-regionales", { provincia, valorSugerido });
}

/* ---- M8 — Canal de soporte (US-7) ---- */

export type RolAdmin = "moderacion_seguridad" | "soporte_financiero";
export type EstadoTicket = "abierto" | "en_proceso" | "resuelto" | "cerrado";

export interface TicketAdmin {
  id: string;
  usuarioId: string;
  origenModulo: string;
  asunto: string;
  detalle: string;
  estado: EstadoTicket;
  rolAsignado: RolAdmin;
  creadoEn: string;
  resueltoEn: string | null;
}

export function getTicketsAdmin(): Promise<TicketAdmin[]> {
  return api.get("/api/admin/tickets");
}

/* ---- M1 — Menores a cargo y autorizaciones (auditoría 2026-09-18/19) ---- */

export interface Menor {
  id: string;
  nombre: string;
  apellido: string;
}

/** Antes solo había alta y baja por id, sin forma de volver a listarlos. */
export function getMenores(): Promise<Menor[]> {
  return api.get("/api/usuarios/menores");
}

export function autorizarTutor(menorId: string, tutorId: string): Promise<{ id: string }> {
  return api.post("/api/autorizaciones", { menorId, tutorId });
}

/** Crea un ticket de soporte (US-7) — lo usa cualquier usuario autenticado,
 *  no solo el Admin. El enrutamiento por `origenModulo` lo resuelve el server. */
export function crearTicketSoporte(body: {
  origenModulo: string;
  asunto: string;
  detalle: string;
}): Promise<TicketAdmin> {
  return api.post("/api/soporte/tickets", body);
}

/* ---- M9 — Denuncias (US-1), del lado de quien denuncia ---- */

export type MotivoDenuncia =
  | "comportamiento_inapropiado"
  | "incumplimiento"
  | "fraude"
  | "contenido_ilegal"
  | "acoso";

export interface DenunciaCreada {
  id: string;
  denunciadoId: string;
  estado: string;
  motivo: MotivoDenuncia;
  sesionId: string | null;
  createdAt: string;
}

/** `sesionId` es opcional — una denuncia de perfil (sin sesión puntual) es
 *  un caso válido y así lo modela el backend. */
export function presentarDenuncia(body: {
  denunciadoId: string;
  sesionId?: string | null;
  motivo: MotivoDenuncia;
  evidenciaUrl?: string;
}): Promise<DenunciaCreada> {
  return api.post("/api/denuncias", {
    denunciadoId: body.denunciadoId,
    sesionId: body.sesionId ?? null,
    motivo: body.motivo,
    evidenciaUrl: body.evidenciaUrl,
  });
}