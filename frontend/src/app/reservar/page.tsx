"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import { formatearFechaCorta, formatearPrecio } from "@/lib/formatos";
import {
  Alerta,
  Boton,
  Campo,
  CampoSelect,
  Cargando,
  EstadoVacio,
  Tarjeta,
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
  "Miércoles",
  "Jueves",
  "Viernes",
  "Sábado",
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

  const session = useSesion();
  const payload = session?.payload;
  const esMenor = payload?.tipo === "MENOR";
  const [copiado, setCopiado] = useState(false);

  // B5: el menor no tiene el picker conectado todavía (04-descubrir…); que el
  // aviso tenga una acción concreta (copiar el pedido) en vez de un callejón.
  async function copiarPedido() {
    if (!perfil) return;
    const texto = `¡Hola! Quiero tomar una clase con ${perfil.nombre}${perfil.apellido ? ` ${perfil.apellido}` : ""} en Tinku. ¿Me la reservás?`;
    try {
      await navigator.clipboard.writeText(texto);
      setCopiado(true);
    } catch {
      setCopiado(false);
    }
  }

  function cargar() {
    if (!tutorId) {
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
    // Sin tutor en la URL no hay nada que reservar: redirigir a la búsqueda
    // con un mensaje neutro en vez de un error rojo inútil (B9).
    if (!tutorId) {
      router.replace("/buscar");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tutorId]);

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
      setError("Elegí una fecha válida.");
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
    <main className="mx-auto max-w-2xl px-5 py-8">
      <h1 className="text-xl tracking-tight">
        Reservar una clase
      </h1>

      {cargando && <Cargando>Cargando...</Cargando>}

      {error && !cargando && (
        <Alerta tono="error" className="mb-4">
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

      {esMenor && (
        <Alerta tono="aviso" className="mb-4">
          <p>Podés pedir esta clase, pero la confirma tu Adulto Responsable.</p>
          {perfil && (
            <div className="mt-2 flex flex-col items-start gap-2">
              <p className="text-xs">
                Sugerencia para enviarle: “¡Hola! Quiero tomar una clase con{" "}
                {perfil.nombre}
                {perfil.apellido ? ` ${perfil.apellido}` : ""} en Tinku. ¿Me la
                reservás?”
              </p>
              <Boton
                type="button"
                tamano="sm"
                onClick={copiarPedido}
                aria-live="polite"
              >
                {copiado ? "Mensaje copiado" : "Copiar este mensaje"}
              </Boton>
            </div>
          )}
        </Alerta>
      )}

      {perfil && !esMenor && (
        <form onSubmit={reservar} className="flex flex-col gap-4">
          <Tarjeta className="flex flex-col gap-1.5 p-4">
            <p className="mb-1 text-sm text-slate-500">
              Tutor
            </p>
            <span className="mb-1 font-semibold">
              {perfil.nombre} {perfil.apellido}
            </span>
            {typeof perfil.precioHora === "number" && (
              <span className="text-sm text-slate-500">
                {formatearPrecio(perfil.precioHora)} por hora
              </span>
            )}
          </Tarjeta>

          <Campo
            id="fecha"
            etiqueta="Fecha"
            type="date"
            required
            value={fechaElegida}
            onChange={elegirFecha}
          />

          {cargandoFranjas ? (
            <Cargando>Cargando franjas...</Cargando>
          ) : franjasVisibles.length > 0 ? (
            <div>
              <p className="mb-2 text-sm font-semibold">
                Franjas de disponibilidad
              </p>
              <ul className="m-0 list-none p-0">
                {franjasVisibles.map((f) => (
                  <li key={f.id} className="mb-1.5">
                    <Boton
                      variante="secundario"
                      tamano="sm"
                      className="w-full justify-start text-left"
                      onClick={() => verHoras(f)}
                    >
                      {f.diaSemana !== null
                        ? `${NOMBRE_DIA[f.diaSemana]} de ${f.horaInicio} a ${f.horaFin}`
                        : `${formatearFechaCorta(`${f.fechaEspecifica}T12:00:00`)} de ${f.horaInicio} a ${f.horaFin}`}
                    </Boton>
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <EstadoVacio>Este tutor no publicó disponibilidad todavía.</EstadoVacio>
          )}

          {horas.length > 0 && (
            <CampoSelect
              id="hora"
              etiqueta="Horario"
              value={horaElegida}
              onChange={(e) => setHoraElegida(e.target.value)}
              required
            >
              <option value="">Elegí un horario</option>
              {horas.map((h) => (
                <option key={h} value={h}>
                  {h}
                </option>
              ))}
            </CampoSelect>
          )}

          <Boton
            type="submit"
            cargando={enviando}
            textoCargando="Creando reserva..."
            disabled={!fechaElegida || !horaElegida}
          >
            Reservar y pagar
          </Boton>
        </form>
      )}

      <p className="mt-8 text-center text-sm text-slate-500">
        <Link href="/buscar">Volver a buscar</Link>
      </p>
    </main>
  );
}

export default function ReservarPage() {
  return (
    <Suspense fallback={<div className="mx-auto max-w-2xl px-5 py-8">Cargando...</div>}>
      <ReservarForm />
    </Suspense>
  );
}