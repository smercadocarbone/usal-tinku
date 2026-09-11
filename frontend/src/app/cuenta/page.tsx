"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { getSession } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { formatearFechaCorta } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";
import TemasTutor from "@/components/TemasTutor";

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

const DIAS = ["Domingo", "Lunes", "Martes", "Miercoles", "Jueves", "Viernes", "Sabado"];

const LABEL_ESTADO_SOLICITUD: Record<string, string> = {
  pendiente: "Pendiente",
  convertida: "Convertida en reserva",
  expirada: "Expirada",
  rechazada: "Rechazada",
};

interface Franja {
  id: string;
  tutorId: string;
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
  activa: boolean;
}

interface Solicitud {
  id: string;
  tutorId: string;
  horarioPropuesto: string;
  estado: string;
  expiraAt: string;
}

interface UsuarioResponse {
  id: string;
  [key: string]: unknown;
}

interface ReservaResponse {
  id: string;
  [key: string]: unknown;
}

function parseMinutos(hora: string): number {
  const [h, m] = hora.split(":").map(Number);
  return h * 60 + m;
}

function formatFechaHoraEsAr(iso: string): string {
  const d = new Date(iso);
  return d.toLocaleDateString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function PanelTutor({ tutorId }: { tutorId: string }) {
  const [tipoFranja, setTipoFranja] = useState<"semanal" | "puntual">("semanal");
  const [diaSemana, setDiaSemana] = useState<string>("1");
  const [fechaEspecifica, setFechaEspecifica] = useState("");
  const [horaInicio, setHoraInicio] = useState("10:00");
  const [horaFin, setHoraFin] = useState("11:00");
  const [franjas, setFranjas] = useState<Franja[]>([]);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const [procesando, setProcesando] = useState(false);
  const [cargandoLista, setCargandoLista] = useState(true);
  const [listaPendiente, setListaPendiente] = useState(false);

  function cargarFranjas() {
    setCargandoLista(true);
    setListaPendiente(false);
    api
      .get<Franja[]>(`/api/tutores/${tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => {
        setListaPendiente(true);
      })
      .finally(() => setCargandoLista(false));
  }

  useEffect(() => {
    cargarFranjas();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function publicar(e: FormEvent) {
    e.preventDefault();
    setError("");
    setExito("");

    if (tipoFranja === "puntual" && !fechaEspecifica) {
      setError("Elegí una fecha para la franja puntual.");
      return;
    }

    const minsInicio = parseMinutos(horaInicio);
    const minsFin = parseMinutos(horaFin);
    const duracion = minsFin - minsInicio;
    if (duracion < 30 || duracion > 180) {
      setError("La duración de la franja debe ser entre 30 y 180 minutos.");
      return;
    }

    const datos = {
      diaSemana: tipoFranja === "semanal" ? Number(diaSemana) : null,
      fechaEspecifica: tipoFranja === "puntual" ? fechaEspecifica : null,
      horaInicio,
      horaFin,
    };

    setProcesando(true);
    api
      .post("/api/tutores/franjas", datos)
      .then(() => {
        setExito("Franja publicada.");
        setHoraInicio("10:00");
        setHoraFin("11:00");
        setFechaEspecifica("");
        cargarFranjas();
      })
      .catch((err) => {
        if (err instanceof ApiError) setError(err.message);
        else setError("Error inesperado.");
      })
      .finally(() => setProcesando(false));
  }

  return (
    <section className="mt-8">
      <h2>Panel del tutor</h2>
      <div className="w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
        Tus credenciales estan en revision por el equipo de Tinku.
      </div>

      <div className="mt-4 w-full max-w-[26rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <form className="flex flex-col gap-4" onSubmit={publicar}>
          <p className="mb-2 font-semibold">Tipo de franja</p>
          <div className="flex flex-col gap-2 rounded-lg border border-borde bg-stone-50 p-3" role="group" aria-label="Tipo de franja">
            <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
              <input
                type="radio"
                name="tipoFranja"
                value="semanal"
                className="mt-[0.2rem] accent-accent"
                checked={tipoFranja === "semanal"}
                onChange={() => setTipoFranja("semanal")}
              />
              Semanal
            </label>
            <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
              <input
                type="radio"
                name="tipoFranja"
                value="puntual"
                className="mt-[0.2rem] accent-accent"
                checked={tipoFranja === "puntual"}
                onChange={() => setTipoFranja("puntual")}
              />
              Puntual
            </label>
          </div>

          {tipoFranja === "semanal" ? (
            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="diaSemana" className="text-[0.85rem] font-semibold">Dia de la semana</label>
              <select
                id="diaSemana"
                value={diaSemana}
                onChange={(e) => setDiaSemana(e.target.value)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {DIAS.map((d, i) => (
                  <option key={d} value={i}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="fechaEspecifica" className="text-[0.85rem] font-semibold">Fecha</label>
              <input
                id="fechaEspecifica"
                type="date"
                value={fechaEspecifica}
                onChange={(e) => setFechaEspecifica(e.target.value)}
                required
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>
          )}

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="horaInicio" className="text-[0.85rem] font-semibold">Hora de inicio</label>
            <input
              id="horaInicio"
              type="time"
              value={horaInicio}
              onChange={(e) => setHoraInicio(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="horaFin" className="text-[0.85rem] font-semibold">Hora de fin</label>
            <input
              id="horaFin"
              type="time"
              value={horaFin}
              onChange={(e) => setHoraFin(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
              {error}
            </div>
          )}
          {exito && (
            <div className="rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito" role="status">
              {exito}
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={procesando}
          >
            {procesando ? "Publicando..." : "Publicar franja"}
          </button>
        </form>
      </div>

      <h3 className="mt-6">Mis franjas</h3>
      {cargandoLista ? (
        <p className="text-texto-suave">Cargando...</p>
      ) : listaPendiente ? (
        <div className="w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
          El listado de franjas esta pendiente en backend.
        </div>
      ) : franjas.length === 0 ? (
        <p className="text-texto-suave">
          No publicaste franjas todavia.
        </p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {franjas.map((f) => (
            <li
              key={f.id}
              className="flex justify-between gap-4 border-b border-borde py-3"
            >
              <span>
                {f.diaSemana !== null
                  ? `${DIAS[f.diaSemana]} de ${f.horaInicio} a ${f.horaFin}`
                  : `${formatearFechaCorta(f.fechaEspecifica!)} de ${f.horaInicio} a ${f.horaFin}`}
              </span>
              <span
                className={
                  f.activa
                    ? "text-[0.85rem] font-medium text-accent"
                    : "text-[0.85rem] font-medium text-texto-suave"
                }
              >
                {f.activa ? "Activa" : "Inactiva"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function PanelAdulto() {
  const [dni, setDni] = useState("");
  const [nombre, setNombre] = useState("");
  const [apellido, setApellido] = useState("");
  const [fechaNac, setFechaNac] = useState("");
  const [password, setPassword] = useState("");
  const [fotoDni, setFotoDni] = useState<File | null>(null);
  const [consentimiento, setConsentimiento] = useState(false);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");
  const [procesando, setProcesando] = useState(false);

  const [menorAlta, setMenorAlta] = useState<{ id: string; nombre: string; apellido: string } | null>(null);
  const [bajaPaso, setBajaPaso] = useState<"idle" | "advertencia">("idle");
  const [bajaProcesando, setBajaProcesando] = useState(false);

  const [solicitudes, setSolicitudes] = useState<Solicitud[]>([]);
  const [cargandoSolicitudes, setCargandoSolicitudes] = useState(true);
  const [solicitudesError, setSolicitudesError] = useState(false);
  const [aprobandoId, setAprobandoId] = useState<string | null>(null);

  const cargarSolicitudes = useCallback(() => {
    setCargandoSolicitudes(true);
    setSolicitudesError(false);
    api
      .get<Solicitud[]>("/api/solicitudes/pendientes")
      .then(setSolicitudes)
      .catch(() => setSolicitudesError(true))
      .finally(() => setCargandoSolicitudes(false));
  }, []);

  useEffect(() => {
    cargarSolicitudes();
  }, [cargarSolicitudes]);

  function altaMenor(e: FormEvent) {
    e.preventDefault();
    setError("");
    setExito("");

    if (!consentimiento) {
      setError("Necesitas tu consentimiento como Adulto Responsable para dar de alta al menor");
      return;
    }
    if (!fotoDni) {
      setError("Adjunta la foto del DNI del menor.");
      return;
    }
    if (password.length < 8) {
      setError("La contraseña debe tener al menos 8 caracteres.");
      return;
    }

    const datos = {
      dniDeclarado: dni,
      nombreDeclarado: nombre,
      apellidoDeclarado: apellido,
      fechaNacimientoDeclarada: fechaNac,
      password,
      consentimientoExplicito: true,
      versionTextoConsentimiento: "v1",
    };

    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);

    setProcesando(true);
    api
      .post<UsuarioResponse>("/api/usuarios/menores", form)
      .then((res) => {
        setExito("Menor dado de alta.");
        setMenorAlta({ id: res.id, nombre, apellido });
        setDni("");
        setNombre("");
        setApellido("");
        setFechaNac("");
        setPassword("");
        setFotoDni(null);
        setConsentimiento(false);
      })
      .catch((err) => {
        if (err instanceof ApiError) setError(err.message);
        else setError("Error inesperado.");
      })
      .finally(() => setProcesando(false));
  }

  function aprobarSolicitud(id: string) {
    setAprobandoId(id);
    api
      .post<ReservaResponse>(`/api/solicitudes/${id}/aprobar`)
      .then((res) => {
        setSolicitudes((prev) => prev.map((s) => (s.id === id ? { ...s, estado: "convertida" } : s)));
        setExito(`Solicitud aprobada. Se creo la reserva.`);
        window.location.href = `/pagar?reserva=${res.id}`;
      })
      .catch((err) => {
        if (err instanceof ApiError) setError(err.message);
        else setError("Error inesperado.");
      })
      .finally(() => setAprobandoId(null));
  }

  function bajaMenorSinConfirmar() {
    if (!menorAlta) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorAlta.id}`)
      .then(() => {
        setExito("Menor dado de baja.");
        setMenorAlta(null);
        setBajaPaso("idle");
      })
      .catch((err) => {
        if (err instanceof ApiError && (err.status === 409 || err.status === 422)) {
          setBajaPaso("advertencia");
        } else {
          setError(err instanceof ApiError ? err.message : "Error inesperado.");
        }
      })
      .finally(() => setBajaProcesando(false));
  }

  function bajaMenorConfirmar() {
    if (!menorAlta) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorAlta.id}?confirmar=true`)
      .then(() => {
        setExito("Menor dado de baja.");
        setMenorAlta(null);
        setBajaPaso("idle");
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Error inesperado.");
      })
      .finally(() => setBajaProcesando(false));
  }

  return (
    <section className="mt-8">
      <h2>Menores a cargo</h2>

      <div className="mt-4 w-full max-w-[26rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <form className="flex flex-col gap-4" onSubmit={altaMenor}>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="dniMenor" className="text-[0.85rem] font-semibold">DNI del menor</label>
            <input
              id="dniMenor"
              type="text"
              inputMode="numeric"
              value={dni}
              onChange={(e) => setDni(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="nombreMenor" className="text-[0.85rem] font-semibold">Nombre</label>
            <input
              id="nombreMenor"
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="apellidoMenor" className="text-[0.85rem] font-semibold">Apellido</label>
            <input
              id="apellidoMenor"
              type="text"
              value={apellido}
              onChange={(e) => setApellido(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="fechaNacMenor" className="text-[0.85rem] font-semibold">Fecha de nacimiento</label>
            <input
              id="fechaNacMenor"
              type="date"
              value={fechaNac}
              onChange={(e) => setFechaNac(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="passMenor" className="text-[0.85rem] font-semibold">Contrasena</label>
            <input
              id="passMenor"
              type="password"
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="fotoDniMenor" className="text-[0.85rem] font-semibold">Foto del DNI</label>
            <input
              id="fotoDniMenor"
              type="file"
              accept="image/*"
              onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
              required
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>
          <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
            <input
              type="checkbox"
              className="mt-[0.2rem] accent-accent"
              checked={consentimiento}
              onChange={(e) => setConsentimiento(e.target.checked)}
              required
            />
            Confirmo que soy el Adulto Responsable del menor y doy mi consentimiento explicito para crear su cuenta
          </label>

          {error && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
              {error}
            </div>
          )}
          {exito && (
            <div className="rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito" role="status">
              {exito}
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={procesando}
          >
            {procesando ? "Cargando..." : "Dar de alta"}
          </button>
        </form>
      </div>

      <h3 className="mt-6">Solicitudes pendientes</h3>
      {cargandoSolicitudes ? (
        <p className="text-texto-suave">Cargando...</p>
      ) : solicitudesError ? (
        <div className="w-fit rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
          No se pudieron cargar las solicitudes.
          <button
            type="button"
            className="mt-3 block cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
            onClick={cargarSolicitudes}
          >
            Reintentar
          </button>
        </div>
      ) : solicitudes.length === 0 ? (
        <p className="text-texto-suave">No hay solicitudes pendientes.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {solicitudes.map((s) => (
            <li key={s.id} className="mb-3 w-full max-w-[26rem] rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta">
              <strong>Solicitud #{s.id.slice(0, 8)}</strong>
              <p className="my-1 text-[0.9rem] text-texto-suave">
                {formatFechaHoraEsAr(s.horarioPropuesto)}
              </p>
              <p className="my-1 text-[0.85rem]">
                Estado: {LABEL_ESTADO_SOLICITUD[s.estado] ?? s.estado}
              </p>
              {s.expiraAt && (
                <p className="my-1 text-[0.85rem] text-texto-suave">
                  Expira: {formatFechaHoraEsAr(s.expiraAt)}
                </p>
              )}
              {s.estado === "pendiente" && (
                <button
                  type="button"
                  className="mt-2 cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={aprobandoId === s.id}
                  onClick={() => aprobarSolicitud(s.id)}
                >
                  {aprobandoId === s.id ? "Procesando..." : "Aprobar"}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <h3 className="mt-6">Baja de menor</h3>
      <div className="w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
        El listado de menores esta pendiente en backend.
      </div>

      {menorAlta && (
        <div className="mt-3 w-full max-w-[26rem] rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta">
          {bajaPaso === "advertencia" ? (
            <>
              <p role="alert" className="mb-2 text-aviso">
                Este menor tiene reservas futuras. Se cancelaran.
              </p>
              <div className="flex gap-2">
                <button
                  type="button"
                  className="cursor-pointer rounded-lg bg-peligro px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-red-800 disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={bajaProcesando}
                  onClick={bajaMenorConfirmar}
                >
                  {bajaProcesando ? "Procesando..." : "Confirmar baja"}
                </button>
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                  onClick={() => setBajaPaso("idle")}
                >
                  Cancelar
                </button>
              </div>
            </>
          ) : (
            <button
              type="button"
              className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={bajaProcesando}
              onClick={bajaMenorSinConfirmar}
            >
              {bajaProcesando ? "Procesando..." : `Dar de baja a ${menorAlta.nombre} ${menorAlta.apellido}`}
            </button>
          )}
        </div>
      )}
    </section>
  );
}

export default function CuentaPage() {
  const session = getSession();
  const payload = session?.payload;

  return (
    <>
      <Cabecera />

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        <h1>Mi cuenta</h1>
        <p>
          Tu espacio en Tinku. Busca un tutor, reserva una clase y segui tus
          reservas.
        </p>

        <div className="my-6 grid gap-3">
          <Link
            href="/buscar"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] text-center font-semibold text-white enabled:hover:bg-accent-hover"
          >
            Buscar tutores
          </Link>
          <Link
            href="/cuenta/reservas"
            className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] text-center font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
          >
            Mis reservas
          </Link>
        </div>

        <dl>
          <div className="flex justify-between gap-4 border-b border-borde py-3">
            <dt className="font-semibold">DNI</dt>
            <dd className="m-0 text-right capitalize">{payload?.sub ?? "—"}</dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-borde py-3">
            <dt className="font-semibold">Tipo de cuenta</dt>
            <dd className="m-0 text-right capitalize">
              {payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}
            </dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-borde py-3">
            <dt className="font-semibold">Capacidad Estudiante</dt>
            <dd className="m-0 text-right capitalize">
              {payload?.cap_est ? "Activa" : "Inactiva"}
            </dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-borde py-3">
            <dt className="font-semibold">Adulto Responsable</dt>
            <dd className="m-0 text-right capitalize">
              {payload?.cap_ar ? "Activa" : "Inactiva"}
            </dd>
          </div>
        </dl>

        {payload?.tipo === "TUTOR" && <PanelTutor tutorId={String(payload.sub)} />}
        {payload?.tipo === "TUTOR" && <TemasTutor />}
        {payload?.cap_ar === true && <PanelAdulto />}
      </main>
    </>
  );
}