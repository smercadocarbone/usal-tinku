"use client";

import { useEffect, useState } from "react";
import { api, ApiError } from "./api";
import { TOKEN_KEY } from "./auth";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

/** Perfil público del Tutor (`GET /api/tutores/{id}`, UX-04 §2 / U1). */
export interface TutorPerfil {
  id: string;
  nombre: string;
  apellido: string;
  materias: string[];
  nivel: string | null;
  /** `null` si tiene menos de 5 calificaciones (FR-REP-007). */
  calificacionPromedio: number | null;
  cantidadCalificaciones: number;
  bio: string | null;
  tieneFoto: boolean;
  /** Tiene al menos una credencial académica aprobada. */
  verificado: boolean;
  /** Tarifa por clase que configuró el tutor; `null` = todavía no la definió. */
  precioSesion: number | null;
}

export function getTutor(id: string): Promise<TutorPerfil> {
  return api.get<TutorPerfil>(`/api/tutores/${id}`).then(normalizarTutor);
}

/** Tolera respuestas viejas (sin los campos de U1) sin romper la pantalla. */
export function normalizarTutor(t: Partial<TutorPerfil> & { id: string }): TutorPerfil {
  return {
    id: t.id,
    nombre: t.nombre ?? "",
    apellido: t.apellido ?? "",
    materias: t.materias ?? [],
    nivel: t.nivel ?? null,
    calificacionPromedio: t.calificacionPromedio ?? null,
    cantidadCalificaciones: t.cantidadCalificaciones ?? 0,
    bio: t.bio ?? null,
    tieneFoto: t.tieneFoto ?? false,
    verificado: t.verificado ?? false,
    precioSesion: t.precioSesion ?? null,
  };
}

/** "Valeria G." — nombre de pila + inicial: suficiente para reconocer, sin exponer de más. */
export function nombreCorto(nombre: string, apellido?: string | null): string {
  const inicial = apellido?.trim()?.[0];
  return inicial ? `${nombre} ${inicial}.` : nombre;
}

/*
 * La foto se sirve autenticada (U1: nunca una URL pública), así que un <img src>
 * directo no alcanza: se pide con el token y se muestra como blob. Cache por id
 * durante la vida de la página: la lista de resultados y el perfil la comparten.
 */
const cacheFotos = new Map<string, Promise<string | null>>();

export function invalidarFoto(tutorId: string) {
  cacheFotos.delete(tutorId);
}

export function useFotoTutor(tutorId: string | null | undefined, tieneFoto: boolean | undefined): string | null {
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!tutorId || !tieneFoto) {
      setUrl(null);
      return;
    }
    let vivo = true;
    if (!cacheFotos.has(tutorId)) {
      const token = window.localStorage.getItem(TOKEN_KEY);
      cacheFotos.set(
        tutorId,
        fetch(`${API_BASE_URL}/api/tutores/${tutorId}/foto`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
          .then((r) => (r.ok ? r.blob() : Promise.reject(new ApiError(r.status, r.statusText))))
          .then((b) => URL.createObjectURL(b))
          .catch(() => null)
      );
    }
    void cacheFotos.get(tutorId)!.then((u) => vivo && setUrl(u));
    return () => {
      vivo = false;
    };
  }, [tutorId, tieneFoto]);
  return url;
}
