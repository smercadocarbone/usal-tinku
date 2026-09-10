"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Room, RoomEvent, Track } from "livekit-client";
import { api, ApiError } from "@/lib/api";
import { clearSession } from "@/lib/auth";

interface TokenResponse {
  token: string;
  livekitUrl: string;
  livekitRoomId: string;
}

type Estado = "conectando" | "esperando" | "conectado" | "sala_no_disponible" | "error" | "finalizada";

const MENSAJES_ESTADO: Record<Estado, string> = {
  conectando: "Conectando...",
  esperando: "Esperando al otro participante",
  conectado: "En la sala",
  sala_no_disponible: "Sala no disponible",
  error: "Error de conexion",
  finalizada: "Sesion finalizada",
};

export default function AulaPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const sesionId = params.id;

  const roomRef = useRef<Room | null>(null);
  const roomConectadoRef = useRef(false);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const remoteAudioRef = useRef<HTMLAudioElement>(null);

  const [estado, setEstado] = useState<Estado>("conectando");
  const [error, setError] = useState<string | null>(null);
  const [finalizando, setFinalizando] = useState(false);
  const [remoteActivo, setRemoteActivo] = useState(false);
  const [camActiva, setCamActiva] = useState(true);
  const [micActiva, setMicActiva] = useState(true);

  /** Refs espejo del estado para usarlos dentro de callbacks de eventos del
   * Room (que cierran sobre el render de creación) sin recrear la conexión. */
  const estadoRef = useRef<Estado>(estado);
  estadoRef.current = estado;
  const camActivaRef = useRef(camActiva);
  camActivaRef.current = camActiva;
  const micActivaRef = useRef(micActiva);
  micActivaRef.current = micActiva;

  /** Pensar la conexión como una operación: crea el room, registra listeners y
   * recién después conecta. roomRef se setea ANTES del connect para que el
   * cleanup del unmount pueda desconectar siempre, aunque el connect falle. */
  const conectar = useCallback(async () => {
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
        room.localParticipant.setCameraEnabled(camActivaRef.current);
        room.localParticipant.setMicrophoneEnabled(micActivaRef.current);
      });

      room.on(RoomEvent.ParticipantConnected, () => {
        setEstado("conectado");
      });

      room.on(RoomEvent.ParticipantDisconnected, () => {
        setEstado("esperando");
        setRemoteActivo(false);
      });

      room.on(RoomEvent.LocalTrackPublished, (pub) => {
        if (
          pub.source === Track.Source.Camera &&
          pub.track?.kind === Track.Kind.Video &&
          localVideoRef.current
        ) {
          pub.track.attach(localVideoRef.current);
        }
      });

      room.on(RoomEvent.TrackSubscribed, (track) => {
        if (track.kind === Track.Kind.Video && remoteVideoRef.current) {
          track.attach(remoteVideoRef.current);
          setRemoteActivo(true);
        } else if (track.kind === Track.Kind.Audio && remoteAudioRef.current) {
          track.attach(remoteAudioRef.current);
        }
      });

      room.on(RoomEvent.TrackUnsubscribed, (track) => {
        track.detach();
        if (track.kind === Track.Kind.Video) {
          setRemoteActivo(false);
        }
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
          setError("Sesion no encontrada.");
        } else if (err.status === 403) {
          setError("No sos participante de esta sesion.");
        } else if (err.status === 422) {
          setEstado("sala_no_disponible");
          setError(
            "La sala se abre unos minutos antes de la clase. Volvi a intentarlo en un momento."
          );
        } else {
          setError(err.message || "No se pudo conectar a la sala.");
        }
      } else {
        setError("No se pudo conectar a la sala.");
      }
      if (estadoRef.current !== "sala_no_disponible") setEstado("error");
    }
  }, [sesionId]);

  useEffect(() => {
    conectar();
    return () => {
      roomConectadoRef.current = false;
      roomRef.current?.disconnect();
      roomRef.current = null;
    };
  }, [conectar]);

  async function finalizar() {
    setFinalizando(true);
    setError(null);
    try {
      await api.post(`/api/sesiones/${sesionId}/finalizar`);
      roomRef.current?.disconnect();
      roomRef.current = null;
      setEstado("finalizada");
      router.replace("/cuenta");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo finalizar la sesion.");
      }
      setFinalizando(false);
    }
  }

  function alternarCam() {
    const nueva = !camActiva;
    setCamActiva(nueva);
    roomRef.current?.localParticipant.setCameraEnabled(nueva);
  }

  function alternarMic() {
    const nueva = !micActiva;
    setMicActiva(nueva);
    roomRef.current?.localParticipant.setMicrophoneEnabled(nueva);
  }

  return (
    <main className="pantalla" style={{ padding: 0 }}>
      <div className="aula">
        <header className="aula-cabecera">
          <div className="marca" style={{ marginBottom: 0 }}>
            Tinku<span>.</span>
          </div>
          <span className="aula-estado">{MENSAJES_ESTADO[estado]}</span>
        </header>

        <div className="aula-video">
          <div className="aula-video-remoto">
            <video ref={remoteVideoRef} autoPlay playsInline />
            {!remoteActivo && (
              <div className="aula-esperando">
                {estado === "conectado" || estado === "esperando"
                  ? "Esperando al otro participante"
                  : MENSAJES_ESTADO[estado]}
              </div>
            )}
          </div>
          <div className="aula-video-local">
            <video ref={localVideoRef} autoPlay playsInline muted />
          </div>
          <audio ref={remoteAudioRef} autoPlay />
        </div>

        {error && (
          <div
            className="alerta alerta--error"
            role="alert"
            style={{ margin: "0.5rem 1rem", alignSelf: "center" }}
          >
            {error}
          </div>
        )}

        <div className="aula-controles">
          {(estado === "conectado" || estado === "esperando") && (
            <div className="aula-controles-grupo">
              <button
                type="button"
                className={`aula-tool ${camActiva ? "" : "aula-tool--apagado"}`}
                onClick={alternarCam}
                aria-label={camActiva ? "Apagar camara" : "Prender camara"}
                title={camActiva ? "Apagar camara" : "Prender camara"}
              >
                {camActiva ? "Camara on" : "Camara off"}
              </button>
              <button
                type="button"
                className={`aula-tool ${micActiva ? "" : "aula-tool--apagado"}`}
                onClick={alternarMic}
                aria-label={micActiva ? "Silenciar" : "Activar microfono"}
                title={micActiva ? "Silenciar" : "Activar microfono"}
              >
                {micActiva ? "Micro on" : "Micro off"}
              </button>
              <span className="aula-separador" />
            </div>
          )}

          {estado === "sala_no_disponible" && (
            <button type="button" className="boton" onClick={conectar}>
              Volver a intentar
            </button>
          )}

          {(estado === "conectado" ||
            estado === "esperando" ||
            estado === "sala_no_disponible") && (
            <button
              type="button"
              className="aula-finalizar"
              onClick={finalizar}
              disabled={finalizando}
            >
              {finalizando ? "Finalizando..." : "Finalizar sesion"}
            </button>
          )}
        </div>
      </div>
    </main>
  );
}