"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { formatearPrecio } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";

interface TutorPerfil {
  id: string;
  nombre: string;
  apellido: string;
  tipo: string;
  capacidadEstudiante: boolean;
  capacidadAdultoResponsable: boolean;
  materias: string[];
  nivel: string;
  calificacionPromedio: number | null;
  cantidadCalificaciones: number;
  precioHora?: number | null;
}

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

export default function TutorPerfilPage({ params }: { params: { id: string } }) {
  const session = getSession();
  const payload = session?.payload;

  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [noConfiable, setNoConfiable] = useState(false);
  const [enviandoNoConfiable, setEnviandoNoConfiable] = useState(false);
  const [mensajeNoConfiable, setMensajeNoConfiable] = useState<string | null>(null);

  function cargar() {
    setCargando(true);
    setError(null);
    api
      .get<TutorPerfil>(`/api/tutores/${params.id}`)
      .then((p) => setPerfil(p))
      .catch((err) => {
        if (err instanceof ApiError) {
          setError(
            err.status === 404
              ? "Tutor no encontrado."
              : err.message || "No se pudo cargar el perfil."
          );
        } else {
          setError("No se pudo cargar el perfil.");
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.id]);

  const esMenor = payload?.tipo === "MENOR";
  const puedeReservar = !esMenor;
  const esAdultoConAR = !esMenor && payload?.cap_ar === true;

  async function toggleNoConfiable(nuevoValor: boolean) {
    setEnviandoNoConfiable(true);
    setMensajeNoConfiable(null);
    setError(null);
    try {
      await api.patch("/api/autorizaciones/no-confiable", {
        tutorId: params.id,
        noConfiable: nuevoValor,
      });
      setNoConfiable(nuevoValor);
      setMensajeNoConfiable(
        nuevoValor
          ? "Tutor marcado como no confiable. Ya no aparece en los resultados de matching de tu cuenta."
          : "Tutor desmarcado como no confiable."
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message || "No se pudo actualizar el estado de confianza.");
      } else {
        setError("No se pudo actualizar el estado de confianza.");
      }
    } finally {
      setEnviandoNoConfiable(false);
    }
  }

  return (
    <>
      <Cabecera enlaces={[{ href: "/buscar", label: "Buscar" }]} />

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        {cargando && (
          <p className="text-texto-suave">Cargando perfil...</p>
        )}

        {error && !cargando && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
            {error}
            <button
              type="button"
              className="mt-3 block cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
              onClick={cargar}
            >
              Reintentar
            </button>
          </div>
        )}

        {perfil && (
          <div>
            <div className="mb-4 w-full max-w-none rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
              <h1 className="mb-2 text-[1.6rem]">
                {perfil.nombre} {perfil.apellido}
              </h1>

              {perfil.materias.length > 0 && (
                <div className="mb-4">
                  {perfil.materias.map((m) => (
                    <span
                      key={m}
                      className="mb-[0.35rem] mr-[0.35rem] inline-block rounded-full bg-teal-50 px-2.5 py-1 text-[0.8rem] font-semibold text-accent"
                    >
                      {m}
                    </span>
                  ))}
                </div>
              )}

              <dl className="m-0">
                {perfil.nivel && (
                  <div className="flex justify-between gap-4 border-b border-borde py-3">
                    <dt className="font-semibold">Nivel</dt>
                    <dd className="m-0 text-right">{perfil.nivel}</dd>
                  </div>
                )}
                {typeof perfil.precioHora === "number" && (
                  <div className="flex justify-between gap-4 border-b border-borde py-3">
                    <dt className="font-semibold">Precio por hora</dt>
                    <dd className="m-0 text-right">
                      {formatearPrecio(perfil.precioHora)}
                    </dd>
                  </div>
                )}
                <div className="flex justify-between gap-4 border-b border-borde py-3">
                  <dt className="font-semibold">Calificacion</dt>
                  <dd className="m-0 text-right">
                    {perfil.calificacionPromedio !== null &&
                    perfil.cantidadCalificaciones >= 5
                      ? `${perfil.calificacionPromedio.toFixed(1)} (${perfil.cantidadCalificaciones})`
                      : "Sin calificaciones suficientes"}
                  </dd>
                </div>
              </dl>
            </div>

            {puedeReservar ? (
              <Link
                href={`/reservar?tutor=${perfil.id}`}
                className="inline-block cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover"
              >
                Reservar clase
              </Link>
            ) : (
              <div className="w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
                Pedile a tu adulto responsable que te autorice a esta tutora/o.
              </div>
            )}

            {esAdultoConAR && (
              <div className="mt-6">
                <h2 className="mb-3 text-[1.1rem]">
                  Autorizacion
                </h2>

                <div className="mb-4">
                  <button
                    type="button"
                    className="cursor-not-allowed rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white opacity-60"
                    onClick={() => {}}
                    disabled
                  >
                    Autorizar para mi menor
                  </button>
                  <div
                    className="mt-2 w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso"
                    role="status"
                  >
                    El listado de tus menores esta pendiente en backend. Cuando
                    este disponible, vas a poder autorizar tutores para cada
                    menor.
                  </div>
                </div>

                <div
                  className="mb-2 flex items-center gap-3"
                >
                  <label
                    htmlFor="no-confiable"
                    className="flex cursor-pointer items-center gap-2"
                    style={{ cursor: enviandoNoConfiable ? "not-allowed" : "pointer" }}
                  >
                    <input
                      id="no-confiable"
                      type="checkbox"
                      className="h-[1.1rem] w-[1.1rem] accent-accent disabled:cursor-not-allowed"
                      checked={noConfiable}
                      disabled={enviandoNoConfiable}
                      onChange={(e) => toggleNoConfiable(e.target.checked)}
                    />
                    Marcar como no confiable
                  </label>
                </div>
                <p
                  className="mb-2 text-[0.85rem] text-texto-suave"
                >
                  Sacarlo de tus resultados de busqueda.
                </p>

                {mensajeNoConfiable && (
                  <div
                    className="mt-2 w-fit rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito"
                    role="status"
                  >
                    {mensajeNoConfiable}
                  </div>
                )}
              </div>
            )}

            {payload && (
              <p className="text-[0.8rem] text-texto-suave">
                Sesion de {NOMBRE_TIPO[payload.tipo ?? ""] ?? payload.tipo ?? "usuario"}
              </p>
            )}
          </div>
        )}
      </main>
    </>
  );
}