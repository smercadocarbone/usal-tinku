"use client";

import { resolverCierre, type CierreAula } from "@/lib/killswitch/cierre";
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type ReactNode,
  type RefObject,
} from "react";
import { useRouter, useParams } from "next/navigation";
import {
  ConnectionQuality,
  createLocalAudioTrack,
  createLocalVideoTrack,
  LocalAudioTrack,
  LocalVideoTrack,
  Room,
  RoomEvent,
  ScreenSharePresets,
  Track,
  VideoQuality,
} from "livekit-client";
import {
  LogOut,
  Mic,
  MicOff,
  MoreVertical,
  PenLine,
  ScreenShare,
  ScreenShareOff,
  Settings,
  UserPlus,
  Video,
  VideoOff,
} from "lucide-react";
import {
  api,
  ApiError,
  confirmarKillswitch,
  dispararKillswitch,
  getEstadoKillswitch,
  reportarClasificador,
  subirAudioResumen,
  subirEvidencia,
} from "@/lib/api";
import { GrabadorAudioResumen } from "@/lib/grabadorAudioResumen";
import Logo from "@/components/Logo";
import AvisoProteccion, { type MotivoAvisoProteccion } from "@/components/aula/AvisoProteccion";
import DialogoRamaAdultos from "@/components/aula/DialogoRamaAdultos";
import DialogoAdmitir from "@/components/aula/DialogoAdmitir";
import DialogoConfiguracion, { type Dispositivo } from "@/components/aula/DialogoConfiguracion";
import { useSesion } from "@/lib/useSesion";
import { BufferEvidencia } from "@/lib/killswitch/bufferEvidencia";
import { ClasificadorKillswitch } from "@/lib/killswitch/clasificador";
import { TOPIC_KILLSWITCH } from "@/lib/killswitch/config";
import { crearOrquestador, decodificarAviso } from "@/lib/killswitch/orquestador";
import Pizarra from "@/components/aula/Pizarra";
import { aplicar, codificar, decodificar, mensajesDeSync, type MensajePizarra, type Trazo } from "@/lib/pizarra";
import { filtrarMensaje } from "@/lib/chat/filtro";
import { Boton, Modal, ModalConfirmacion } from "@/components/ui";

interface TokenResponse {
  token: string;
  livekitUrl: string;
  livekitRoomId: string;
  /** ADR-M3-04: solo true para el Tutor de una clase con el adicional de resumen. */
  grabarAudioResumen?: boolean;
  /** FR-AULA-012 (ADR-M3-06): ningún participante es Menor. Sin esto, no hay pizarra. */
  pizarraHabilitada?: boolean;
  /** ADR-M3-07: hay un Menor en la clase (umbral del clasificador y fail-closed). */
  hayMenor?: boolean;
  /** El Tutor ya dejó pasar a este alumno: al volver a entrar se saltea la sala de espera. */
  admitido?: boolean;
  /** ADR-M3-07 §4bis: clase de Medicina o Anatomía entre adultos (modo salud del clasificador). */
  contenidoAnatomico?: boolean;
}

type Estado =
  | "previa"
  | "conectando"
  | "sala_espera"
  | "esperando"
  | "conectado"
  | "reconectando"
  | "sala_no_disponible"
  | "error"
  | "finalizada";

/** Fuentes de video posibles en un momento dado: nunca son más de 4 (es 1:1) — la
 * cámara de cada participante y, si están activas, sus pantallas compartidas. */
type TileId = "camara-local" | "camara-remota" | "pantalla-local" | "pantalla-remota";

const MENSAJES_ESTADO: Record<Estado, string> = {
  previa: "Preparando cámara y micrófono",
  conectando: "Conectando…",
  sala_espera: "En la sala de espera",
  esperando: "Esperando al otro participante",
  conectado: "En la sala",
  reconectando: "Reconectando…",
  sala_no_disponible: "Sala no disponible",
  error: "Error de conexión",
  finalizada: "Sesión finalizada",
};

/**
 * La etiqueta sale del claim `name` del token de LiveKit (solo el nombre de
 * pila). El `identity` es un UUID opaco y NO se renderiza nunca (AUD-003).
 */
function etiquetaParticipante(nombre: string, esLocal: boolean): string {
  if (esLocal) return "Vos";
  return nombre || "Participante";
}

function mensajeErrorDispositivo(err: unknown): string {
  if (err instanceof DOMException) {
    if (err.name === "NotAllowedError") {
      return "El navegador bloqueó el acceso. Activá los permisos de cámara y micrófono para este sitio y volvé a intentar.";
    }
    if (err.name === "NotFoundError") {
      return "No encontramos una cámara o un micrófono conectados a este dispositivo.";
    }
    if (err.name === "NotReadableError") {
      return "La cámara o el micrófono ya están siendo usados por otra aplicación.";
    }
  }
  return "No pudimos acceder a la cámara o al micrófono.";
}

const COLOR_CALIDAD: Record<ConnectionQuality, string> = {
  [ConnectionQuality.Excellent]: "bg-teal-500",
  [ConnectionQuality.Good]: "bg-amber-400",
  [ConnectionQuality.Poor]: "bg-red-500",
  [ConnectionQuality.Lost]: "bg-red-700",
  [ConnectionQuality.Unknown]: "bg-gray-500",
};

const TEXTO_CALIDAD: Record<ConnectionQuality, string> = {
  [ConnectionQuality.Excellent]: "Conexión excelente",
  [ConnectionQuality.Good]: "Conexión buena",
  [ConnectionQuality.Poor]: "Conexión inestable",
  [ConnectionQuality.Lost]: "Conexión perdida",
  [ConnectionQuality.Unknown]: "Calidad de conexión desconocida",
};

/**
 * FR-AULA-002 / BR-CONN-01: cuántas lecturas seguidas de `ConnectionQualityChanged`
 * en el mismo sentido hacen falta antes de actuar. LiveKit emite este evento
 * cada pocos segundos con la métrica ya suavizada de su lado — igual conviene
 * no reaccionar a la primera lectura mala (podría ser un pico de medio
 * segundo) ni a la primera buena (podría ser un respiro momentáneo antes de
 * volver a caer). 3 lecturas seguidas es una decisión simple y conservadora,
 * no viene de una medición: prioriza no parpadear la cámara antes que
 * reaccionar rápido.
 */
const UMBRAL_LECTURAS = 3;

interface MensajeTexto {
  propio: boolean;
  texto: string;
}

function BotonControl({
  activo,
  onClick,
  etiquetaOn,
  etiquetaOff,
  IconoOn,
  IconoOff,
}: {
  activo: boolean;
  onClick: () => void;
  etiquetaOn: string;
  etiquetaOff: string;
  IconoOn: ComponentType<{ className?: string }>;
  IconoOff: ComponentType<{ className?: string }>;
}) {
  const Icono = activo ? IconoOn : IconoOff;
  return (
    <button
      type="button"
      className={`flex h-12 w-12 cursor-pointer items-center justify-center rounded-full border transition-colors ${
        activo
          ? "border-gray-700 bg-gray-900 text-gray-50 hover:border-gray-500"
          : "border-red-800 bg-red-900/80 text-red-50 hover:border-red-600"
      }`}
      onClick={onClick}
      aria-pressed={activo}
      aria-label={activo ? etiquetaOff : etiquetaOn}
      title={activo ? etiquetaOff : etiquetaOn}
    >
      <Icono className="h-5 w-5" aria-hidden />
    </button>
  );
}

/** Pizarra (ADR-M3-06): canal de datos confiable con `topic`, separado del modo texto. */
function publicarPizarra(room: Room, m: MensajePizarra) {
  void room.localParticipant.publishData(codificar(m), { reliable: true, topic: "pizarra" });
}

/**
 * Sala de espera: el alumno entra a la sala de LiveKit sin publicar ni suscribirse a nada, y
 * avisa que espera. El Tutor lo deja entrar con un mensaje por este canal. El alumno sí cuenta
 * como presente para el no-show (el webhook de LiveKit ve su ingreso).
 */
const TOPIC_SALA = "sala";
type MensajeSala = { op: "esperando" } | { op: "adentro" } | { op: "quien" } | { op: "admitir" };

function publicarSala(room: Room, m: MensajeSala, destino?: string) {
  void room.localParticipant
    .publishData(new TextEncoder().encode(JSON.stringify(m)), {
      reliable: true,
      topic: TOPIC_SALA,
      destinationIdentities: destino ? [destino] : undefined,
    })
    .catch(() => {});
}

function decodificarSala(payload: Uint8Array): MensajeSala | null {
  try {
    const m = JSON.parse(new TextDecoder().decode(payload)) as { op?: unknown };
    return m.op === "esperando" || m.op === "adentro" || m.op === "quien" || m.op === "admitir"
      ? (m as MensajeSala)
      : null;
  } catch {
    return null;
  }
}

interface OpcionMenuLlamada {
  id: string;
  label: string;
  Icono: ComponentType<{ className?: string }>;
  onClick: () => void;
}

/** Botón "⋮" con menú desplegable hacia arriba (la barra de controles está pegada
 * abajo de la pantalla). Recibe la lista de opciones como datos, no como JSX
 * hardcodeado, para que sumar una opción nueva más adelante sea agregar un
 * elemento al array, no reescribir el menú. */
function MenuOpcionesLlamada({ opciones }: { opciones: OpcionMenuLlamada[] }) {
  const [abierto, setAbierto] = useState(false);
  const contenedorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    function alClickFuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) {
        setAbierto(false);
      }
    }
    document.addEventListener("mousedown", alClickFuera);
    return () => document.removeEventListener("mousedown", alClickFuera);
  }, [abierto]);

  return (
    <div className="relative" ref={contenedorRef}>
      <button
        type="button"
        className="flex h-12 w-12 cursor-pointer items-center justify-center rounded-full border border-gray-700 bg-gray-900 text-gray-50 hover:border-gray-500"
        onClick={() => setAbierto((v) => !v)}
        aria-label="Más opciones"
        aria-haspopup="menu"
        aria-expanded={abierto}
      >
        <MoreVertical className="h-5 w-5" aria-hidden />
      </button>
      {abierto && (
        <div
          role="menu"
          className="absolute bottom-full right-0 mb-2 w-56 overflow-hidden rounded-lg border border-gray-700 bg-gray-900 py-1 shadow-lg"
        >
          {opciones.map((o) => (
            <button
              key={o.id}
              type="button"
              role="menuitem"
              className="flex w-full cursor-pointer items-center gap-2.5 px-3.5 py-2.5 text-left text-sm text-gray-100 hover:bg-gray-800"
              onClick={() => {
                o.onClick();
                setAbierto(false);
              }}
            >
              <o.Icono className="h-4 w-4 shrink-0" aria-hidden />
              {o.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export default function AulaPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const sesionId = params.id;
  // Quien da la clase entra directo; el resto pasa por la sala de espera.
  const esTutor = useSesion()?.payload.tipo === "TUTOR";
  const esTutorRef = useRef(esTutor);
  useEffect(() => {
    esTutorRef.current = esTutor;
  }, [esTutor]);

  const roomRef = useRef<Room | null>(null);
  const roomConectadoRef = useRef(false);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null);
  /** Como los videos: el <audio> se vuelve a crear al pasar de la sala de espera a la clase. */
  const remoteAudioTrackRef = useRef<Track | null>(null);
  const localScreenVideoRef = useRef<HTMLVideoElement>(null);
  const remoteScreenVideoRef = useRef<HTMLVideoElement>(null);
  // La pista de cada tile. El <video> de un tile se crea DESPUÉS del evento que trae la pista
  // (la pantalla compartida recién existe cuando llega) y se vuelve a crear al cambiar cuál se ve
  // en grande: por eso se conecta cuando el elemento aparece (ref de abajo), no en el evento.
  const pistasRef = useRef<Partial<Record<TileId, Track>>>({});
  const previaVideoTrackRef = useRef<LocalVideoTrack | null>(null);
  const previaAudioTrackRef = useRef<LocalAudioTrack | null>(null);
  // ADR-M3-04: grabación de solo audio para el resumen (solo el Tutor, solo si el backend lo indica).
  const grabadorRef = useRef<GrabadorAudioResumen | null>(null);
  const [grabandoResumen, setGrabandoResumen] = useState(false);

  // ADR-M3-07: kill-switch del cliente. Ante la duda, hay un menor (AGENTS §3).
  const [hayMenor, setHayMenor] = useState<boolean | null>(null);
  const hayMenorRef = useRef(true);
  // ADR-M3-07 §4bis: modo salud. Ante la duda, no (queda el umbral de siempre).
  const [contenidoAnatomico, setContenidoAnatomico] = useState(false);
  const contenidoAnatomicoRef = useRef(false);
  const [avisoSaludVisto, setAvisoSaludVisto] = useState(false);
  const [proteccion, setProteccion] = useState<"cargando" | "lista" | "no-disponible">("cargando");
  const proteccionRef = useRef<"cargando" | "lista" | "no-disponible">("cargando");
  const clasificadorRef = useRef<ClasificadorKillswitch | null>(null);
  const buffersRef = useRef<Map<string, BufferEvidencia>>(new Map());
  const orquestadorRef = useRef<ReturnType<typeof crearOrquestador> | null>(null);
  /** Video remoto pendiente de observar mientras el clasificador todavía carga. */
  const remotosRef = useRef<Map<string, { duenoId: string; pista: MediaStreamTrack }>>(new Map());
  /** trackSid de cada tile remoto (el kill-switch habla de tracks; los tiles, de TileId). */
  const sidPorTileRef = useRef<Partial<Record<TileId, string>>>({});
  const ocultosRef = useRef<Set<string>>(new Set()); // ocultos por detección
  const todoOcultoRef = useRef(false); // ocultos por falla/demora del clasificador
  const [, setVersionOcultos] = useState(0);
  const [pregunta, setPregunta] = useState<{ trackId: string; detectadoId: string; venceEn: number } | null>(null);
  const [cierreKillswitch, setCierreKillswitch] = useState<MotivoAvisoProteccion | null>(null);
  const [avisoKillswitch, setAvisoKillswitch] = useState<string | null>(null);
  const temporizadorAvisoRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  const [estado, setEstado] = useState<Estado>("previa");
  const [confirmarFin, setConfirmarFin] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [finalizando, setFinalizando] = useState(false);
  const [remoteActivo, setRemoteActivo] = useState(false);
  const [remoteNombre, setRemoteNombre] = useState("");
  const [camActiva, setCamActiva] = useState(true);
  const [micActiva, setMicActiva] = useState(true);
  const [calidad, setCalidad] = useState<ConnectionQuality>(ConnectionQuality.Unknown);

  // ---- Compartir pantalla + modelo de tiles (destacada + miniaturas tocables) ----
  const [compartiendoPantalla, setCompartiendoPantalla] = useState(false);
  const [remoteCompartiendoPantalla, setRemoteCompartiendoPantalla] = useState(false);
  const [tileDestacada, setTileDestacada] = useState<TileId>("camara-remota");

  // ---- FR-AULA-011..013 (ADR-M3-06): pizarra compartida, solo entre adultos ----
  const [pizarraHabilitada, setPizarraHabilitada] = useState(false);
  const pizarraHabilitadaRef = useRef(false);
  const [pizarraAbierta, setPizarraAbierta] = useState(false);
  const [trazos, setTrazos] = useState<Trazo[]>([]);
  const trazosRef = useRef<Trazo[]>([]);
  useEffect(() => {
    trazosRef.current = trazos;
  }, [trazos]);

  // ---- FR-AULA-002: degradación automática video → audio → texto ----
  const [camApagadaPorDegradacion, setCamApagadaPorDegradacion] = useState(false);
  const [modoTexto, setModoTexto] = useState(false);
  const [mensajesTexto, setMensajesTexto] = useState<MensajeTexto[]>([]);
  const [textoAEnviar, setTextoAEnviar] = useState("");
  const [avisoFiltro, setAvisoFiltro] = useState<string | null>(null);

  // ---- Sala de espera: el alumno, hasta que lo admiten; el Tutor, a quién tiene esperando ----
  const admitidoRef = useRef(false);
  const [admitido, setAdmitido] = useState(false);
  const saliendoRef = useRef(false);
  const [hayOtro, setHayOtro] = useState(false);
  const [enEspera, setEnEspera] = useState<{ identity: string; nombre: string } | null>(null);
  const [admisionPospuesta, setAdmisionPospuesta] = useState(false);
  const [otroAdmitido, setOtroAdmitido] = useState(false);
  /** El Tutor recuerda a quién ya dejó entrar: si se le corta y vuelve, entra sin esperar. */
  const admitidosRef = useRef<Set<string>>(new Set());
  const enEsperaIdRef = useRef<string | null>(null);
  /** Mientras espera, el alumno repite el aviso: uno solo se puede perder (p. ej. al reconectar). */
  const avisoEsperaRef = useRef<ReturnType<typeof setInterval> | undefined>(undefined);

  // ---- Pantalla previa (lobby): preview + elección de dispositivo ----
  const [camaras, setCamaras] = useState<Dispositivo[]>([]);
  const [microfonos, setMicrofonos] = useState<Dispositivo[]>([]);
  const [parlantes, setParlantes] = useState<Dispositivo[]>([]);
  const [camaraId, setCamaraId] = useState<string | undefined>(undefined);
  const [microfonoId, setMicrofonoId] = useState<string | undefined>(undefined);
  const [parlanteId, setParlanteId] = useState<string | undefined>(undefined);
  const [configAbierta, setConfigAbierta] = useState(false);
  const camaraIdRef = useRef(camaraId);
  const microfonoIdRef = useRef(microfonoId);
  useEffect(() => {
    camaraIdRef.current = camaraId;
    microfonoIdRef.current = microfonoId;
  });
  const [errorCam, setErrorCam] = useState<string | null>(null);
  const [errorMic, setErrorMic] = useState<string | null>(null);
  const [previaLista, setPreviaLista] = useState(false);

  /** ADR-M3-04: corta la grabación y sube el audio. Un fallo no frena el cierre de la clase:
   *  sin audio no hay resumen y el adicional se reembolsa solo. */
  const cerrarGrabacion = useCallback(async () => {
    const grabador = grabadorRef.current;
    if (!grabador) return;
    grabadorRef.current = null;
    setGrabandoResumen(false);
    try {
      const audio = await grabador.detener();
      if (audio) await subirAudioResumen(sesionId, audio);
    } catch {
      // Sin audio → el resumen queda fallido y se reembolsa el adicional (BR-PAG-11).
    }
  }, [sesionId]);

  const estadoRef = useRef<Estado>(estado);
  const camActivaRef = useRef(camActiva);
  const micActivaRef = useRef(micActiva);
  const camApagadaPorDegradacionRef = useRef(camApagadaPorDegradacion);
  const modoTextoRef = useRef(modoTexto);
  useEffect(() => {
    estadoRef.current = estado;
    camActivaRef.current = camActiva;
    micActivaRef.current = micActiva;
    camApagadaPorDegradacionRef.current = camApagadaPorDegradacion;
    modoTextoRef.current = modoTexto;
  });

  // Contadores de lecturas consecutivas de `ConnectionQualityChanged` — no son
  // estado de React a propósito, se leen y escriben sincrónicamente en cada
  // evento y nunca deberían disparar un re-render por sí solos.
  const consecutivasPoorRef = useRef(0);
  const consecutivasBuenaRef = useRef(0);
  const consecutivasLostRef = useRef(0);

  /**
   * FR-AULA-002: video → audio (escalón 1) y audio → texto (escalón 2).
   * Se llama en cada `ConnectionQualityChanged` del participante LOCAL — la
   * calidad del otro participante no la podemos arreglar apagando algo acá.
   */
  const evaluarDegradacion = useCallback((quality: ConnectionQuality) => {
    // Escalón 1 — video → audio.
    if (quality === ConnectionQuality.Poor) {
      consecutivasPoorRef.current += 1;
      consecutivasBuenaRef.current = 0;
    } else if (quality === ConnectionQuality.Good || quality === ConnectionQuality.Excellent) {
      consecutivasBuenaRef.current += 1;
      consecutivasPoorRef.current = 0;
    }
    // `Lost`/`Unknown` no suman para ninguno de los dos contadores de este
    // escalón: una pérdida total ya la atiende el escalón 2, y no queremos
    // que una lectura `Unknown` aislada cuente como "mejoró".

    if (
      consecutivasPoorRef.current >= UMBRAL_LECTURAS &&
      !camApagadaPorDegradacionRef.current &&
      camActivaRef.current
    ) {
      consecutivasPoorRef.current = 0;
      camApagadaPorDegradacionRef.current = true;
      setCamApagadaPorDegradacion(true);
      roomRef.current?.localParticipant.setCameraEnabled(false);
    } else if (
      consecutivasBuenaRef.current >= UMBRAL_LECTURAS &&
      camApagadaPorDegradacionRef.current
    ) {
      consecutivasBuenaRef.current = 0;
      camApagadaPorDegradacionRef.current = false;
      setCamApagadaPorDegradacion(false);
      // Solo la reprendemos si la persona la seguía queriendo prendida — si
      // la apagó a mano durante la degradación, esa decisión manual gana.
      if (camActivaRef.current) {
        roomRef.current?.localParticipant.setCameraEnabled(true);
      }
    }

    // Escalón 2 — audio → texto. Entra y sale sin el debounce de "3 lecturas
    // buenas" del escalón 1: perder del todo la conexión es urgente, así que
    // conviene mostrar el canal de texto apenas se confirma (3 lecturas
    // `Lost`) y esconderlo apenas hay UNA lectura que ya no es `Lost` — acá
    // el costo de un falso "ya se recuperó" es bajo (el input simplemente
    // reaparece en el próximo ciclo si la pérdida sigue).
    if (quality === ConnectionQuality.Lost) {
      consecutivasLostRef.current += 1;
      if (consecutivasLostRef.current >= UMBRAL_LECTURAS && !modoTextoRef.current) {
        modoTextoRef.current = true;
        setModoTexto(true);
      }
    } else {
      consecutivasLostRef.current = 0;
      if (modoTextoRef.current) {
        modoTextoRef.current = false;
        setModoTexto(false);
      }
    }
  }, []);

  /** Corta el clasificador y descarta todos los buffers de evidencia (Artículo V). Idempotente. */
  const destruirProteccion = useCallback(() => {
    clasificadorRef.current?.destruir();
    clasificadorRef.current = null;
    for (const b of buffersRef.current.values()) b.descartar();
    buffersRef.current.clear();
    remotosRef.current.clear();
  }, []);

  // ---- Kill-switch (ADR-M3-07): estado, orquestador y clasificador, antes de dejar entrar ----
  useEffect(() => {
    let cancelado = false;
    const REMOTOS: TileId[] = ["camara-remota", "pantalla-remota"];
    const sidDe = (sid: string) => (REMOTOS.find((t) => sidPorTileRef.current[t] === sid));

    orquestadorRef.current = crearOrquestador({
      sesionId,
      hayMenor: () => hayMenorRef.current,
      api: {
        disparar: (id) => dispararKillswitch(sesionId, id),
        confirmar: (vio) => confirmarKillswitch(sesionId, vio),
        subirEvidencia: (clip, dur) => subirEvidencia(sesionId, clip, dur),
        reportar: (e) => reportarClasificador(sesionId, e),
      },
      congelarClip: (sid) => buffersRef.current.get(sid)?.congelar() ?? null,
      sala: {
        ocultarTrack: (sid) => {
          ocultosRef.current.add(sid);
          const t = sidDe(sid);
          if (t) pistasRef.current[t]?.detach();
          setVersionOcultos((v) => v + 1);
        },
        mostrarTrack: (sid) => {
          ocultosRef.current.delete(sid);
          setVersionOcultos((v) => v + 1);
        },
        ocultarTodoRemoto: () => {
          todoOcultoRef.current = true;
          for (const t of REMOTOS) pistasRef.current[t]?.detach();
          setVersionOcultos((v) => v + 1);
        },
        mostrarTodoRemoto: () => {
          todoOcultoRef.current = false;
          setVersionOcultos((v) => v + 1);
        },
        enviarAviso: (m) => {
          void roomRef.current?.localParticipant
            .publishData(new TextEncoder().encode(JSON.stringify(m)), { reliable: true, topic: TOPIC_KILLSWITCH })
            .catch(() => {});
        },
        salir: () => roomRef.current?.disconnect(),
      },
      clasificador: {
        pausar: (sid) => clasificadorRef.current?.pausar(sid),
        reanudar: (sid) => clasificadorRef.current?.reanudar(sid),
        destruir: destruirProteccion,
      },
      ui: {
        preguntar: setPregunta,
        cerrarPregunta: () => setPregunta(null),
        cierre: setCierreKillswitch,
        aviso: (texto) => {
          setAvisoKillswitch(texto);
          clearTimeout(temporizadorAvisoRef.current);
          temporizadorAvisoRef.current = setTimeout(() => setAvisoKillswitch(null), 6000);
        },
        limpiarAviso: () => {
          clearTimeout(temporizadorAvisoRef.current);
          setAvisoKillswitch(null);
        },
      },
      esperar: (ms) => new Promise((r) => setTimeout(r, ms)),
      ahora: () => Date.now(),
    });

    (async () => {
      let menor = true;
      let anatomico = false;
      try {
        const e = await getEstadoKillswitch(sesionId);
        menor = e.hayMenor;
        anatomico = e.contenidoAnatomico === true && !menor;
      } catch {
        // Ante la duda, hay un menor (y no hay modo salud).
      }
      if (cancelado) return;
      hayMenorRef.current = menor;
      setHayMenor(menor);
      contenidoAnatomicoRef.current = anatomico;
      setContenidoAnatomico(anatomico);
      try {
        const k = await ClasificadorKillswitch.crear({
          hayMenor: menor,
          contenidoAnatomico: anatomico,
          alDetectar: (d) => void orquestadorRef.current?.alDetectar(d),
          alDemorar: () => orquestadorRef.current?.alDemorar(),
          alNormalizar: () => orquestadorRef.current?.alNormalizar(),
          alCaer: () => orquestadorRef.current?.alCaer(),
          alReactivar: () => orquestadorRef.current?.alReactivar(),
          alFallarDefinitivo: () => orquestadorRef.current?.alFallarDefinitivo(),
        });
        if (cancelado) {
          k.destruir();
          return;
        }
        clasificadorRef.current = k;
        for (const [sid, r] of remotosRef.current) k.observar(sid, r.duenoId, r.pista);
        proteccionRef.current = "lista";
        setProteccion("lista");
      } catch {
        if (cancelado) return;
        proteccionRef.current = "no-disponible";
        setProteccion("no-disponible");
        if (!menor) void reportarClasificador(sesionId, "NO_CARGA").catch(() => {});
      }
    })();

    return () => {
      cancelado = true;
      clearTimeout(temporizadorAvisoRef.current);
      destruirProteccion();
    };
  }, [sesionId, destruirProteccion]);

  // Con el aula bloqueada, la cámara de la previa no tiene por qué seguir prendida.
  const bloqueoProteccion = cierreKillswitch !== null || (proteccion === "no-disponible" && hayMenor !== false);
  useEffect(() => {
    if (!bloqueoProteccion) return;
    previaVideoTrackRef.current?.stop();
    previaAudioTrackRef.current?.stop();
  }, [bloqueoProteccion]);

  // ---- Preview de la pantalla previa: pide permisos ACÁ, antes de que
  // corra ningún reloj de no-show del lado del backend. ----
  useEffect(() => {
    let cancelado = false;

    async function iniciarPreview() {
      try {
        const videoTrack = await createLocalVideoTrack();
        if (cancelado) {
          videoTrack.stop();
          return;
        }
        previaVideoTrackRef.current = videoTrack;
        if (localVideoRef.current) videoTrack.attach(localVideoRef.current);
      } catch (err) {
        if (!cancelado) setErrorCam(mensajeErrorDispositivo(err));
      }

      try {
        const audioTrack = await createLocalAudioTrack();
        if (cancelado) {
          audioTrack.stop();
          return;
        }
        previaAudioTrackRef.current = audioTrack;
      } catch (err) {
        if (!cancelado) setErrorMic(mensajeErrorDispositivo(err));
      }

      if (!cancelado) setPreviaLista(true);

      try {
        const [cams, mics] = await Promise.all([
          Room.getLocalDevices("videoinput"),
          Room.getLocalDevices("audioinput"),
        ]);
        if (cancelado) return;
        setCamaras(cams.map((d) => ({ deviceId: d.deviceId, label: d.label || "Cámara" })));
        setMicrofonos(mics.map((d) => ({ deviceId: d.deviceId, label: d.label || "Micrófono" })));
      } catch {
        // La enumeración de dispositivos es un extra (elegir cuál usar) — si
        // falla, el preview ya adquirido con el dispositivo por defecto sigue sirviendo.
      }
    }

    iniciarPreview();
    return () => {
      cancelado = true;
      previaVideoTrackRef.current?.stop();
      previaAudioTrackRef.current?.stop();
      previaVideoTrackRef.current = null;
      previaAudioTrackRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function alternarCamPrevia() {
    const nueva = !camActiva;
    setCamActiva(nueva);
    if (previaVideoTrackRef.current) {
      previaVideoTrackRef.current.mediaStreamTrack.enabled = nueva;
    }
  }

  function alternarMicPrevia() {
    const nueva = !micActiva;
    setMicActiva(nueva);
    if (previaAudioTrackRef.current) {
      previaAudioTrackRef.current.mediaStreamTrack.enabled = nueva;
    }
  }

  async function cambiarCamara(deviceId: string) {
    setCamaraId(deviceId);
    setErrorCam(null);
    previaVideoTrackRef.current?.stop();
    try {
      const track = await createLocalVideoTrack({ deviceId });
      previaVideoTrackRef.current = track;
      track.mediaStreamTrack.enabled = camActivaRef.current;
      if (localVideoRef.current) track.attach(localVideoRef.current);
    } catch (err) {
      setErrorCam(mensajeErrorDispositivo(err));
    }
  }

  async function cambiarMicrofono(deviceId: string) {
    setMicrofonoId(deviceId);
    setErrorMic(null);
    previaAudioTrackRef.current?.stop();
    try {
      const track = await createLocalAudioTrack({ deviceId });
      track.mediaStreamTrack.enabled = micActivaRef.current;
      previaAudioTrackRef.current = track;
    } catch (err) {
      setErrorMic(mensajeErrorDispositivo(err));
    }
  }

  /** Un tile remoto no se conecta si está oculto por una detección o por la falla del clasificador. */
  function remotoOculto(id: TileId): boolean {
    if (id !== "camara-remota" && id !== "pantalla-remota") return false;
    const sid = sidPorTileRef.current[id];
    return todoOcultoRef.current || (sid !== undefined && ocultosRef.current.has(sid));
  }

  /** La cámara/mic de la previa quedan libres antes de que la Room adquiera los suyos —
   *  sostener dos handles del mismo dispositivo a la vez es lo que en algunos navegadores
   *  dispara NotReadableError. */
  function soltarPrevia() {
    previaVideoTrackRef.current?.stop();
    previaAudioTrackRef.current?.stop();
    previaVideoTrackRef.current = null;
    previaAudioTrackRef.current = null;
  }

  // ---- Conexión real a la sala (recién al confirmar desde la previa) ----
  const conectar = useCallback(async () => {
    // El alumno conserva la previa: es lo que ve de sí mismo en la sala de espera.
    if (esTutorRef.current) soltarPrevia();

    setError(null);
    setEstado("conectando");

    try {
      const tokenResp = await api.post<TokenResponse>(
        `/api/sesiones/${sesionId}/token?clasificadorListo=${proteccionRef.current === "lista"}`
      );
      // El backend es la fuente de verdad de si hay un menor: si difiere, se ajusta el umbral.
      if (typeof tokenResp.hayMenor === "boolean" && tokenResp.hayMenor !== hayMenorRef.current) {
        hayMenorRef.current = tokenResp.hayMenor;
        setHayMenor(tokenResp.hayMenor);
        clasificadorRef.current?.actualizarHayMenor(tokenResp.hayMenor);
      }
      const anatomico = tokenResp.contenidoAnatomico === true && tokenResp.hayMenor === false;
      if (anatomico !== contenidoAnatomicoRef.current) {
        contenidoAnatomicoRef.current = anatomico;
        setContenidoAnatomico(anatomico);
        clasificadorRef.current?.actualizarContenidoAnatomico(anatomico);
      }

      const room = new Room({
        adaptiveStream: true,
        dynacast: true,
      });

      const enEsperaLocal = () => !esTutorRef.current && !admitidoRef.current;
      const yaAdmitido = !esTutorRef.current && tokenResp.admitido === true;

      const publicarMedios = () => {
        room.localParticipant.setCameraEnabled(camActivaRef.current, { deviceId: camaraIdRef.current });
        room.localParticipant.setMicrophoneEnabled(micActivaRef.current, { deviceId: microfonoIdRef.current });
      };

      /** El Tutor dejó entrar al alumno: recién ahora publica y se suscribe a lo del otro. */
      const admitirme = () => {
        if (admitidoRef.current) return;
        admitidoRef.current = true;
        clearInterval(avisoEsperaRef.current);
        setAdmitido(true);
        soltarPrevia();
        publicarMedios();
        for (const p of room.remoteParticipants.values()) {
          for (const pub of p.trackPublications.values()) pub.setSubscribed(true);
        }
        if (pizarraHabilitadaRef.current) publicarPizarra(room, { op: "sync-pedido" });
        publicarSala(room, { op: "adentro" });
        setEstado(room.remoteParticipants.size > 0 ? "conectado" : "esperando");
      };

      const atenderSala = (m: MensajeSala, identity: string, nombre: string) => {
        if (esTutorRef.current) {
          if (m.op === "esperando") {
            if (admitidosRef.current.has(identity)) {
              publicarSala(room, { op: "admitir" }, identity);
              return;
            }
            // El aviso se repite: solo uno nuevo vuelve a abrir el modal si el Tutor lo pospuso.
            if (enEsperaIdRef.current === identity) return;
            enEsperaIdRef.current = identity;
            setEnEspera({ identity, nombre });
            setAdmisionPospuesta(false);
          } else if (m.op === "adentro") {
            admitidosRef.current.add(identity);
            setOtroAdmitido(true);
            if (enEsperaIdRef.current === identity) {
              enEsperaIdRef.current = null;
              setEnEspera(null);
            }
          }
          return;
        }
        if (m.op === "admitir") admitirme();
        else if (m.op === "quien") publicarSala(room, { op: admitidoRef.current ? "adentro" : "esperando" }, identity);
      };

      room.on(RoomEvent.Connected, () => {
        roomConectadoRef.current = true;
        setHayOtro(room.remoteParticipants.size > 0);
        const yaEstaba = room.remoteParticipants.values().next().value;
        if (yaEstaba) setRemoteNombre(yaEstaba.name ?? "");
        // Ya había entrado a la clase y salió (volvió atrás, recargó): no vuelve a esperar.
        if (yaAdmitido) {
          admitirme();
          return;
        }
        if (enEsperaLocal()) {
          setEstado("sala_espera");
          publicarSala(room, { op: "esperando" });
          clearInterval(avisoEsperaRef.current);
          avisoEsperaRef.current = setInterval(() => {
            if (enEsperaLocal() && room.remoteParticipants.size > 0) publicarSala(room, { op: "esperando" });
          }, 3000);
          return;
        }
        setEstado("conectado");
        publicarMedios();
        // Si el alumno ya estaba esperando antes de que entrara el Tutor, que avise.
        if (esTutorRef.current) publicarSala(room, { op: "quien" });
      });

      room.on(RoomEvent.ParticipantConnected, (participante) => {
        setHayOtro(true);
        setRemoteNombre(participante.name ?? "");
        if (enEsperaLocal()) {
          publicarSala(room, { op: "esperando" }, participante.identity);
          return;
        }
        setEstado("conectado");
        if (!esTutorRef.current) publicarSala(room, { op: "adentro" }, participante.identity);
      });

      room.on(RoomEvent.ParticipantDisconnected, (participante) => {
        setHayOtro(room.remoteParticipants.size > 0);
        setEstado(enEsperaLocal() ? "sala_espera" : "esperando");
        setRemoteActivo(false);
        setRemoteNombre("");
        setOtroAdmitido(false);
        setAdmisionPospuesta(false);
        if (enEsperaIdRef.current === participante.identity) {
          enEsperaIdRef.current = null;
          setEnEspera(null);
        }
      });

      // Con autoSubscribe apagado (alumno), lo que el otro publique después (p. ej. su pantalla)
      // hay que pedirlo a mano, y solo una vez admitido.
      room.on(RoomEvent.TrackPublished, (pub) => {
        if (!esTutorRef.current && admitidoRef.current) pub.setSubscribed(true);
      });

      room.on(RoomEvent.LocalTrackPublished, (pub) => {
        if (pub.source === Track.Source.Microphone && pub.track) {
          grabadorRef.current?.agregarPista("local", pub.track.mediaStreamTrack);
        }
        if (pub.source === Track.Source.Camera && pub.track?.kind === Track.Kind.Video) {
          pistasRef.current["camara-local"] = pub.track;
          if (localVideoRef.current) pub.track.attach(localVideoRef.current);
        }
        if (pub.source === Track.Source.ScreenShare && pub.track?.kind === Track.Kind.Video) {
          // Antes solo se marcaba "compartiendo" si el <video> ya existía — y no existe hasta
          // marcarlo: la pantalla propia nunca aparecía.
          pistasRef.current["pantalla-local"] = pub.track;
          setCompartiendoPantalla(true);
          setTileDestacada("pantalla-local");
        }
      });

      room.on(RoomEvent.LocalTrackUnpublished, (pub) => {
        if (pub.source === Track.Source.ScreenShare) {
          delete pistasRef.current["pantalla-local"];
          setCompartiendoPantalla(false);
          setTileDestacada((actual) => (actual === "pantalla-local" ? "camara-remota" : actual));
        }
      });

      room.on(RoomEvent.TrackSubscribed, (track, pub, participante) => {
        if (track.kind === Track.Kind.Video) {
          // Kill-switch (ADR-M3-07): este navegador clasifica y graba el video que RECIBE.
          const tile: TileId = pub.source === Track.Source.ScreenShare ? "pantalla-remota" : "camara-remota";
          sidPorTileRef.current[tile] = pub.trackSid;
          remotosRef.current.set(pub.trackSid, { duenoId: participante.identity, pista: track.mediaStreamTrack });
          clasificadorRef.current?.observar(pub.trackSid, participante.identity, track.mediaStreamTrack);
          const buffer = BufferEvidencia.crear(track.mediaStreamTrack);
          if (buffer) buffersRef.current.set(pub.trackSid, buffer);
          else console.warn("Sin MediaRecorder de video: el corte funciona, pero no habrá clip de evidencia");
        }
        if (track.kind === Track.Kind.Video && pub.source === Track.Source.ScreenShare) {
          // El <video> de la pantalla remota recién se crea al marcar que hay pantalla: la pista
          // se conecta cuando aparece (antes se intentaba acá, con el elemento todavía en null, y
          // el otro veía negro). Siempre la mejor calidad: es texto chico que se tiene que leer.
          pistasRef.current["pantalla-remota"] = track;
          pub.setVideoQuality(VideoQuality.HIGH);
          setRemoteCompartiendoPantalla(true);
          setRemoteNombre(participante.name ?? "");
          setTileDestacada("pantalla-remota");
        } else if (track.kind === Track.Kind.Video) {
          pistasRef.current["camara-remota"] = track;
          if (remoteVideoRef.current && !remotoOculto("camara-remota")) track.attach(remoteVideoRef.current);
          setRemoteActivo(true);
          setRemoteNombre(participante.name ?? "");
        } else if (track.kind === Track.Kind.Audio) {
          remoteAudioTrackRef.current = track;
          if (remoteAudioRef.current) track.attach(remoteAudioRef.current);
        }
        if (track.kind === Track.Kind.Audio) {
          grabadorRef.current?.agregarPista(pub.trackSid, track.mediaStreamTrack);
        }
      });

      room.on(RoomEvent.TrackUnsubscribed, (track, pub) => {
        track.detach();
        if (remoteAudioTrackRef.current === track) remoteAudioTrackRef.current = null;
        grabadorRef.current?.quitarPista(pub.trackSid);
        if (track.kind === Track.Kind.Video) {
          clasificadorRef.current?.dejarDeObservar(pub.trackSid);
          remotosRef.current.delete(pub.trackSid);
          buffersRef.current.get(pub.trackSid)?.descartar();
          buffersRef.current.delete(pub.trackSid);
          ocultosRef.current.delete(pub.trackSid);
          if (pub.source === Track.Source.ScreenShare) {
            delete pistasRef.current["pantalla-remota"];
            setRemoteCompartiendoPantalla(false);
            setTileDestacada((actual) => (actual === "pantalla-remota" ? "camara-remota" : actual));
          } else {
            delete pistasRef.current["camara-remota"];
            setRemoteActivo(false);
          }
        }
      });

      room.on(RoomEvent.ConnectionQualityChanged, (quality, participante) => {
        if (!participante.isLocal) return;
        setCalidad(quality);
        evaluarDegradacion(quality);
      });

      // Escalón 2 de FR-AULA-002: canal de emergencia por datos de LiveKit,
      // NO es un chat de la clase — solo existe mientras `modoTexto` está
      // activo (conexión `Lost` sostenida) y se ignora cualquier paquete que
      // no tenga el formato esperado (podría venir de otra parte del SDK).
      room.on(RoomEvent.DataReceived, (payload, participante, _tipo, topic) => {
        if (topic === TOPIC_SALA) {
          const m = decodificarSala(payload);
          if (m && participante) atenderSala(m, participante.identity, participante.name ?? "");
          return;
        }
        if (topic === TOPIC_KILLSWITCH) {
          // Solo informa a la UI: el estado de verdad está en el backend.
          const aviso = decodificarAviso(payload);
          if (aviso) orquestadorRef.current?.alRecibirAviso(aviso);
          return;
        }
        if (topic === "pizarra") {
          // FR-AULA-012: sin habilitación (hay un Menor) todo paquete de pizarra se descarta.
          if (!pizarraHabilitadaRef.current || enEsperaLocal()) return;
          const m = decodificar(payload);
          if (!m) return;
          if (m.op === "sync-pedido") {
            for (const s of mensajesDeSync(trazosRef.current)) publicarPizarra(room, s);
          } else if (m.op === "abrir") {
            setPizarraAbierta(true);
          } else {
            setTrazos((prev) => aplicar(prev, m, false));
          }
          return;
        }
        try {
          const data = JSON.parse(new TextDecoder().decode(payload)) as {
            tipo?: string;
            texto?: string;
          };
          if (data.tipo === "mensaje" && typeof data.texto === "string") {
            // Se vuelve a filtrar al recibir: no se confía en el filtro del otro navegador.
            const texto = filtrarMensaje(data.texto).texto;
            setMensajesTexto((prev) => [...prev.slice(-9), { propio: false, texto }]);
          }
        } catch {
          // Paquete que no es del formato esperado — se ignora.
        }
      });

      room.on(RoomEvent.Reconnecting, () => {
        setEstado("reconectando");
      });

      room.on(RoomEvent.Reconnected, () => {
        setEstado(enEsperaLocal() ? "sala_espera" : roomConectadoRef.current ? "conectado" : "esperando");
      });

      room.on(RoomEvent.Disconnected, () => {
        roomConectadoRef.current = false;
        clearInterval(avisoEsperaRef.current);
        // Corte automático de la clase (T-fin+5): se sube lo grabado igual.
        if (estadoRef.current !== "finalizada") void cerrarGrabacion();
        setRemoteActivo(false);
        destruirProteccion();
        if (saliendoRef.current) return;
        // Si el orquestador ya mostró un cierre (o llegó el aviso de corte), la página no muestra el suyo.
        if (orquestadorRef.current?.alDesconectar()) return;
        if (estadoRef.current === "finalizada") return;
        // R-C C-1 (H-AULA-1): si el aviso entre pares no llegó, el estado de la sesión en el
        // backend dice si fue un corte por seguridad, el fin de la clase o que el otro se fue.
        // Hasta 3 intentos: la sala se cierra un instante antes de que el estado se persista.
        void (async () => {
          let cierre: CierreAula = "esperando";
          for (let intento = 0; intento < 3; intento++) {
            try {
              cierre = resolverCierre(await getEstadoKillswitch(sesionId));
            } catch {
              cierre = "esperando";
            }
            if (cierre !== "esperando") break;
            await new Promise((r) => setTimeout(r, 1000));
          }
          if (cierre === "seguridad") setCierreKillswitch("seguridad");
          else if (cierre === "finalizada") setEstado("finalizada");
          else setEstado("esperando");
        })();
      });

      roomRef.current = room;
      pizarraHabilitadaRef.current = tokenResp.pizarraHabilitada === true;
      setPizarraHabilitada(pizarraHabilitadaRef.current);
      if (tokenResp.grabarAudioResumen && !grabadorRef.current) {
        grabadorRef.current = GrabadorAudioResumen.crear();
        setGrabandoResumen(grabadorRef.current !== null);
      }
      // El alumno no recibe nada del otro hasta que el Tutor lo deja entrar.
      await room.connect(tokenResp.livekitUrl, tokenResp.token, { autoSubscribe: esTutorRef.current });
      // Si el otro ya estaba dibujando, que nos mande lo que hay en la pizarra.
      if (pizarraHabilitadaRef.current && !enEsperaLocal()) publicarPizarra(room, { op: "sync-pedido" });
      const micPub = room.localParticipant?.getTrackPublication(Track.Source.Microphone);
      if (micPub?.track) grabadorRef.current?.agregarPista("local", micPub.track.mediaStreamTrack);

      if (room.localParticipant) {
        const camPub = room.localParticipant.getTrackPublication(
          Track.Source.Camera
        );
        if (camPub?.videoTrack) {
          pistasRef.current["camara-local"] = camPub.videoTrack;
          if (localVideoRef.current) camPub.videoTrack.attach(localVideoRef.current);
        }
      }

      if (!enEsperaLocal()) setEstado("conectado");
    } catch (err) {
      if (err instanceof ApiError && err.codigo === "CLASIFICADOR_NO_LISTO") {
        setCierreKillswitch("no-disponible");
        return;
      }
      if (err instanceof ApiError) {
        if (err.status === 404) {
          setError("Sesión no encontrada.");
        } else if (err.status === 403) {
          setError("No sos participante de esta sesión.");
        } else if (err.status === 422) {
          setEstado("sala_no_disponible");
          setError(
            "La sala se abre unos minutos antes de la clase. Volvi a intentarlo en un momento."
          );
        } else {
          setError(err.message || "No se pudo conectar a la sala.");
        }
      } else {
        setError(mensajeErrorDispositivo(err));
      }
      // El 422 ya dejó "sala_no_disponible" (con "Volver a intentar"). Antes se leía
      // estadoRef, que todavía no se había actualizado, y la sala terminaba en "error".
      if (!(err instanceof ApiError && err.status === 422)) setEstado("error");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sesionId, evaluarDegradacion, cerrarGrabacion, destruirProteccion]);

  useEffect(() => {
    return () => {
      roomConectadoRef.current = false;
      clearInterval(avisoEsperaRef.current);
      roomRef.current?.disconnect();
      roomRef.current = null;
      destruirProteccion();
    };
  }, [destruirProteccion]);

  async function finalizar() {
    setFinalizando(true);
    setError(null);
    try {
      await cerrarGrabacion();
      await api.post(`/api/sesiones/${sesionId}/finalizar`);
      destruirProteccion();
      roomRef.current?.disconnect();
      roomRef.current = null;
      setEstado("finalizada");
      router.replace("/cuenta/reservas");
    } catch (err) {
      if (err instanceof ApiError && err.message) {
        setError(err.message);
      } else {
        setError("No se pudo finalizar la clase.");
      }
      setFinalizando(false);
    }
  }

  function alternarCam() {
    const nueva = !camActiva;
    setCamActiva(nueva);
    // Si la persona prende la cámara a mano mientras estaba apagada por
    // degradación, se respeta su decisión explícita — si la conexión sigue
    // mala, el próximo `ConnectionQualityChanged` la vuelve a apagar solo.
    if (nueva && camApagadaPorDegradacionRef.current) {
      camApagadaPorDegradacionRef.current = false;
      setCamApagadaPorDegradacion(false);
    }
    roomRef.current?.localParticipant.setCameraEnabled(nueva);
  }

  function alternarMic() {
    const nueva = !micActiva;
    setMicActiva(nueva);
    roomRef.current?.localParticipant.setMicrophoneEnabled(nueva);
  }

  /** El Tutor deja entrar a quien está en la sala de espera. */
  function admitirAlumno() {
    if (!enEspera || !roomRef.current) return;
    publicarSala(roomRef.current, { op: "admitir" }, enEspera.identity);
    // Queda guardado: si el alumno sale y vuelve, entra directo aunque el Tutor haya recargado.
    void api.post(`/api/sesiones/${sesionId}/admitir`, { usuarioId: enEspera.identity }).catch(() => {});
    admitidosRef.current.add(enEspera.identity);
    setOtroAdmitido(true);
    enEsperaIdRef.current = null;
    setEnEspera(null);
  }

  /** Salir de la llamada sin terminar la clase: se puede volver a entrar mientras dure. */
  function salir() {
    saliendoRef.current = true;
    roomRef.current?.disconnect();
    roomRef.current = null;
    router.push("/cuenta/reservas");
  }

  async function abrirConfiguracion() {
    setConfigAbierta(true);
    const lista = (ds: MediaDeviceInfo[], nombre: string) =>
      ds.map((d) => ({ deviceId: d.deviceId, label: d.label || nombre }));
    try {
      const [cams, mics, salidas] = await Promise.all([
        Room.getLocalDevices("videoinput"),
        Room.getLocalDevices("audioinput"),
        Room.getLocalDevices("audiooutput").catch(() => []),
      ]);
      setCamaras(lista(cams, "Cámara"));
      setMicrofonos(lista(mics, "Micrófono"));
      setParlantes(lista(salidas, "Parlante"));
    } catch {
      // Sin la lista, el modal avisa que no encontró dispositivos.
    }
  }

  /** En la sala de espera se cambia la previa; ya en la clase, la pista publicada. */
  async function elegirDispositivo(tipo: MediaDeviceKind, deviceId: string) {
    const room = roomRef.current;
    const publicando = room !== null && (esTutor || admitido);
    try {
      if (tipo === "audiooutput") {
        setParlanteId(deviceId);
        if (room) await room.switchActiveDevice("audiooutput", deviceId);
      } else if (!publicando) {
        await (tipo === "videoinput" ? cambiarCamara(deviceId) : cambiarMicrofono(deviceId));
      } else {
        if (tipo === "videoinput") setCamaraId(deviceId);
        else setMicrofonoId(deviceId);
        await room.switchActiveDevice(tipo, deviceId);
      }
    } catch {
      setError("No pudimos cambiar a ese dispositivo.");
    }
  }

  async function alternarCompartirPantalla() {
    if (!roomRef.current) return;
    try {
      // Resolución 1080 y "detail": en una clase se comparte texto y fórmulas, que con la
      // calidad por defecto (pensada para video) llegaban borrosos.
      await roomRef.current.localParticipant.setScreenShareEnabled(
        !compartiendoPantalla,
        { audio: false, contentHint: "detail", resolution: ScreenSharePresets.h1080fps15.resolution },
        { screenShareEncoding: ScreenSharePresets.h1080fps15.encoding }
      );
      // El estado real (`compartiendoPantalla`) lo confirman los eventos
      // LocalTrackPublished/LocalTrackUnpublished de arriba — no acá, para que
      // quede correcto también cuando el usuario corta desde el botón nativo
      // "Stop sharing" del navegador en vez de este menú.
    } catch (err) {
      // NotAllowedError: el usuario cerró el selector nativo del navegador sin
      // elegir nada — no es un error real, no hace falta mostrar nada.
      if (!(err instanceof DOMException && err.name === "NotAllowedError")) {
        setError("No se pudo compartir la pantalla.");
      }
    }
  }

  /** Una acción propia en la pizarra: se aplica acá y se le manda al otro. */
  function accionPizarra(m: MensajePizarra) {
    setTrazos((prev) => aplicar(prev, m, true));
    if (roomRef.current) publicarPizarra(roomRef.current, m);
  }

  function alternarPizarra() {
    if (pizarraAbierta) {
      setPizarraAbierta(false);
      return;
    }
    setPizarraAbierta(true);
    if (roomRef.current) publicarPizarra(roomRef.current, { op: "abrir" });
  }

  function enviarMensajeTexto(e: React.FormEvent) {
    e.preventDefault();
    const original = textoAEnviar.trim();
    if (!original || !roomRef.current) return;
    const { texto, contactoOculto, insultoOculto } = filtrarMensaje(original);
    const payload = new TextEncoder().encode(JSON.stringify({ tipo: "mensaje", texto }));
    roomRef.current.localParticipant.publishData(payload, { reliable: true });
    setMensajesTexto((prev) => [...prev.slice(-9), { propio: true, texto }]);
    setTextoAEnviar("");
    setAvisoFiltro(
      contactoOculto
        ? "Los datos de contacto no se comparten en Tinku: se ocultaron del mensaje."
        : insultoOculto
          ? "Se ocultaron palabras ofensivas del mensaje."
          : null,
    );
  }

  // ---------------------------------------------------------------- Bloqueos de protección
  if (cierreKillswitch) return <AvisoProteccion motivo={cierreKillswitch} />;
  if (estado === "previa" && proteccion === "no-disponible" && hayMenor !== false) {
    return <AvisoProteccion motivo="no-disponible" />;
  }

  // ---------------------------------------------------------------- Previa
  if (estado === "previa") {
    const bloqueadoTotal = errorCam && errorMic;
    // Con un menor (o sin saberlo todavía) no se entra hasta que el clasificador esté listo.
    const activandoProteccion = hayMenor !== false && proteccion === "cargando";

    return (
      <main className="flex min-h-screen flex-col items-center justify-center bg-gray-900 px-4 py-8 text-gray-50">
        <div className="w-full max-w-md">
          <div className="mb-6 flex justify-center">
            <Logo href="/cuenta/reservas" claro />
          </div>
          <Modal
            abierto={contenidoAnatomico && !avisoSaludVisto}
            onCerrar={() => setAvisoSaludVisto(true)}
            titulo="Clase de Medicina o Anatomía"
            descripcion="En esta clase la protección del aula se flexibiliza, porque el contenido puede incluir el cuerpo humano."
            ancho="sm"
            pie={<Boton onClick={() => setAvisoSaludVisto(true)}>Entendido</Boton>}
          />
          <h1 className="mb-1 text-center text-2xl font-extrabold text-gray-50">Tu clase está por empezar</h1>
          <p className="mb-6 text-center text-sm text-gray-400">Revisá tu cámara y tu micrófono antes de entrar.</p>

          <div className="relative aspect-video overflow-hidden rounded-xl border-2 border-gray-700 bg-black">
            <video ref={localVideoRef} autoPlay playsInline muted className="h-full w-full object-cover" />
            {!previaLista && (
              <div className="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
                Preparando cámara…
              </div>
            )}
            {previaLista && !camActiva && (
              <div className="absolute inset-0 flex items-center justify-center bg-gray-800 text-sm text-gray-400">
                Cámara apagada
              </div>
            )}
          </div>

          {(errorCam || errorMic) && (
            <div className="mt-3 rounded-2xl border border-amber-700 bg-amber-950/40 px-4 py-3.5 text-sm text-amber-200" role="alert">
              {errorCam && <p>{errorCam}</p>}
              {errorMic && <p className={errorCam ? "mt-1" : ""}>{errorMic}</p>}
              <p className="mt-2 text-amber-100/90">
                Para darle permiso: tocá el candado de la barra de direcciones, elegí <strong>Permitir</strong> en cámara y micrófono y volvé a intentar.
              </p>
              <button
                type="button"
                onClick={() => window.location.reload()}
                className="mt-3 inline-flex min-h-10 cursor-pointer items-center rounded-control border border-amber-600 px-3 font-semibold text-amber-100 hover:bg-amber-900/40"
              >
                Volver a intentar
              </button>
            </div>
          )}

          <div className="mt-4 flex items-center justify-center gap-2">
            <BotonControl
              activo={camActiva}
              onClick={alternarCamPrevia}
              etiquetaOn="Cámara on"
              etiquetaOff="Cámara off"
              IconoOn={Video}
              IconoOff={VideoOff}
            />
            <BotonControl
              activo={micActiva}
              onClick={alternarMicPrevia}
              etiquetaOn="Micro on"
              etiquetaOff="Micro off"
              IconoOn={Mic}
              IconoOff={MicOff}
            />
          </div>

          {(camaras.length > 1 || microfonos.length > 1) && (
            <div className="mt-4 flex flex-col gap-2 text-sm">
              {camaras.length > 1 && (
                <label className="flex flex-col gap-1 text-gray-400">
                  Cámara
                  <select
                    value={camaraId ?? camaras[0]?.deviceId}
                    onChange={(e) => cambiarCamara(e.target.value)}
                    className="rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-gray-50"
                  >
                    {camaras.map((c) => (
                      <option key={c.deviceId} value={c.deviceId}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {microfonos.length > 1 && (
                <label className="flex flex-col gap-1 text-gray-400">
                  Micrófono
                  <select
                    value={microfonoId ?? microfonos[0]?.deviceId}
                    onChange={(e) => cambiarMicrofono(e.target.value)}
                    className="rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-gray-50"
                  >
                    {microfonos.map((m) => (
                      <option key={m.deviceId} value={m.deviceId}>
                        {m.label}
                      </option>
                    ))}
                  </select>
                </label>
              )}
            </div>
          )}

          <button
            type="button"
            className="mt-6 min-h-13 w-full cursor-pointer rounded-control bg-marca-600 px-4 py-3 text-base font-bold text-white enabled:hover:bg-marca-500 disabled:cursor-not-allowed disabled:opacity-50"
            onClick={conectar}
            disabled={!previaLista || Boolean(bloqueadoTotal) || activandoProteccion}
          >
            {bloqueadoTotal
              ? "Revisá los permisos para continuar"
              : activandoProteccion
                ? "Activando la protección del aula…"
                : esTutor
                  ? "Entrar a la clase"
                  : "Entrar a la sala de espera"}
          </button>
          <p className="mt-4 text-center text-[13px] text-gray-400">
            La clase se hace dentro de Tinku. No compartas teléfonos, emails ni redes.
          </p>
        </div>
      </main>
    );
  }

  const dialogoConfiguracion = (
    <DialogoConfiguracion
      abierto={configAbierta}
      onCerrar={() => setConfigAbierta(false)}
      camaras={camaras}
      microfonos={microfonos}
      parlantes={parlantes}
      camaraId={camaraId}
      microfonoId={microfonoId}
      parlanteId={parlanteId}
      onCambiarCamara={(id) => void elegirDispositivo("videoinput", id)}
      onCambiarMicrofono={(id) => void elegirDispositivo("audioinput", id)}
      onCambiarParlante={(id) => void elegirDispositivo("audiooutput", id)}
    />
  );
  const opcionConfiguracion: OpcionMenuLlamada = {
    id: "configuracion",
    label: "Configuración",
    Icono: Settings,
    onClick: () => void abrirConfiguracion(),
  };
  const botonSalir = (
    <button
      type="button"
      className="flex cursor-pointer items-center gap-2 rounded-full border border-gray-600 px-4 py-2.5 text-sm font-semibold text-gray-100 hover:border-gray-400"
      onClick={salir}
    >
      <LogOut className="h-4 w-4" aria-hidden />
      Salir
    </button>
  );

  // ---------------------------------------------------------------- Sala de espera (alumno)
  if (estado === "sala_espera") {
    return (
      <main className="flex min-h-screen flex-col items-center justify-center bg-gray-900 px-4 py-8 text-gray-50">
        <div className="w-full max-w-md">
          <h1 className="mb-1 text-center text-2xl font-extrabold text-gray-50">Estás en la sala de espera</h1>
          <p className="mb-6 text-center text-sm text-gray-400" role="status">
            {hayOtro
              ? `${remoteNombre || "Tu tutor"} ya está en la clase. En un momento te deja entrar.`
              : "Tu tutor todavía no entró. Cuando llegue, te deja pasar."}
          </p>
          <div className="relative aspect-video overflow-hidden rounded-xl border-2 border-gray-700 bg-black">
            <video
              ref={(el) => {
                localVideoRef.current = el;
                if (el && previaVideoTrackRef.current) previaVideoTrackRef.current.attach(el);
              }}
              autoPlay
              playsInline
              muted
              className="h-full w-full -scale-x-100 object-cover"
            />
            {!camActiva && (
              <div className="absolute inset-0 flex items-center justify-center bg-gray-800 text-sm text-gray-400">
                Cámara apagada
              </div>
            )}
          </div>
          {error && (
            <div className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700" role="alert">
              {error}
            </div>
          )}
          <div className="mt-4 flex items-center justify-center gap-2">
            <BotonControl
              activo={camActiva}
              onClick={alternarCamPrevia}
              etiquetaOn="Cámara on"
              etiquetaOff="Cámara off"
              IconoOn={Video}
              IconoOff={VideoOff}
            />
            <BotonControl
              activo={micActiva}
              onClick={alternarMicPrevia}
              etiquetaOn="Micro on"
              etiquetaOff="Micro off"
              IconoOn={Mic}
              IconoOff={MicOff}
            />
            <MenuOpcionesLlamada opciones={[opcionConfiguracion]} />
            <span className="mx-1 h-6 w-px bg-gray-700" />
            {botonSalir}
          </div>
        </div>
        {dialogoConfiguracion}
      </main>
    );
  }

  // ---------------------------------------------------------------- En sala

  interface Tile {
    id: TileId;
    etiqueta: string;
    videoRef: RefObject<HTMLVideoElement | null>;
    espejo?: boolean;
    contenidoVacio?: ReactNode;
  }

  const tiles: Tile[] = [
    {
      id: "camara-remota",
      etiqueta: remoteNombre ? etiquetaParticipante(remoteNombre, false) : "Participante",
      videoRef: remoteVideoRef,
      contenidoVacio: !remoteActivo ? (
        <span className="px-4 text-center text-sm text-gray-500">
          {enEspera
            ? `${enEspera.nombre || "Tu alumno"} está en la sala de espera`
            : estado === "conectado" || estado === "esperando" || estado === "reconectando"
              ? "Esperando al otro participante"
              : MENSAJES_ESTADO[estado]}
        </span>
      ) : undefined,
    },
    {
      id: "camara-local",
      etiqueta: "Vos",
      videoRef: localVideoRef,
      espejo: true,
      contenidoVacio:
        !camActiva || camApagadaPorDegradacion ? (
          <span className="px-2 text-center text-xs text-gray-400">
            {camApagadaPorDegradacion ? "Audio priorizado" : "Cámara apagada"}
          </span>
        ) : undefined,
    },
  ];
  if (compartiendoPantalla) {
    tiles.push({ id: "pantalla-local", etiqueta: "Tu pantalla", videoRef: localScreenVideoRef });
  }
  if (remoteCompartiendoPantalla) {
    tiles.push({
      id: "pantalla-remota",
      etiqueta: `Pantalla de ${etiquetaParticipante(remoteNombre, false)}`,
      videoRef: remoteScreenVideoRef,
    });
  }
  const puedeColaborar = hayOtro && (esTutor ? otroAdmitido : admitido);
  // Con la pizarra abierta, la pizarra ocupa el escenario y todos los videos pasan a miniatura.
  const tileDestacadaActual = pizarraAbierta ? null : (tiles.find((t) => t.id === tileDestacada) ?? tiles[0]);
  const miniaturas = tiles.filter((t) => t.id !== tileDestacadaActual?.id);

  return (
    <main className="flex min-h-screen flex-col p-0">
      <div className="flex min-h-screen flex-col bg-gray-900 text-gray-50">
        <header className="flex items-center justify-between bg-gray-800 px-5 py-3">
          <div className="text-lg font-bold text-gray-50">
            Tinku<span className="text-teal-700">.</span>
          </div>
          <div className="flex items-center gap-3">
            {camApagadaPorDegradacion && (
              <span className="text-xs text-amber-400">Priorizando el audio por tu conexión</span>
            )}
            {grabandoResumen && (
              <span className="flex items-center gap-1.5 text-xs text-red-300" title="Se graba solo el audio, para el resumen automático. Se borra al transcribirlo.">
                <span aria-hidden className="h-2 w-2 animate-pulse rounded-full bg-red-500" />
                Grabando solo audio para el resumen
              </span>
            )}
            {(estado === "conectado" || estado === "reconectando") && (
              <span
                className="flex items-center gap-1.5 text-xs text-gray-400"
                title={TEXTO_CALIDAD[calidad]}
              >
                <span aria-hidden className={`h-2 w-2 rounded-full ${COLOR_CALIDAD[calidad]}`} />
                <span className="sr-only">{TEXTO_CALIDAD[calidad]}</span>
              </span>
            )}
            <span className="text-xs capitalize text-gray-400">{MENSAJES_ESTADO[estado]}</span>
          </div>
        </header>

        <div className="relative flex min-h-0 flex-1 bg-black">
          {tiles.map((t) => {
            const esDestacada = t.id === tileDestacadaActual?.id;
            const indiceMini = esDestacada ? -1 : miniaturas.findIndex((m) => m.id === t.id);
            const contenido = (
              <>
                <video
                  ref={(el) => {
                    t.videoRef.current = el;
                    const pista = pistasRef.current[t.id];
                    if (el && pista && !remotoOculto(t.id)) pista.attach(el);
                  }}
                  autoPlay
                  playsInline
                  muted={t.id === "camara-local" || t.id === "pantalla-local"}
                  className={`block h-full w-full ${esDestacada ? "object-contain" : "object-cover"} ${t.espejo ? "-scale-x-100" : ""}`}
                />
                {remotoOculto(t.id) && (
                  <div
                    role="status"
                    className="absolute inset-0 flex items-center justify-center bg-gray-900 px-3 text-center text-sm text-gray-200"
                  >
                    {sidPorTileRef.current[t.id] && ocultosRef.current.has(sidPorTileRef.current[t.id]!)
                      ? "Video oculto por seguridad"
                      : "Reactivando la protección…"}
                  </div>
                )}
                {t.contenidoVacio && (
                  <div
                    className={`absolute inset-0 flex items-center justify-center ${esDestacada ? "" : "bg-gray-800/95"}`}
                  >
                    {t.contenidoVacio}
                  </div>
                )}
                <span
                  className={`absolute bottom-1 left-1 rounded bg-black/60 px-1.5 py-0.5 text-gray-100 ${esDestacada ? "text-xs" : "text-[10px]"}`}
                >
                  {t.etiqueta}
                </span>
              </>
            );

            if (esDestacada) {
              return (
                <div key={t.id} className="absolute inset-0 flex items-center justify-center">
                  {contenido}
                </div>
              );
            }

            return (
              <button
                key={t.id}
                type="button"
                onClick={() => {
                  setTileDestacada(t.id);
                  setPizarraAbierta(false);
                }}
                aria-label={`Ver en grande: ${t.etiqueta}`}
                className={`absolute bottom-4 z-10 cursor-pointer overflow-hidden rounded-lg border-2 border-gray-700 bg-black transition-[right] hover:border-gray-500 ${pizarraAbierta ? "w-[96px] sm:w-[140px]" : "w-[140px] sm:w-[180px]"}`}
                style={{ right: `${16 + indiceMini * (pizarraAbierta ? 108 : 196)}px` }}
              >
                {contenido}
              </button>
            );
          })}
          {pizarraAbierta && pizarraHabilitada && (
            <Pizarra trazos={trazos} onMensaje={accionPizarra} onCerrar={() => setPizarraAbierta(false)} />
          )}
          <audio
            ref={(el) => {
              remoteAudioRef.current = el;
              if (el && remoteAudioTrackRef.current) remoteAudioTrackRef.current.attach(el);
            }}
            autoPlay
          />

          {modoTexto && (
            <div
              className="absolute inset-x-4 bottom-4 flex max-h-64 flex-col gap-2 rounded-lg border border-red-800 bg-gray-900/95 p-3"
              role="status"
            >
              <p className="text-xs font-semibold text-red-400">
                Se perdió la conexión de audio y video — mientras se restablece, escribí acá.
              </p>
              {mensajesTexto.length > 0 && (
                <ul className="flex max-h-24 flex-col gap-1 overflow-y-auto text-sm">
                  {mensajesTexto.map((m, i) => (
                    // ponytail: lista corta y efímera del canal de emergencia — índice como key está bien
                    // eslint-disable-next-line react/no-array-index-key
                    <li key={i} className={m.propio ? "self-end text-teal-300" : "self-start text-gray-200"}>
                      {m.texto}
                    </li>
                  ))}
                </ul>
              )}
              {avisoFiltro && (
                <p className="text-xs text-amber-300" data-testid="aviso-filtro-chat">
                  {avisoFiltro}
                </p>
              )}
              <form onSubmit={enviarMensajeTexto} className="flex gap-2">
                <input
                  type="text"
                  value={textoAEnviar}
                  onChange={(e) => setTextoAEnviar(e.target.value)}
                  placeholder="Escribí un mensaje…"
                  maxLength={280}
                  className="flex-1 rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-gray-50"
                />
                <button
                  type="submit"
                  className="cursor-pointer rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white enabled:hover:bg-teal-800"
                  disabled={!textoAEnviar.trim()}
                >
                  Enviar
                </button>
              </form>
            </div>
          )}
        </div>

        {avisoKillswitch && (
          <div
            className="mx-4 my-2 self-center rounded-lg border border-amber-700 bg-amber-950/40 px-3.5 py-2 text-sm text-amber-200"
            role="status"
          >
            {avisoKillswitch}
          </div>
        )}
        <DialogoAdmitir
          abierto={enEspera !== null && !admisionPospuesta && pregunta === null}
          nombre={enEspera?.nombre ?? ""}
          onAdmitir={admitirAlumno}
          onPosponer={() => setAdmisionPospuesta(true)}
        />
        {dialogoConfiguracion}
        <DialogoRamaAdultos
          abierto={pregunta !== null}
          nombre={remoteNombre}
          venceEn={pregunta?.venceEn ?? 0}
          onResponder={(vio) => void orquestadorRef.current?.responder(vio)}
          onVencio={() => orquestadorRef.current?.vencioPregunta()}
        />

        {error && (
          <div
            className="mx-4 my-2 self-center rounded-lg border border-red-200 bg-red-50 px-3.5 py-3 text-sm text-red-700"
            role="alert"
          >
            {error}
          </div>
        )}

        <div className="flex items-center justify-center gap-3 bg-gray-800 p-4">
          {(estado === "conectado" || estado === "esperando" || estado === "reconectando") && (
            <div className="flex items-center gap-2">
              <BotonControl
                activo={camActiva}
                onClick={alternarCam}
                etiquetaOn="Cámara on"
                etiquetaOff="Cámara off"
                IconoOn={Video}
                IconoOff={VideoOff}
              />
              <BotonControl
                activo={micActiva}
                onClick={alternarMic}
                etiquetaOn="Micro on"
                etiquetaOff="Micro off"
                IconoOn={Mic}
                IconoOff={MicOff}
              />
              <MenuOpcionesLlamada
                opciones={[
                  // Pantalla y pizarra, solo con los dos adentro de la clase (no en la espera).
                  ...(puedeColaborar || compartiendoPantalla
                    ? [
                        {
                          id: "compartir-pantalla",
                          label: compartiendoPantalla ? "Dejar de compartir" : "Compartir pantalla",
                          Icono: compartiendoPantalla ? ScreenShareOff : ScreenShare,
                          onClick: alternarCompartirPantalla,
                        },
                      ]
                    : []),
                  ...(pizarraHabilitada && (puedeColaborar || pizarraAbierta)
                    ? [
                        {
                          id: "pizarra",
                          label: pizarraAbierta ? "Cerrar pizarra" : "Abrir pizarra",
                          Icono: PenLine,
                          onClick: alternarPizarra,
                        },
                      ]
                    : []),
                  opcionConfiguracion,
                ]}
              />
              <span className="mx-1 h-6 w-px bg-gray-700" />
            </div>
          )}

          {enEspera && admisionPospuesta && (
            <button
              type="button"
              className="flex cursor-pointer items-center gap-2 rounded-full bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
              onClick={admitirAlumno}
            >
              <UserPlus className="h-4 w-4" aria-hidden />
              Dejar entrar a {enEspera.nombre || "tu alumno"}
            </button>
          )}

          {estado === "sala_no_disponible" && (
            <button
              type="button"
              className="cursor-pointer rounded-lg bg-teal-700 px-4 py-2.5 font-semibold text-white enabled:hover:bg-teal-800"
              onClick={conectar}
            >
              Volver a intentar
            </button>
          )}

          {(estado === "conectado" ||
            estado === "esperando" ||
            estado === "reconectando" ||
            estado === "sala_no_disponible") && (
            <button
              type="button"
              className="cursor-pointer rounded-full bg-red-600 px-5 py-2.5 text-sm font-semibold text-white enabled:hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
              onClick={() => setConfirmarFin(true)}
              disabled={finalizando}
            >
              {finalizando ? "Finalizando…" : "Finalizar clase"}
            </button>
          )}
          {estado !== "finalizada" && botonSalir}
          <ModalConfirmacion
            abierto={confirmarFin}
            onCerrar={() => setConfirmarFin(false)}
            onConfirmar={async () => {
              setConfirmarFin(false);
              await finalizar();
            }}
            cargando={finalizando}
            titulo="¿Finalizar la clase?"
            textoConfirmar="Finalizar clase"
            textoCancelar="Seguir en la clase"
          >
            Termina la clase para los dos y no se puede volver a abrir.
          </ModalConfirmacion>
        </div>
      </div>
    </main>
  );
}
