"use client";

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ComponentType,
  type ReactNode,
  type RefObject,
} from "react";
import { useRouter } from "next/navigation";
import {
  ConnectionQuality,
  createLocalAudioTrack,
  createLocalVideoTrack,
  LocalAudioTrack,
  LocalVideoTrack,
  Room,
  RoomEvent,
  Track,
} from "livekit-client";
import { Mic, MicOff, MoreVertical, ScreenShare, ScreenShareOff, Video, VideoOff } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import Logo from "@/components/Logo";
import { ModalConfirmacion } from "@/components/ui";

interface TokenResponse {
  token: string;
  livekitUrl: string;
  livekitRoomId: string;
}

type Estado =
  | "previa"
  | "conectando"
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

interface Dispositivo {
  deviceId: string;
  label: string;
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

export default function AulaPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const sesionId = params.id;

  const roomRef = useRef<Room | null>(null);
  const roomConectadoRef = useRef(false);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const remoteAudioRef = useRef<HTMLAudioElement>(null);
  const localScreenVideoRef = useRef<HTMLVideoElement>(null);
  const remoteScreenVideoRef = useRef<HTMLVideoElement>(null);
  const previaVideoTrackRef = useRef<LocalVideoTrack | null>(null);
  const previaAudioTrackRef = useRef<LocalAudioTrack | null>(null);

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

  // ---- FR-AULA-002: degradación automática video → audio → texto ----
  const [camApagadaPorDegradacion, setCamApagadaPorDegradacion] = useState(false);
  const [modoTexto, setModoTexto] = useState(false);
  const [mensajesTexto, setMensajesTexto] = useState<MensajeTexto[]>([]);
  const [textoAEnviar, setTextoAEnviar] = useState("");

  // ---- Pantalla previa (lobby): preview + elección de dispositivo ----
  const [camaras, setCamaras] = useState<Dispositivo[]>([]);
  const [microfonos, setMicrofonos] = useState<Dispositivo[]>([]);
  const [camaraId, setCamaraId] = useState<string | undefined>(undefined);
  const [microfonoId, setMicrofonoId] = useState<string | undefined>(undefined);
  const [errorCam, setErrorCam] = useState<string | null>(null);
  const [errorMic, setErrorMic] = useState<string | null>(null);
  const [previaLista, setPreviaLista] = useState(false);

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

  // ---- Conexión real a la sala (recién al confirmar desde la previa) ----
  const conectar = useCallback(async () => {
    // La cámara/mic de la previa quedan libres antes de que la Room adquiera
    // los suyos — sostener dos handles del mismo dispositivo a la vez es lo
    // que en algunos navegadores dispara NotReadableError.
    previaVideoTrackRef.current?.stop();
    previaAudioTrackRef.current?.stop();
    previaVideoTrackRef.current = null;
    previaAudioTrackRef.current = null;

    setError(null);
    setEstado("conectando");

    try {
      const tokenResp = await api.post<TokenResponse>(
        `/api/sesiones/${sesionId}/token`
      );

      const room = new Room({
        adaptiveStream: true,
        dynacast: true,
      });

      room.on(RoomEvent.Connected, () => {
        roomConectadoRef.current = true;
        setEstado("conectado");
        room.localParticipant.setCameraEnabled(camActivaRef.current, {
          deviceId: camaraId,
        });
        room.localParticipant.setMicrophoneEnabled(micActivaRef.current, {
          deviceId: microfonoId,
        });
      });

      room.on(RoomEvent.ParticipantConnected, (participante) => {
        setEstado("conectado");
        setRemoteNombre(participante.name ?? "");
      });

      room.on(RoomEvent.ParticipantDisconnected, () => {
        setEstado("esperando");
        setRemoteActivo(false);
        setRemoteNombre("");
      });

      room.on(RoomEvent.LocalTrackPublished, (pub) => {
        if (
          pub.source === Track.Source.Camera &&
          pub.track?.kind === Track.Kind.Video &&
          localVideoRef.current
        ) {
          pub.track.attach(localVideoRef.current);
        }
        if (
          pub.source === Track.Source.ScreenShare &&
          pub.track?.kind === Track.Kind.Video &&
          localScreenVideoRef.current
        ) {
          pub.track.attach(localScreenVideoRef.current);
          setCompartiendoPantalla(true);
          setTileDestacada("pantalla-local");
        }
      });

      room.on(RoomEvent.LocalTrackUnpublished, (pub) => {
        if (pub.source === Track.Source.ScreenShare) {
          setCompartiendoPantalla(false);
          setTileDestacada((actual) => (actual === "pantalla-local" ? "camara-remota" : actual));
        }
      });

      room.on(RoomEvent.TrackSubscribed, (track, pub, participante) => {
        if (track.kind === Track.Kind.Video && pub.source === Track.Source.ScreenShare) {
          if (remoteScreenVideoRef.current) track.attach(remoteScreenVideoRef.current);
          setRemoteCompartiendoPantalla(true);
          setRemoteNombre(participante.name ?? "");
          setTileDestacada("pantalla-remota");
        } else if (track.kind === Track.Kind.Video && remoteVideoRef.current) {
          track.attach(remoteVideoRef.current);
          setRemoteActivo(true);
          setRemoteNombre(participante.name ?? "");
        } else if (track.kind === Track.Kind.Audio && remoteAudioRef.current) {
          track.attach(remoteAudioRef.current);
        }
      });

      room.on(RoomEvent.TrackUnsubscribed, (track, pub) => {
        track.detach();
        if (track.kind === Track.Kind.Video) {
          if (pub.source === Track.Source.ScreenShare) {
            setRemoteCompartiendoPantalla(false);
            setTileDestacada((actual) => (actual === "pantalla-remota" ? "camara-remota" : actual));
          } else {
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
      room.on(RoomEvent.DataReceived, (payload) => {
        try {
          const data = JSON.parse(new TextDecoder().decode(payload)) as {
            tipo?: string;
            texto?: string;
          };
          if (data.tipo === "mensaje" && typeof data.texto === "string") {
            setMensajesTexto((prev) => [...prev.slice(-9), { propio: false, texto: data.texto! }]);
          }
        } catch {
          // Paquete que no es del formato esperado — se ignora.
        }
      });

      room.on(RoomEvent.Reconnecting, () => {
        setEstado("reconectando");
      });

      room.on(RoomEvent.Reconnected, () => {
        setEstado(roomConectadoRef.current ? "conectado" : "esperando");
      });

      room.on(RoomEvent.Disconnected, () => {
        roomConectadoRef.current = false;
        setRemoteActivo(false);
        if (estadoRef.current !== "finalizada") {
          setEstado("esperando");
        }
      });

      roomRef.current = room;
      await room.connect(tokenResp.livekitUrl, tokenResp.token);

      if (room.localParticipant) {
        const camPub = room.localParticipant.getTrackPublication(
          Track.Source.Camera
        );
        if (camPub?.videoTrack && localVideoRef.current) {
          camPub.videoTrack.attach(localVideoRef.current);
        }
      }

      setEstado("conectado");
    } catch (err) {
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
      if (estadoRef.current !== "sala_no_disponible") setEstado("error");
    }
  }, [sesionId, camaraId, microfonoId, evaluarDegradacion]);

  useEffect(() => {
    return () => {
      roomConectadoRef.current = false;
      roomRef.current?.disconnect();
      roomRef.current = null;
    };
  }, []);

  async function finalizar() {
    setFinalizando(true);
    setError(null);
    try {
      await api.post(`/api/sesiones/${sesionId}/finalizar`);
      roomRef.current?.disconnect();
      roomRef.current = null;
      setEstado("finalizada");
      router.replace("/cuenta/reservas");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo finalizar la sesión.");
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

  async function alternarCompartirPantalla() {
    if (!roomRef.current) return;
    try {
      await roomRef.current.localParticipant.setScreenShareEnabled(!compartiendoPantalla);
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

  function enviarMensajeTexto(e: React.FormEvent) {
    e.preventDefault();
    const texto = textoAEnviar.trim();
    if (!texto || !roomRef.current) return;
    const payload = new TextEncoder().encode(JSON.stringify({ tipo: "mensaje", texto }));
    roomRef.current.localParticipant.publishData(payload, { reliable: true });
    setMensajesTexto((prev) => [...prev.slice(-9), { propio: true, texto }]);
    setTextoAEnviar("");
  }

  // ---------------------------------------------------------------- Previa
  if (estado === "previa") {
    const bloqueadoTotal = errorCam && errorMic;

    return (
      <main className="flex min-h-screen flex-col items-center justify-center bg-gray-900 px-4 py-8 text-gray-50">
        <div className="w-full max-w-md">
          <div className="mb-6 flex justify-center">
            <Logo href="/cuenta/reservas" claro />
          </div>
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
            disabled={!previaLista || Boolean(bloqueadoTotal)}
          >
            {bloqueadoTotal ? "Revisá los permisos para continuar" : "Unirme a la clase"}
          </button>
          <p className="mt-4 text-center text-[13px] text-gray-400">
            La clase se hace dentro de Tinku. No compartas teléfonos, emails ni redes.
          </p>
        </div>
      </main>
    );
  }

  // ---------------------------------------------------------------- En sala

  interface Tile {
    id: TileId;
    etiqueta: string;
    videoRef: RefObject<HTMLVideoElement>;
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
          {estado === "conectado" || estado === "esperando" || estado === "reconectando"
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
  const tileDestacadaActual = tiles.find((t) => t.id === tileDestacada) ?? tiles[0];
  const miniaturas = tiles.filter((t) => t.id !== tileDestacadaActual.id);

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
            const esDestacada = t.id === tileDestacadaActual.id;
            const indiceMini = esDestacada ? -1 : miniaturas.findIndex((m) => m.id === t.id);
            const contenido = (
              <>
                <video
                  ref={t.videoRef}
                  autoPlay
                  playsInline
                  muted={t.id === "camara-local" || t.id === "pantalla-local"}
                  className={`block h-full w-full ${esDestacada ? "object-contain" : "object-cover"} ${t.espejo ? "-scale-x-100" : ""}`}
                />
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
                onClick={() => setTileDestacada(t.id)}
                aria-label={`Ver en grande: ${t.etiqueta}`}
                className="absolute bottom-4 w-[140px] cursor-pointer overflow-hidden rounded-lg border-2 border-gray-700 bg-black transition-[right] hover:border-gray-500 sm:w-[180px]"
                style={{ right: `${16 + indiceMini * 196}px` }}
              >
                {contenido}
              </button>
            );
          })}
          <audio ref={remoteAudioRef} autoPlay />

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
                  {
                    id: "compartir-pantalla",
                    label: compartiendoPantalla ? "Dejar de compartir" : "Compartir pantalla",
                    Icono: compartiendoPantalla ? ScreenShareOff : ScreenShare,
                    onClick: alternarCompartirPantalla,
                  },
                ]}
              />
              <span className="mx-1 h-6 w-px bg-gray-700" />
            </div>
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
