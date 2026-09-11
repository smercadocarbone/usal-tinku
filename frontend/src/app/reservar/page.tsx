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
    <main className="contenido">
      <h1 style={{ fontSize: "1.3rem", letterSpacing: "-0.01em" }}>
        Reservar una clase
      </h1>

      {cargando && (
        <p style={{ color: "var(--color-texto-suave)" }}>Cargando...</p>
      )}

      {error && !cargando && (
        <div className="alerta alerta--error" role="alert" style={{ marginBottom: "1rem" }}>
          {error}
          <button
            type="button"
            className="boton boton--secundario"
            onClick={cargar}
            style={{ marginTop: "0.75rem", fontSize: "0.85rem", padding: "0.4rem 0.75rem" }}
          >
            Reintentar
          </button>
        </div>
      )}

      {esMenor && (
        <div className="alerta alerta--informativa" role="status" style={{ marginBottom: "1rem" }}>
          Tu Adulto Responsable debe reservar por vos.
        </div>
      )}

      {perfil && !esMenor && (
        <form onSubmit={reservar} className="formulario">
          <div
            className="campo"
            style={{
              maxWidth: "none",
              background: "var(--color-superficie)",
              border: "1px solid var(--color-borde)",
              borderRadius: "var(--radio)",
              padding: "1rem",
            }}
          >
            <p style={{ margin: "0 0 0.25rem", fontSize: "0.85rem", color: "var(--color-texto-suave)" }}>
              Tutor
            </p>
            <span style={{ fontWeight: 600, marginBottom: "0.25rem" }}>
              {perfil.nombre} {perfil.apellido}
            </span>
            {typeof perfil.precioHora === "number" && (
              <span style={{ color: "var(--color-texto-suave)", fontSize: "0.9rem" }}>
                {formatearPrecio(perfil.precioHora)} por hora
              </span>
            )}
          </div>

          <div className="campo">
            <label htmlFor="fecha">Fecha</label>
            <input
              id="fecha"
              type="date"
              required
              value={fechaElegida}
              onChange={elegirFecha}
            />
          </div>

          {cargandoFranjas ? (
            <p style={{ color: "var(--color-texto-suave)", fontSize: "0.9rem" }}>
              Cargando franjas...
            </p>
          ) : franjasVisibles.length > 0 ? (
            <div>
              <p style={{ fontSize: "0.9rem", fontWeight: 600, marginBottom: "0.5rem" }}>
                Franjas de disponibilidad
              </p>
              <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                {franjasVisibles.map((f) => (
                  <li key={f.id} style={{ marginBottom: "0.35rem" }}>
                    <button
                      type="button"
                      className="boton boton--secundario"
                      onClick={() => verHoras(f)}
                      style={{ fontSize: "0.85rem", padding: "0.4rem 0.75rem", width: "100%", textAlign: "left" }}
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
              style={{ color: "var(--color-texto-suave)", fontSize: "0.9rem" }}
            >
              Este tutor no publico disponibilidad todavia.
            </p>
          )}

          {horas.length > 0 && (
            <div className="campo">
              <label htmlFor="hora">Horario</label>
              <select
                id="hora"
                value={horaElegida}
                onChange={(e) => setHoraElegida(e.target.value)}
                required
                style={{
                  padding: "0.6rem 0.75rem",
                  border: "1px solid var(--color-borde)",
                  borderRadius: "8px",
                  fontSize: "1rem",
                  background: "var(--color-superficie)",
                  color: "var(--color-texto)",
                }}
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
            className="boton"
            disabled={enviando || !fechaElegida || !horaElegida}
          >
            {enviando ? "Creando reserva..." : "Reservar y pagar"}
          </button>
        </form>
      )}

      <p className="pie-enlace" style={{ marginTop: "2rem" }}>
        <Link href="/buscar">Volver a buscar</Link>
      </p>
    </main>
  );
}

export default function ReservarPage() {
  return (
    <Suspense fallback={<div className="contenido">Cargando...</div>}>
      <ReservarForm />
    </Suspense>
  );
}