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

/** Auditoría 2026-09-19: tenía "APROBADA"/"RECHAZADA" (femenino) pero el
 *  enum Java (sin `@JsonValue`, serializa por nombre de constante) es
 *  "APROBADO"/"RECHAZADO" — ningún estado resuelto matcheaba nunca. Inocuo
 *  hasta ahora porque `ColaCredenciales` no compara contra `estado` (la
 *  cola de {@code colaPendientes()} solo trae PENDIENTE), pero el tipo
 *  mentía sobre el contrato real. */
export type EstadoCredencial = "PENDIENTE" | "APROBADO" | "RECHAZADO";
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

/** Auditoría 2026-09-18 (backend) / 2026-09-20 (este gap): el endpoint de
 *  transición de estado del ticket existía, pero el panel de Admin era
 *  de solo lectura — nunca lo llamaba. */
export function actualizarEstadoTicket(id: string, estado: EstadoTicket): Promise<TicketAdmin> {
  return api.patch(`/api/admin/tickets/${id}`, { estado });
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

/* ---- M9 — Mis denuncias recibidas y mis alertas de seguridad (auditoría 2026-09-20) ----
 * Antes de esto, el backend ya soportaba el descargo (derecho a réplica) pero
 * no había NINGÚN endpoint para que el propio denunciado/detectado se
 * enterara de que existía un caso — el plazo de descargoVenceAt corría en
 * silencio hasta escalar. */

export type EstadoDenunciaRecibida =
  | "registrada"
  | "en_revision"
  | "resuelta_infundada"
  | "resuelta_fundada"
  | "escalada";

export interface DenunciaRecibida {
  id: string;
  denunciadoId: string;
  estado: EstadoDenunciaRecibida;
  motivo: MotivoDenuncia;
  sesionId: string | null;
  descargoTexto: string | null;
  descargoVenceAt: string | null;
  slaResolucionVenceAt: string | null;
  prioridadAlta: boolean;
  resueltaAt: string | null;
  createdAt: string;
}

export function getDenunciasRecibidas(): Promise<DenunciaRecibida[]> {
  return api.get("/api/denuncias/recibidas");
}

export function presentarDescargoDenuncia(id: string, descargo: string): Promise<DenunciaRecibida> {
  return api.post(`/api/denuncias/${id}/descargo`, { descargo });
}

export interface AlertaPropia {
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

export function getAlertasMias(): Promise<AlertaPropia[]> {
  return api.get("/api/alertas-seguridad/mias");
}

export function presentarDescargoAlerta(id: string, descargo: string): Promise<AlertaPropia> {
  return api.post(`/api/alertas-seguridad/${id}/descargo`, { descargo });
}

/* ---- M3 — Sesión de una Reserva (backend: auditoría 2026-09-19) ---- */

export type EstadoSesion =
  | "no_iniciada"
  | "en_curso"
  | "finalizada"
  | "finalizada_anticipada"
  | "interrumpida";

export interface SesionInfo {
  id: string;
  reservaId: string;
  estado: EstadoSesion;
  livekitRoomId: string | null;
  inicioReal: string | null;
  finReal: string | null;
  duracionEfectivaSegundos: number | null;
}

/**
 * 404 si la Reserva nunca llegó a confirmarse (no hay Sesión programada) o si
 * la Reserva no existe — el llamador lo trata como "no hay nada que mostrar
 * todavía" (chequear `err.status === 404`), no como un error real.
 */
export function getSesionPorReserva(reservaId: string): Promise<SesionInfo> {
  return api.get(`/api/sesiones/por-reserva/${reservaId}`);
}

/* ---- M6 — Resumen automático de una sesión (backend: auditoría 2026-09-20) ---- */

export interface ResumenSesionInfo {
  disponible: boolean;
  resumenFinal: string | null;
}

/** `disponible: false` cubre tanto "todavía no hay resumen" como "no se va a
 *  generar" (sesión corta, sin proveedor, suspendido por seguridad, etc.) —
 *  el backend no distingue esos casos acá a propósito. */
export function getResumenSesion(sesionId: string): Promise<ResumenSesionInfo> {
  return api.get(`/api/sesiones/${sesionId}/resumen`);
}

/* ---- M7 — Calificación de una sesión ---- */

export interface CalificacionCreada {
  id: string;
  sesionId: string;
  direccion: string;
  estrellas: number;
  comentario: string | null;
  editableHasta: string;
  createdAt: string;
}

/** La dirección (a quién califica quién) la deriva el backend del rol del
 *  autor — nunca es un campo que el cliente pueda mandar. */
export function calificarSesion(
  sesionId: string,
  body: { estrellas: number; comentario?: string }
): Promise<CalificacionCreada> {
  return api.post(`/api/sesiones/${sesionId}/calificacion`, body);
}

/** Auditoría 2026-09-20: antes no había forma de recuperar el id de la propia
 *  calificación al volver a cargar la pantalla — sin esto, ni se podía
 *  mostrar lo ya calificado ni editarlo/borrarlo. null = todavía no calificó. */
export async function getMiCalificacion(sesionId: string): Promise<CalificacionCreada | null> {
  const res = await api.get<CalificacionCreada | undefined>(`/api/sesiones/${sesionId}/calificacion`);
  return res ?? null;
}

export function editarCalificacion(
  id: string,
  body: { estrellas: number; comentario?: string }
): Promise<CalificacionCreada> {
  return api.patch(`/api/calificaciones/${id}`, body);
}

export function eliminarCalificacion(id: string): Promise<void> {
  return api.delete(`/api/calificaciones/${id}`);
}

/* ---- M1 — "Editar cuenta" y "olvidé mi contraseña" (auditoría 2026-09-19) ---- */

export interface PerfilPropio {
  id: string;
  nombre: string;
  apellido: string;
  tipo: string;
  capacidadEstudiante: boolean;
  capacidadAdultoResponsable: boolean;
  email: string | null;
}

/** El JWT no lleva el email en el payload — hace falta este endpoint para
 *  mostrarlo en la pantalla de cuenta. */
export function getPerfilPropio(): Promise<PerfilPropio> {
  return api.get("/api/usuarios/me");
}

export function actualizarEmail(email: string): Promise<PerfilPropio> {
  return api.patch("/api/usuarios/me/email", { email });
}

export function cambiarPassword(passwordActual: string, passwordNueva: string): Promise<void> {
  return api.patch("/api/usuarios/me/password", { passwordActual, passwordNueva });
}

/** Auditoría 2026-09-20: el backend soporta activar/desactivar capacidades
 *  desde el alta (PATCH /api/usuarios/me/capacidades) pero no había ningún
 *  llamador en el frontend — un Adulto que se registró solo como Estudiante
 *  no tenía forma de convertirse en Adulto Responsable más adelante (y
 *  viceversa) sin soporte. */
export function actualizarCapacidades(
  capacidadEstudiante: boolean,
  capacidadAdultoResponsable: boolean
): Promise<PerfilPropio> {
  return api.patch("/api/usuarios/me/capacidades", { capacidadEstudiante, capacidadAdultoResponsable });
}

/** Responde 204 siempre, exista o no el DNI (no confirma ni niega su
 *  existencia — ver PasswordResetService en el backend). */
export function solicitarResetPassword(dni: string): Promise<void> {
  return api.post("/api/usuarios/recuperar-password", { dni });
}

export function resetearPassword(token: string, passwordNueva: string): Promise<void> {
  return api.post("/api/usuarios/resetear-password", { token, passwordNueva });
}

/* ---- M1 — Estado real de la credencial propia del Tutor ---- */

export interface CredencialPropia {
  id: string;
  tipoDocumento: TipoCredencial;
  estado: EstadoCredencial;
  numeroIntento: number;
  createdAt: string;
}

/** null = todavía no cargó ninguna credencial (204 del backend). */
export async function getMiCredencial(): Promise<CredencialPropia | null> {
  const res = await api.get<CredencialPropia | undefined>("/api/tutores/me/credencial");
  return res ?? null;
}

export function subirCredencial(tipo: TipoCredencial, archivo: File): Promise<CredencialPropia> {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify({ tipoDocumento: tipo })], { type: "application/json" }));
  form.append("archivo", archivo);
  return api.post("/api/tutores/credenciales", form);
}