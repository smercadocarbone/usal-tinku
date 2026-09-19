"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Room, RoomEvent, Track } from "livekit-client";
import { api, ApiError } from "@/lib/api";

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

  const estadoRef = useRef<Estado>(estado);
  const camActivaRef = useRef(camActiva);
  const micActivaRef = useRef(micActiva);
  useEffect(() => {
    estadoRef.current = estado;
    camActivaRef.current = camActiva;
    micActivaRef.current = micActiva;
  });

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
    <main className="flex min-h-screen flex-col p-0">
      <div className="flex min-h-screen flex-col bg-gray-900 text-gray-50">
        <header className="flex items-center justify-between bg-gray-800 px-5 py-3">
          <div className="text-lg font-bold text-gray-50">
            Tinku<span className="text-teal-700">.</span>
          </div>
          <span className="text-xs capitalize text-gray-400">{MENSAJES_ESTADO[estado]}</span>
        </header>

        <div className="relative flex min-h-0 flex-1">
          <div className="relative flex flex-1 items-center justify-center bg-black">
            <video ref={remoteVideoRef} autoPlay playsInline className="h-full w-full object-contain" />
            {!remoteActivo && (
              <div className="text-sm text-gray-500">
                {estado === "conectado" || estado === "esperando"
                  ? "Esperando al otro participante"
                  : MENSAJES_ESTADO[estado]}
              </div>
            )}
          </div>
          <div className="absolute bottom-4 right-4 w-[180px] overflow-hidden rounded-lg border-2 border-gray-700">
            <video ref={localVideoRef} autoPlay playsInline muted className="block w-full" />
          </div>
          <audio ref={remoteAudioRef} autoPlay />
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
          {(estado === "conectado" || estado === "esperando") && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                className={`rounded-full border border-gray-700 bg-gray-900 px-4 py-2 text-sm font-semibold text-gray-50 ${camActiva ? "" : "opacity-55"} cursor-pointer hover:border-gray-500`}
                onClick={alternarCam}
                aria-label={camActiva ? "Apagar camara" : "Prender camara"}
                title={camActiva ? "Apagar camara" : "Prender camara"}
              >
                {camActiva ? "Camara on" : "Camara off"}
              </button>
              <button
                type="button"
                className={`rounded-full border border-gray-700 bg-gray-900 px-4 py-2 text-sm font-semibold text-gray-50 ${micActiva ? "" : "opacity-55"} cursor-pointer hover:border-gray-500`}
                onClick={alternarMic}
                aria-label={micActiva ? "Silenciar" : "Activar microfono"}
                title={micActiva ? "Silenciar" : "Activar microfono"}
              >
                {micActiva ? "Micro on" : "Micro off"}
              </button>
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
            estado === "sala_no_disponible") && (
            <button
              type="button"
              className="cursor-pointer rounded-full bg-red-600 px-5 py-2.5 text-sm font-semibold text-white enabled:hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
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