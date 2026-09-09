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

export default function AulaPage({
  params,
}: {
  params: { id: string };
}) {
  const router = useRouter();
  const sesionId = params.id;

  const roomRef = useRef<Room | null>(null);
  const localVideoRef = useRef<HTMLVideoElement>(null);
  const remoteVideoRef = useRef<HTMLVideoElement>(null);
  const remoteAudioRef = useRef<HTMLAudioElement>(null);

  const [estado, setEstado] = useState<string>("conectando");
  const [error, setError] = useState<string | null>(null);
  const [finalizando, setFinalizando] = useState(false);
  const [remoteActivo, setRemoteActivo] = useState(false);

  const conectar = useCallback(async () => {
    try {
      const tokenResp = await api.post<TokenResponse>(
        `/api/sesiones/${sesionId}/token`
      );

      const room = new Room({
        adaptiveStream: true,
        dynacast: true,
      });

      room.on(RoomEvent.Connected, () => {
        setEstado("conectado");
        room.localParticipant.setCameraEnabled(true);
        room.localParticipant.setMicrophoneEnabled(true);
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
        setEstado("desconectado");
        setRemoteActivo(false);
      });

      room.on(RoomEvent.ParticipantDisconnected, () => {
        setEstado("esperando");
        setRemoteActivo(false);
      });

      await room.connect(tokenResp.livekitUrl, tokenResp.token);

      if (room.localParticipant) {
        const camPub = room.localParticipant.getTrackPublication(Track.Source.Camera);
        if (camPub?.videoTrack && localVideoRef.current) {
          camPub.videoTrack.attach(localVideoRef.current);
        }
      }

      roomRef.current = room;
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.status === 404
          ? "Sesión no encontrada."
          : err.status === 403
            ? "No sos participante de esta sesión."
            : err.message);
      } else {
        setError("No se pudo conectar a la sala.");
      }
      setEstado("error");
    }
  }, [sesionId]);

  useEffect(() => {
    conectar();
    return () => {
      roomRef.current?.disconnect();
      roomRef.current = null;
    };
  }, [conectar]);

  async function finalizar() {
    setFinalizando(true);
    try {
      await api.post(`/api/sesiones/${sesionId}/finalizar`);
      roomRef.current?.disconnect();
      router.replace("/cuenta");
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo finalizar la sesión.");
      }
      setFinalizando(false);
    }
  }

  return (
    <main className="pantalla" style={{ padding: 0 }}>
      <div className="aula">
        <header className="aula-cabecera">
          <div className="marca" style={{ marginBottom: 0 }}>
            Tinku<span>.</span>
          </div>
          <span className="aula-estado">{estado}</span>
        </header>

        <div className="aula-video">
          <div className="aula-video-remoto">
            <video ref={remoteVideoRef} autoPlay playsInline />
            {!remoteActivo && (
              <div className="aula-esperando">
                {estado === "conectado" || estado === "esperando"
                  ? "Esperando al otro participante…"
                  : estado}
              </div>
            )}
          </div>
          <div className="aula-video-local">
            <video ref={localVideoRef} autoPlay playsInline muted />
          </div>
          <audio ref={remoteAudioRef} autoPlay />
        </div>

        {error && (
          <div className="alerta alerta--error" role="alert" style={{ margin: "1rem" }}>
            {error}
          </div>
        )}

        <div className="aula-controles">
          <button
            type="button"
            className="boton"
            onClick={finalizar}
            disabled={finalizando || estado === "finalizada"}
          >
            {finalizando ? "Finalizando…" : "Finalizar sesión"}
          </button>
        </div>
      </div>
    </main>
  );
}
