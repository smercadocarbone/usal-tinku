/**
 * Cliente HTTP centralizado hacia tinku-backend.
 *
 * Regla: ningún componente hace `fetch` directo a la API — todo pasa por
 * acá, para que el manejo de JWT, errores y la URL base vivan en un solo
 * lugar (mismo espíritu que "una sola función de reembolso" en el backend:
 * un único punto de verdad por responsabilidad transversal).
 */

import { clearSession, TOKEN_KEY } from "./auth";
import { ESPERA_SUBIDA_CLIP_MS, REINTENTOS_SUBIDA_CLIP } from "./killswitch/config";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

import type {
  DatosVerificacionCola,
  DetalleDecisionCredencial,
  MotivoRechazoCredencial,
  TipoCredencialEspecialidades,
} from "./especialidades";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public detalles?: Record<string, unknown>
  ) {
    super(message);
  }

  /** R5: código estable del error (p. ej. `HORARIO_OCUPADO`); decidir por esto, no por el texto. */
  get codigo(): string | undefined {
    const c = this.detalles?.codigo;
    return typeof c === "string" ? c : undefined;
  }
}

type Cuerpo = Record<string, unknown> | FormData | string | undefined;

function headersConToken(): Headers {
  const token =
    typeof window !== "undefined" ? window.localStorage.getItem(TOKEN_KEY) : null;
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  return headers;
}

async function request<T>(
  path: string,
  options: { method: string; body?: Cuerpo }
): Promise<T> {
  return (await requestCompleta<T>(path, options)).datos;
}

/** Igual que {@link request}, pero devuelve también los headers de la respuesta (T-TES-11b). */
async function requestCompleta<T>(
  path: string,
  options: { method: string; body?: Cuerpo }
): Promise<{ datos: T; headers: Headers }> {
  const esMultipart =
    typeof FormData !== "undefined" && options.body instanceof FormData;

  const headers = headersConToken();
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

  if (res.status === 204) return { datos: undefined as T, headers: res.headers };
  return { datos: (await res.json()) as T, headers: res.headers };
}

/** Extrae un mensaje legible y el detalle estructurado del cuerpo de error
 * del backend (contrato M1: `{"error": "...", ...extra}`). */
async function leerError(res: Response): Promise<[string, Record<string, unknown>?]> {
  // Si no hay un mensaje pensado para el usuario se devuelve "" y cada pantalla
  // usa su propio texto de respaldo (mensajeDeError). Nunca se muestra el
  // statusText en inglés, el cuerpo por defecto de Spring ("Internal Server
  // Error") ni una página HTML de error de un proxy.
  let texto = "";
  try {
    texto = await res.text();
  } catch {
    return [""];
  }

  if (!texto) return [""];

  try {
    const json = JSON.parse(texto) as Record<string, unknown>;
    // Cuerpo por defecto de Spring Boot: {timestamp, status, error: "Not Found", path}.
    const esErrorPorDefecto = "timestamp" in json && "path" in json;
    const mensaje = esErrorPorDefecto
      ? ""
      : (typeof json.error === "string" && json.error) ||
        (typeof json.message === "string" && json.message) ||
        (typeof json.detail === "string" && json.detail);
    return [mensaje || "", json];
  } catch {
    return ["", { body: texto }];
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
  /** POST que además devuelve los headers de la respuesta (p. ej. `X-Busqueda-Id`, T-TES-11b). */
  postConHeaders: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => requestCompleta<T>(path, { method: "POST", body })),
  put: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => request<T>(path, { method: "PUT", body })),
  patch: <T>(path: string, body?: Cuerpo) =>
    manageSesion(() => request<T>(path, { method: "PATCH", body })),
  delete: <T>(path: string) =>
    manageSesion(() => request<T>(path, { method: "DELETE" })),
  /** Bytes (archivo), no JSON: CSV de cobros, adjuntos, videos. */
  blob: (path: string) =>
    manageSesion(async () => {
      const res = await fetch(`${API_BASE_URL}${path}`, { headers: headersConToken() });
      if (!res.ok) {
        const [mensaje, detalles] = await leerError(res);
        throw new ApiError(res.status, mensaje, detalles);
      }
      return res.blob();
    }),
};

/** Descarga un Blob como archivo con ese nombre. */
export function guardarArchivo(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}

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
  /** FR-MATCH-011: nadie da exactamente lo buscado y es una recomendación del área. */
  porArea?: boolean;
  /** "Matemática · Secundario" cuando {@link porArea}. */
  area?: string | null;
  /** FR-MATCH-013: tarifa vigente (null si no la configuró). */
  precioHora?: number | null;
  /** FR-MATCH-013: primer bloque libre de los próximos 14 días (null si no tiene). */
  proximoHorario?: string | null;
}

export function getCatalogos(filtros?: FiltrosCatalogos): Promise<NivelCatalogo[]> {
  const params = new URLSearchParams();
  if (filtros?.nivel) params.set("nivel", filtros.nivel);
  if (filtros?.curso) params.set("curso", filtros.curso);
  if (filtros?.materia) params.set("materia", filtros.materia);
  const qs = params.toString();
  // AUD-026: sin fallback a un catálogo local. Con el backend caído, el usuario ve el
  // error, no un catálogo falso que parece real (GET /api/catalogos existe desde M2-F).
  return api.get<NivelCatalogo[]>(`/api/catalogos${qs ? `?${qs}` : ""}`);
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
  /** "primario" | "secundario" | "universitario": acota junto con la materia. */
  filtroNivel?: string;
  /** FR-MATCH-014: precio máximo por hora. */
  precioMaxHora?: number;
}

export function buscarTutores(body: CuerpoBusqueda): Promise<ResultadoBusqueda[]> {
  return buscarTutoresConId(body).then((r) => r.resultados);
}

/**
 * Búsqueda + id de la búsqueda registrada para la evaluación (T-TES-11b §4.1). `busquedaId` es
 * null salvo que el backend tenga `tinku.evaluacion.habilitada=true`.
 */
export async function buscarTutoresConId(
  body: CuerpoBusqueda
): Promise<{ resultados: ResultadoBusqueda[]; busquedaId: string | null }> {
  const { datos, headers } = await api.postConHeaders<ResultadoBusqueda[]>("/api/busquedas", {
    texto_busqueda: body.textoBusqueda || undefined,
    nombre: body.nombre || undefined,
    filtro_materia: body.filtroMateria || undefined,
    filtro_nivel: body.filtroNivel || undefined,
    precio_max_hora: body.precioMaxHora || undefined,
  });
  return { resultados: datos, busquedaId: headers.get("X-Busqueda-Id") };
}

/* ---- M2 — Búsquedas guardadas (US-6, FR-MATCH-008; auditoría 2026-09-20) ----
 * El backend tenía guardar/listar/re-ejecutar completos, sin ningún llamador
 * en el frontend. */

export interface BusquedaGuardada {
  id: string;
  textoBusqueda: string;
  createdAt: string;
  /** R-E E-2: filtros con que se guardó (nombres del API). */
  filtros?: { texto_busqueda?: string; nombre?: string; filtro_materia?: string; filtro_nivel?: string; precio_max_hora?: number };
}

export function guardarBusqueda(body: CuerpoBusqueda): Promise<BusquedaGuardada> {
  return api.post("/api/busquedas/guardadas", {
    texto_busqueda: body.textoBusqueda || undefined,
    nombre: body.nombre || undefined,
    filtro_materia: body.filtroMateria || undefined,
    filtro_nivel: body.filtroNivel || undefined,
    precio_max_hora: body.precioMaxHora || undefined,
  });
}

/** R-E E-2: borra una búsqueda guardada propia. */
export function borrarBusquedaGuardada(id: string): Promise<void> {
  return api.delete(`/api/busquedas/guardadas/${id}`);
}

/** R-E E-3 (FR-REP-001): comentarios públicos del perfil del Tutor, sin datos de quién los escribió. */
export interface ComentarioPublico {
  estrellas: number;
  comentario: string;
  fecha: string;
}

export function getComentariosTutor(tutorId: string, limite = 5): Promise<ComentarioPublico[]> {
  return api.get(`/api/tutores/${tutorId}/calificaciones?limite=${limite}`);
}

export function getBusquedasGuardadas(): Promise<BusquedaGuardada[]> {
  return api.get("/api/busquedas/guardadas");
}

/** Re-ejecuta contra el índice vigente — resultados frescos, no congelados. */
export function ejecutarBusquedaGuardada(id: string): Promise<ResultadoBusqueda[]> {
  return api.post(`/api/busquedas/guardadas/${id}/ejecutar`);
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
  /** FASE2-07: false en producción. Ausente en respuestas viejas = permitido. */
  bypassPermitido?: boolean;
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
export type TipoCredencial = "TITULO" | "CERTIFICADO_ANALITICO" | "MATRICULA" | TipoCredencialEspecialidades;
export type DecisionCredencial = "APROBAR" | "RECHAZAR";

export interface CredencialCola extends DatosVerificacionCola {
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

/**
 * Archivo de la Credencial para revisarlo antes de decidir (AUD-007). Devuelve
 * bytes, no JSON: el backend fija el Content-Type por el contenido real del
 * archivo, nunca por lo que declaró el Tutor.
 */
export async function getArchivoCredencial(id: string): Promise<Blob> {
  const res = await fetch(
    `${API_BASE_URL}/api/admin/moderacion/credenciales/${id}/archivo`,
    { headers: headersConToken() }
  );
  if (!res.ok) {
    const [mensaje, detalles] = await leerError(res);
    throw new ApiError(res.status, mensaje, detalles);
  }
  return res.blob();
}

export function resolverCredencial(
  id: string,
  decision: DecisionCredencial,
  /** ADR-M1-06 (solo con el flag encendido): método y cobertura, o motivo del rechazo. */
  extra?: DetalleDecisionCredencial
): Promise<CredencialCola> {
  return api.post(`/api/admin/moderacion/credenciales/${id}/resolver`, { decision, ...extra });
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
  tieneClip: boolean;
  createdAt: string;
}

/** Clip de evidencia del kill-switch (AUD-021): lo sirve Tinku, nunca un enlace externo. */
export async function getClipAlerta(id: string): Promise<Blob> {
  const res = await fetch(
    `${API_BASE_URL}/api/admin/moderacion/alertas/${id}/clip`,
    { headers: headersConToken() }
  );
  if (!res.ok) {
    const [mensaje, detalles] = await leerError(res);
    throw new ApiError(res.status, mensaje, detalles);
  }
  return res.blob();
}

export interface ResolverAlertaBody {
  decision: DecisionAlerta;
  tipoSancion?: TipoSancion;
  diasSuspension?: number;
  [key: string]: unknown;
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
  /** R-F F-3: vista del admin. Nunca trae al denunciante (FR-SEC-006). */
  evidenciaUrl?: string | null;
  denunciadoNombre?: string | null;
  denunciadoApellido?: string | null;
  denunciadoTipo?: string | null;
  descargoHistorial?: string | null;
  /** R-F F-1: quien consulta es parte del caso; lo resuelve otro admin. */
  conflicto?: boolean;
}

export interface ResolverDenunciaBody {
  resolucion: ResolucionDenuncia;
  tipoSancion?: TipoSancion;
  diasSuspension?: number;
  [key: string]: unknown;
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

/** R-F F-9: precios vigentes, una fila por provincia. */
export function getPreciosRegionales(): Promise<PrecioRegional[]> {
  return api.get("/api/admin/financiero/precios-regionales");
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
  /** R-B B-10: rol de admin activo; ausente si no es admin. */
  rolAdmin?: RolAdmin | null;
}

/** El JWT no lleva el email en el payload — hace falta este endpoint para
 *  mostrarlo en la pantalla de cuenta. */
export function getPerfilPropio(): Promise<PerfilPropio> {
  return api.get("/api/usuarios/me");
}

export function actualizarEmail(email: string): Promise<PerfilPropio> {
  return api.patch("/api/usuarios/me/email", { email });
}

/** "Cerrar sesión": invalida el token en el servidor. Si falla (sin red, token ya vencido),
 *  no se insiste: la sesión local se borra igual. */
export async function cerrarSesionEnServidor(): Promise<void> {
  try {
    await request<void>("/api/usuarios/logout", { method: "POST" });
  } catch {
    /* el token igual se descarta en el navegador */
  }
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
  /** B12: true si el Tutor tiene ALGUNA aprobada aunque la última siga en revisión. */
  tieneAprobada: boolean;
  /** ADR-M1-06: motivo del rechazo que eligió el Admin (solo con el flag encendido). */
  motivoRechazo?: MotivoRechazoCredencial;
  motivoRechazoTexto?: string;
  detalleRechazo?: string;
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
/* ---- T02/T03 — CAP (certificado de antecedentes), solo para dar clases a menores ---- */

export type EstadoCap = "PENDIENTE" | "APROBADO" | "RECHAZADO" | "EN_REVISION_LEGAL" | "VENCIDO";

export interface CapPropio {
  id: string;
  estado: EstadoCap;
  tieneAntecedentes: boolean;
  venceAt: string;
  numeroIntento: number;
}

export function subirCap(archivo: File, fechaEmision: string): Promise<CapPropio> {
  const form = new FormData();
  form.append("datos", new Blob([JSON.stringify({ fechaEmision })], { type: "application/json" }));
  form.append("archivo", archivo);
  return api.post("/api/tutores/antecedentes-penales", form);
}

export interface CapCola {
  id: string;
  tutorId: string;
  tutorNombre: string;
  tutorApellido: string;
  estado: EstadoCap;
  fechaEmision: string;
  venceAt: string;
  categoriaAntecedente: string | null;
  numeroIntento: number;
  createdAt: string;
  /** ADR-M1-10: verificación de la firma digital con el validador oficial. */
  firmaEstado: EstadoFirmaCap;
  firmaDetalle: string | null;
  firmaFirmante: string | null;
  firmaSha256: string | null;
  firmaVerificadaAt: string | null;
}

export type EstadoFirmaCap =
  | "NO_VERIFICADA"
  | "PENDIENTE"
  | "EN_PROCESO"
  | "VALIDA"
  | "INVALIDA"
  | "FIRMANTE_NO_RECONOCIDO"
  | "ERROR";

/** BR-CAP-01 (los tres primeros: rechazo automático) y BR-CAP-02 (OTRO: revisión legal). */
export type CategoriaAntecedenteCap = "INTEGRIDAD_SEXUAL" | "VINCULADO_A_MENORES" | "HOMICIDIO" | "OTRO";
export type AccionRevisionCap = "APROBAR" | "RECHAZAR" | "EN_REVISION_LEGAL";

export function getColaCap(): Promise<CapCola[]> {
  return api.get("/api/admin/moderacion/antecedentes-penales");
}

export function revisarCap(id: string, accion: AccionRevisionCap, categoria: CategoriaAntecedenteCap | null): Promise<CapPropio> {
  return api.patch(`/api/admin/moderacion/antecedentes-penales/${id}`, { accion, categoria });
}

/** ADR-M1-10: vuelve a poner el CAP en la cola del validador de firmas. */
export function verificarFirmaCap(id: string): Promise<CapCola> {
  return api.post(`/api/admin/moderacion/antecedentes-penales/${id}/verificar-firma`, {});
}

export async function getArchivoCap(id: string): Promise<Blob> {
  const res = await fetch(`${API_BASE_URL}/api/admin/moderacion/antecedentes-penales/${id}/archivo`, {
    headers: headersConToken(),
  });
  if (!res.ok) {
    const [mensaje, detalles] = await leerError(res);
    throw new ApiError(res.status, mensaje, detalles);
  }
  return res.blob();
}

/* ---- UX-06 / U1 — perfil público y estado del Tutor ---- */

export interface EstadoPerfilTutor {
  /** Flag real del matching (`activo_para_matching`). */
  visibleEnBusquedas: boolean;
  ultimaCredencial: EstadoCredencial | null;
  tieneCredencialAprobada: boolean;
  tieneMaterias: boolean;
  tienePrecio: boolean;
  tieneBio: boolean;
  tieneFoto: boolean;
  /** Último CAP cargado (T03); `null` si nunca cargó. Solo hace falta para dar clases a menores. */
  cap: CapPropio | null;
  habilitadoParaMenores: boolean;
  /** Preferencia del Tutor de dar clases a menores (habilitado = esto Y el CAP vigente). */
  aceptaMenores?: boolean;
}

export function actualizarAceptaMenores(aceptaMenores: boolean): Promise<{ aceptaMenores: boolean; clasesCanceladas: number }> {
  return api.put("/api/tutores/me/menores", { aceptaMenores });
}

export function getEstadoPerfilTutor(): Promise<EstadoPerfilTutor> {
  return api.get("/api/tutores/me/estado-perfil");
}

/** R-B B-9: `avisos` dice si se taparon datos de contacto de la presentación. */
export function actualizarBioTutor(bio: string): Promise<{ bio: string | null; avisos?: string[] }> {
  return api.put("/api/tutores/me/perfil", { bio });
}

export function subirFotoTutor(archivo: File): Promise<unknown> {
  const form = new FormData();
  form.append("archivo", archivo);
  return api.put("/api/tutores/me/foto", form);
}

export function borrarFotoTutor(): Promise<unknown> {
  return api.delete("/api/tutores/me/foto");
}

/** U1 — moderación: el Admin de Moderación quita la presentación o la foto de un Tutor. */
export function moderarPerfilTutor(tutorId: string, que: "bio" | "foto"): Promise<void> {
  return api.delete(`/api/admin/moderacion/tutores/${tutorId}/${que}`);
}

// ---------------------------------------------------------------- T08/T09: resumen automático

/** T09: si el tutor ofrece el resumen automático y a qué precio (ADR-M3-04). */
export interface AdicionalResumen {
  disponible: boolean;
  precio: number;
}

/** ADR-M5-06: el precio crece con la duración de la clase, así que se pide para la duración elegida. */
export function getAdicionalResumen(tutorId: string, duracionMinutos: number): Promise<AdicionalResumen> {
  return api.get<AdicionalResumen>(
    `/api/reservas/adicional-resumen?tutorId=${encodeURIComponent(tutorId)}&duracionMinutos=${duracionMinutos}`
  );
}

/**
 * T-TES-10: si las clases con menores están habilitadas (endpoint público). Fail-closed: ante
 * cualquier error o respuesta inesperada, `false`.
 */
export async function getClasesConMenoresHabilitadas(): Promise<boolean> {
  try {
    const r = await api.get<{ habilitadas?: unknown }>("/api/reservas/clases-con-menores");
    return r?.habilitadas === true;
  } catch {
    return false;
  }
}

export const CLAUSULA_GRABACION = "GRABACION_AUDIO_RESUMEN";
/** ADR-M3-05: los Términos (incluyen el consentimiento de la grabación del resumen). */
export const CLAUSULA_TERMINOS = "TERMINOS_Y_CONDICIONES";

export interface EstadoClausula {
  clausula: string;
  versionVigente: string;
  aceptada: boolean;
}

export function getClausula(clausula: string): Promise<EstadoClausula> {
  return api.get<EstadoClausula>(`/api/usuarios/me/clausulas/${clausula}`);
}

export function aceptarClausula(clausula: string): Promise<EstadoClausula> {
  return api.post<EstadoClausula>(`/api/usuarios/me/clausulas/${clausula}`);
}

/** ADR-M3-04: el navegador del tutor sube el audio de la clase (cuerpo crudo, no multipart). */
export async function subirAudioResumen(sesionId: string, audio: Blob): Promise<void> {
  const headers = headersConToken();
  headers.set("Content-Type", audio.type.startsWith("audio/ogg") ? "audio/ogg" : "audio/webm");
  const res = await fetch(`${API_BASE_URL}/api/sesiones/${sesionId}/audio`, {
    method: "POST",
    headers,
    body: audio,
    keepalive: false,
  });
  if (!res.ok) {
    const [mensaje, detalles] = await leerError(res);
    throw new ApiError(res.status, mensaje, detalles);
  }
}

// ---------------------------------------------------------------- M3C-04: kill-switch del cliente

export interface EstadoKillswitch {
  hayMenor: boolean;
  confirmacionPendiente: boolean;
  detectadoId: string | null;
  venceAt: string | null;
  /** R-C C-1: la sesión quedó cerrada en el backend (finalizada o interrumpida). */
  sesionCerrada?: boolean;
  /** R-C C-1: hubo un corte por seguridad. Nada más de la alerta (BR-KS-03). */
  cortePorSeguridad?: boolean;
}
export type EventoClasificador = "NO_CARGA" | "CAIDA" | "REACTIVADO" | "CORTE_SIN_REACTIVAR";
export interface SesionKillswitch {
  id: string;
  estado: string;
}

/** ADR-M3-07: umbral y pregunta pendiente de la rama adultos (la retoma quien recarga la página). */
export function getEstadoKillswitch(sesionId: string): Promise<EstadoKillswitch> {
  return api.get(`/api/sesiones/${sesionId}/killswitch`);
}

/** US-6/US-7: la rama (menor/adultos) la decide el backend; el cliente solo dice quién fue detectado. */
export function dispararKillswitch(sesionId: string, detectadoId: string): Promise<SesionKillswitch> {
  return api.post(`/api/sesiones/${sesionId}/killswitch`, { detectadoId });
}

/** US-7: "¿viste algo?" del OTRO participante en la rama adultos. */
export function confirmarKillswitch(sesionId: string, vio: boolean): Promise<SesionKillswitch> {
  return api.post(`/api/sesiones/${sesionId}/killswitch/confirmacion`, { vio });
}

/** ADR-M3-07 §6: avisa el estado del clasificador del navegador (solo log en el backend). */
export function reportarClasificador(sesionId: string, evento: EventoClasificador): Promise<void> {
  return api.post(`/api/sesiones/${sesionId}/clasificador`, { evento });
}

/**
 * Sube el clip de evidencia (multipart: parte "clip" + `duracionSegundos` en la query). Hasta
 * REINTENTOS_SUBIDA_CLIP intentos, con ESPERA_SUBIDA_CLIP_MS entre ellos, solo ante error de red o
 * 5xx; un 4xx no se reintenta. Nunca manda la rama. El clip vive solo en memoria: si fallan todos, se pierde.
 */
export async function subirEvidencia(sesionId: string, clip: Blob, duracionSegundos: number): Promise<void> {
  const nombre = clip.type.includes("mp4") ? "evidencia.mp4" : "evidencia.webm";
  let ultimo: unknown;
  for (let intento = 1; intento <= REINTENTOS_SUBIDA_CLIP; intento++) {
    const cuerpo = new FormData();
    cuerpo.append("clip", clip, nombre);
    try {
      await api.post(`/api/sesiones/${sesionId}/evidencia?duracionSegundos=${duracionSegundos}`, cuerpo);
      return;
    } catch (e) {
      ultimo = e;
      if (e instanceof ApiError && e.status < 500) {
        console.error(`Evidencia rechazada (${e.status})`);
        throw e;
      }
      if (intento < REINTENTOS_SUBIDA_CLIP) await new Promise((r) => setTimeout(r, ESPERA_SUBIDA_CLIP_MS));
    }
  }
  throw ultimo;
}

/** R5: Tutores autorizados para un menor (solo su Adulto Responsable). */
export interface AutorizacionMenor {
  tutorId: string;
  tutorNombre: string;
  tutorApellido: string;
  tieneFoto: boolean;
  noConfiable: boolean;
  autorizadoAt: string;
}

export function getAutorizaciones(menorId: string): Promise<AutorizacionMenor[]> {
  return api.get(`/api/autorizaciones?menorId=${encodeURIComponent(menorId)}`);
}

export function marcarNoConfiable(tutorId: string, noConfiable: boolean): Promise<void> {
  return api.patch("/api/autorizaciones/no-confiable", { tutorId, noConfiable });
}

/** R4: cola de reembolsos del adicional de resumen (BR-PAG-11). */
export interface ReembolsoAdicional {
  id: string;
  reservaId: string;
  monto: number;
  estado: "PENDIENTE" | "FALLIDO" | "HECHO" | "RESUELTO_MANUAL";
  intentos: number;
  ultimoError: string | null;
  nota: string | null;
  reembolsadoAt: string | null;
}

export function getColaReembolsosAdicional(): Promise<ReembolsoAdicional[]> {
  return api.get("/api/admin/financiero/reembolsos-adicional");
}

export function reintentarReembolsoAdicional(id: string): Promise<ReembolsoAdicional> {
  return api.post(`/api/admin/financiero/reembolsos-adicional/${id}/reintentar`);
}

export function resolverReembolsoAdicional(id: string, nota: string): Promise<ReembolsoAdicional> {
  return api.post(`/api/admin/financiero/reembolsos-adicional/${id}/resuelto-manual`, { nota });
}

/** Asistente de "Mis materias": temas del catálogo parecidos a lo que cuenta el Tutor. */
export interface TemaSugerido {
  id: string;
  nombre: string;
  materia: string;
  curso: string;
  nivel: string;
  score: number;
}

export function sugerirTemas(texto: string, nivel?: string | null): Promise<TemaSugerido[]> {
  return api.post<TemaSugerido[]>("/api/tutores/me/temas/sugerencias", { texto, ...(nivel ? { nivel } : {}) });
}

/* ---- M2/M8 — Temas sugeridos (FR-MATCH-012, FR-ADM-009, ADR-M2-04) ----
 * Lo que la gente busca y el catálogo no cubre, agregado: solo los temas pedidos varias
 * veces, sin datos de quién los buscó. */
export interface TemaPedido {
  id: string;
  texto: string;
  veces: number;
  nivel: string | null;
  materia: string | null;
  ultimaVez: string;
}

export function getTemasSugeridos(): Promise<TemaPedido[]> {
  return api.get("/api/admin/temas-sugeridos");
}

export function resolverTemaPedido(id: string): Promise<void> {
  return api.delete(`/api/admin/temas-sugeridos/${id}`);
}

/* ---- M4 — Agenda del Tutor (R-D, FR-RES-025) ---- */
export interface FranjaQuitada {
  id: string;
  reservasFuturasEnLaFranja: number;
}

/** Baja lógica de una franja propia: las clases ya reservadas se mantienen. */
export function quitarFranja(id: string): Promise<FranjaQuitada> {
  return api.delete(`/api/tutores/franjas/${id}`);
}

export interface RangoLibre {
  desde: string;
  hasta: string;
}

export interface DiaDisponible {
  fecha: string;
  rangos: RangoLibre[];
}

/** Rangos libres reales del Tutor (sin lo ya reservado ni lo que ya pasó) para el perfil público. */
export function getDisponibilidadTutor(tutorId: string, dias = 7): Promise<DiaDisponible[]> {
  return api.get(`/api/tutores/${tutorId}/disponibilidad?dias=${dias}`);
}
