"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { getSession } from "@/lib/auth";
import { formatearFechaCorta, formatearPrecio } from "@/lib/formatos";

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

interface FranjaDisponible {
  id: string;
  tutorId: string;
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
  activa: boolean;
}

const NOMBRE_DIA = [
  "Domingo",
  "Lunes",
  "Martes",
  "Miercoles",
  "Jueves",
  "Viernes",
  "Sabado",
];

interface ReservaCreada {
  id: string;
}

function ReservarForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const tutorId = searchParams.get("tutor");

  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [franjas, setFranjas] = useState<FranjaDisponible[]>([]);
  const [cargando, setCargando] = useState(true);
  const [cargandoFranjas, setCargandoFranjas] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [fechaElegida, setFechaElegida] = useState("");
  const [horaElegida, setHoraElegida] = useState("");
  const [horas, setHoras] = useState<string[]>([]);
  const [enviando, setEnviando] = useState(false);

  const session = getSession();
  const payload = session?.payload;
  const esMenor = payload?.tipo === "MENOR";

  function cargar() {
    if (!tutorId) {
      setError("Falta el Tutor para reservar.");
      setCargando(false);
      return;
    }
    setCargando(true);
    setError(null);
    api
      .get<TutorPerfil>(`/api/tutores/${tutorId}`)
      .then((p) => setPerfil(p))
      .catch((err) => {
        if (err instanceof ApiError) {
          setError(
            err.status === 404
              ? "Tutor no encontrado."
              : err.message || "No se pudo cargar el perfil."
          );
        }
      })
      .finally(() => setCargando(false));
    setCargandoFranjas(true);
    api
      .get<FranjaDisponible[]>(`/api/tutores/${tutorId}/franjas`)
      .then((lista) => setFranjas(lista.filter((f) => f.activa)))
      .catch(() => setFranjas([]))
      .finally(() => setCargandoFranjas(false));
  }

  useEffect(() => {
    cargar();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tutorId]);

  function elegirFecha(e: React.ChangeEvent<HTMLInputElement>) {
    setFechaElegida(e.target.value);
    setHoraElegida("");
    setHoras([]);
    setError(null);
  }

  function verHoras(franja: FranjaDisponible) {
    if (franja.diaSemana !== null && !fechaElegida) {
      setError("Elegi una fecha valida.");
      return;
    }
    if (franja.fechaEspecifica !== null) {
      setFechaElegida(franja.fechaEspecifica.slice(0, 10));
    }
    setError(null);
    const [inicio, fin] = [franja.horaInicio, franja.horaFin].map((h) => {
      const [hh, mm] = h.split(":").map(Number);
      return hh * 60 + mm;
    });
    const lista: string[] = [];
    for (let t = inicio; t < fin; t += 60) {
      const h = Math.floor(t / 60);
      const m = t % 60;
      lista.push(
        `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`
      );
    }
    setHoras(lista);
  }

  const franjasVisibles = fechaElegida
    ? franjas.filter(
        (f) =>
          (f.diaSemana !== null &&
            f.diaSemana === new Date(`${fechaElegida}T12:00:00`).getDay()) ||
          (f.fechaEspecifica !== null &&
            f.fechaEspecifica.slice(0, 10) === fechaElegida)
      )
    : franjas;

  async function reservar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!tutorId || !fechaElegida || !horaElegida) return;
    setEnviando(true);
    setError(null);

    const horario = new Date(`${fechaElegida}T${horaElegida}:00`);
    try {
      const reserva = await api.post<ReservaCreada>("/api/reservas", {
        tutorId,
        horario: horario.toISOString(),
      });
      router.replace(`/pagar?reserva=${reserva.id}`);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo reservar. Intentá de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="mx-auto max-w-[44rem] px-5 py-8">
      <h1 className="text-[1.3rem] tracking-[-0.01em]">
        Reservar una clase
      </h1>

      {cargando && (
        <p className="text-texto-suave">Cargando...</p>
      )}

      {error && !cargando && (
        <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
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

      {esMenor && (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
          Tu Adulto Responsable debe reservar por vos.
        </div>
      )}

      {perfil && !esMenor && (
        <form onSubmit={reservar} className="flex flex-col gap-4">
          <div
            className="flex flex-col gap-[0.35rem] rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta"
          >
            <p className="mb-1 text-[0.85rem] text-texto-suave">
              Tutor
            </p>
            <span className="mb-1 font-semibold">
              {perfil.nombre} {perfil.apellido}
            </span>
            {typeof perfil.precioHora === "number" && (
              <span className="text-[0.9rem] text-texto-suave">
                {formatearPrecio(perfil.precioHora)} por hora
              </span>
            )}
          </div>

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="fecha" className="text-[0.85rem] font-semibold">Fecha</label>
            <input
              id="fecha"
              type="date"
              required
              value={fechaElegida}
              onChange={elegirFecha}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          {cargandoFranjas ? (
            <p className="text-[0.9rem] text-texto-suave">
              Cargando franjas...
            </p>
          ) : franjasVisibles.length > 0 ? (
            <div>
              <p className="mb-2 text-[0.9rem] font-semibold">
                Franjas de disponibilidad
              </p>
              <ul className="m-0 list-none p-0">
                {franjasVisibles.map((f) => (
                  <li key={f.id} className="mb-[0.35rem]">
                    <button
                      type="button"
                      className="w-full cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-left text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                      onClick={() => verHoras(f)}
                    >
                      {f.diaSemana !== null
                        ? `${NOMBRE_DIA[f.diaSemana]} de ${f.horaInicio} a ${f.horaFin}`
                        : `${formatearFechaCorta(`${f.fechaEspecifica}T12:00:00`)} de ${f.horaInicio} a ${f.horaFin}`}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p
              role="status"
              className="text-[0.9rem] text-texto-suave"
            >
              Este tutor no publico disponibilidad todavia.
            </p>
          )}

          {horas.length > 0 && (
            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="hora" className="text-[0.85rem] font-semibold">Horario</label>
              <select
                id="hora"
                value={horaElegida}
                onChange={(e) => setHoraElegida(e.target.value)}
                required
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <option value="">Elegi un horario</option>
                {horas.map((h) => (
                  <option key={h} value={h}>
                    {h}
                  </option>
                ))}
              </select>
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={enviando || !fechaElegida || !horaElegida}
          >
            {enviando ? "Creando reserva..." : "Reservar y pagar"}
          </button>
        </form>
      )}

      <p className="mt-8 text-center text-[0.9rem] text-texto-suave">
        <Link href="/buscar">Volver a buscar</Link>
      </p>
    </main>
  );
}

export default function ReservarPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-[44rem] px-5 py-8">Cargando...</div>}>
      <ReservarForm />
    </Suspense>
  );
}