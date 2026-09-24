"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { api, ApiError, autorizarTutor, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import { formatearPrecio } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";
import FormularioDenuncia from "@/components/FormularioDenuncia";
import {
  Alerta,
  Boton,
  CampoSelect,
  Cargando,
  EstadoVacio,
  Insignia,
  Tarjeta,
  clasesBoton,
} from "@/components/ui";

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
  const session = useSesion();
  const payload = session?.payload;

  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [noConfiable, setNoConfiable] = useState(false);
  const [enviandoNoConfiable, setEnviandoNoConfiable] = useState(false);
  const [mensajeNoConfiable, setMensajeNoConfiable] = useState<string | null>(null);

  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [errorMenores, setErrorMenores] = useState<string | null>(null);
  const [menorElegido, setMenorElegido] = useState("");
  const [autorizando, setAutorizando] = useState(false);
  const [mensajeAutorizacion, setMensajeAutorizacion] = useState<string | null>(null);

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

  useEffect(() => {
    if (!esAdultoConAR) return;
    getMenores()
      .then((lista) => {
        setMenores(lista);
        if (lista[0]) setMenorElegido(lista[0].id);
      })
      .catch((err) => setErrorMenores(mensajeDeError(err, "No se pudo cargar tu listado de menores.")));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [esAdultoConAR]);

  async function autorizar() {
    if (!menorElegido) return;
    setAutorizando(true);
    setMensajeAutorizacion(null);
    setError(null);
    try {
      await autorizarTutor(menorElegido, params.id);
      const nombreMenor = menores?.find((m) => m.id === menorElegido);
      setMensajeAutorizacion(
        nombreMenor
          ? `Autorizaste a este tutor para ${nombreMenor.nombre}.`
          : "Tutor autorizado."
      );
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo autorizar al tutor."));
    } finally {
      setAutorizando(false);
    }
  }

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
          ? "Tutor marcado como no confiable. Ya no aparece en los resultados de búsqueda de tu cuenta."
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

      <main className="mx-auto max-w-2xl px-5 py-8">
        {cargando && <Cargando>Cargando perfil...</Cargando>}

        {error && !cargando && (
          <Alerta tono="error">
            {error}
            <Boton
              variante="secundario"
              tamano="sm"
              className="mt-3 flex"
              onClick={cargar}
            >
              Reintentar
            </Boton>
          </Alerta>
        )}

        {perfil && (
          <div>
            <Tarjeta className="mb-4 w-full max-w-none p-8">
              <h1 className="mb-2 text-2xl">
                {perfil.nombre} {perfil.apellido}
              </h1>

              {perfil.materias.length > 0 && (
                <div className="mb-4">
                  {perfil.materias.map((m) => (
                    <Insignia
                      key={m}
                      tono="exito"
                      className="mb-1.5 mr-1.5 px-2.5"
                    >
                      {m}
                    </Insignia>
                  ))}
                </div>
              )}

              <dl className="m-0">
                {perfil.nivel && (
                  <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                    <dt className="font-semibold">Nivel</dt>
                    <dd className="m-0 text-right">{perfil.nivel}</dd>
                  </div>
                )}
                {typeof perfil.precioHora === "number" && (
                  <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                    <dt className="font-semibold">Precio por hora</dt>
                    <dd className="m-0 text-right">
                      {formatearPrecio(perfil.precioHora)}
                    </dd>
                  </div>
                )}
                <div className="flex justify-between gap-4 border-b border-slate-200 py-3">
                  <dt className="font-semibold">Calificación</dt>
                  <dd className="m-0 text-right">
                    {perfil.calificacionPromedio !== null &&
                    perfil.cantidadCalificaciones >= 5
                      ? `${perfil.calificacionPromedio.toFixed(1)} (${perfil.cantidadCalificaciones})`
                      : "Sin calificaciones suficientes"}
                  </dd>
                </div>
              </dl>
            </Tarjeta>

            <div className="flex flex-wrap items-center gap-3">
              {puedeReservar ? (
                <Link
                  href={`/reservar?tutor=${perfil.id}`}
                  className={clasesBoton("primario")}
                >
                  Reservar clase
                </Link>
              ) : (
                <Alerta tono="aviso" className="w-fit">
                  Las clases para menores se habilitan al finalizar el piloto.
                  Pedile a tu adulto responsable que te autorice a esta tutora/o.
                </Alerta>
              )}

              {!esMenor && <FormularioDenuncia denunciadoId={perfil.id} />}
            </div>

            {esAdultoConAR && (
              <div className="mt-6">
                <h2 className="mb-3 text-lg">
                  Autorizacion
                </h2>

                <div className="mb-4">
                  {errorMenores && <Alerta tono="error">{errorMenores}</Alerta>}

                  {!errorMenores && menores === null && (
                    <Cargando>Cargando tus menores…</Cargando>
                  )}

                  {menores !== null && menores.length === 0 && (
                    <EstadoVacio className="mx-0 max-w-none py-4 text-left">
                      Todavía no diste de alta a ningún menor. Podés hacerlo desde tu cuenta.
                    </EstadoVacio>
                  )}

                  {menores !== null && menores.length > 0 && (
                    <div className="flex flex-wrap items-end gap-3">
                      <CampoSelect
                        id="menorAAutorizar"
                        etiqueta="Menor"
                        etiquetaOculta
                        value={menorElegido}
                        onChange={(e) => setMenorElegido(e.target.value)}
                      >
                        {menores.map((m) => (
                          <option key={m.id} value={m.id}>
                            {m.nombre} {m.apellido}
                          </option>
                        ))}
                      </CampoSelect>
                      <Boton
                        tamano="sm"
                        cargando={autorizando}
                        textoCargando="Autorizando…"
                        onClick={autorizar}
                      >
                        Autorizar para este menor
                      </Boton>
                    </div>
                  )}

                  {mensajeAutorizacion && (
                    <Alerta tono="exito" className="mt-2 w-fit">
                      {mensajeAutorizacion}
                    </Alerta>
                  )}
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
                      className="h-[1.1rem] w-[1.1rem] accent-teal-600 disabled:cursor-not-allowed"
                      checked={noConfiable}
                      disabled={enviandoNoConfiable}
                      onChange={(e) => toggleNoConfiable(e.target.checked)}
                    />
                    Marcar como no confiable
                  </label>
                </div>
                <p
                  className="mb-2 text-sm text-slate-500"
                >
                  Sacarlo de tus resultados de busqueda.
                </p>

                {mensajeNoConfiable && (
                  <Alerta tono="exito" className="mt-2 w-fit">
                    {mensajeNoConfiable}
                  </Alerta>
                )}
              </div>
            )}

            {payload && (
              <p className="text-xs text-slate-500">
                Tu cuenta: {NOMBRE_TIPO[payload.tipo ?? ""] ?? payload.tipo ?? "usuario"}
              </p>
            )}
          </div>
        )}
      </main>
    </>
  );
}