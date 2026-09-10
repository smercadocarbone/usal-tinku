"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { getSession } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import { formatearFechaCorta } from "@/lib/formatos";
import Cabecera from "@/components/Cabecera";

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
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) {
          setListaPendiente(true);
        } else {
          setListaPendiente(true);
        }
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
    <section style={{ marginTop: "2rem" }}>
      <h2>Panel del tutor</h2>
      <div className="alerta alerta--informativa" role="status">
        Tus credenciales y antecedentes estan en revision por el equipo de Tinku.
      </div>

      <div className="tarjeta" style={{ marginTop: "1rem" }}>
        <form className="formulario" onSubmit={publicar}>
          <label> tipo de franja </label>
          <div
            className="opciones"
            role="group"
            aria-label="Tipo de franja"
          >
            <label className="opcion">
              <input
                type="radio"
                name="tipoFranja"
                value="semanal"
                checked={tipoFranja === "semanal"}
                onChange={() => setTipoFranja("semanal")}
              />
              Semanal
            </label>
            <label className="opcion">
              <input
                type="radio"
                name="tipoFranja"
                value="puntual"
                checked={tipoFranja === "puntual"}
                onChange={() => setTipoFranja("puntual")}
              />
              Puntual
            </label>
          </div>

          {tipoFranja === "semanal" ? (
            <div className="campo">
              <label htmlFor="diaSemana">Dia de la semana</label>
              <select
                id="diaSemana"
                value={diaSemana}
                onChange={(e) => setDiaSemana(e.target.value)}
              >
                {DIAS.map((d, i) => (
                  <option key={i} value={i}>
                    {d}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="campo">
              <label htmlFor="fechaEspecifica">Fecha</label>
              <input
                id="fechaEspecifica"
                type="date"
                value={fechaEspecifica}
                onChange={(e) => setFechaEspecifica(e.target.value)}
                required
              />
            </div>
          )}

          <div className="campo">
            <label htmlFor="horaInicio">Hora de inicio</label>
            <input
              id="horaInicio"
              type="time"
              value={horaInicio}
              onChange={(e) => setHoraInicio(e.target.value)}
              required
            />
          </div>

          <div className="campo">
            <label htmlFor="horaFin">Hora de fin</label>
            <input
              id="horaFin"
              type="time"
              value={horaFin}
              onChange={(e) => setHoraFin(e.target.value)}
              required
            />
          </div>

          {error && (
            <div className="alerta alerta--error" role="alert">
              {error}
            </div>
          )}
          {exito && (
            <div className="alerta alerta--exito" role="status">
              {exito}
            </div>
          )}

          <button type="submit" className="boton" disabled={procesando}>
            {procesando ? "Publicando..." : "Publicar franja"}
          </button>
        </form>
      </div>

      <h3 style={{ marginTop: "1.5rem" }}>Mis franjas</h3>
      {cargandoLista ? (
        <p style={{ color: "var(--color-texto-suave)" }}>Cargando...</p>
      ) : listaPendiente ? (
        <div className="alerta alerta--informativa" role="status">
          El listado de franjas esta pendiente en backend.
        </div>
      ) : franjas.length === 0 ? (
        <p style={{ color: "var(--color-texto-suave)" }}>
          No publicaste franjas todavia.
        </p>
      ) : (
        <ul style={{ listStyle: "none", padding: 0, marginTop: "0.5rem" }}>
          {franjas.map((f) => (
            <li
              key={f.id}
              className="perfil-fila"
              style={{ justifyContent: "space-between" }}
            >
              <span>
                {f.diaSemana !== null
                  ? `${DIAS[f.diaSemana]} de ${f.horaInicio} a ${f.horaFin}`
                  : `${formatearFechaCorta(f.fechaEspecifica!)} de ${f.horaInicio} a ${f.horaFin}`}
              </span>
              <span
                style={{
                  fontSize: "0.85rem",
                  color: f.activa ? "var(--color-accent)" : "var(--color-texto-suave)",
                  fontWeight: 500,
                }}
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

  function cargarSolicitudes() {
    setCargandoSolicitudes(true);
    setSolicitudesError(false);
    api
      .get<Solicitud[]>("/api/solicitudes/pendientes")
      .then(setSolicitudes)
      .catch(() => setSolicitudesError(true))
      .finally(() => setCargandoSolicitudes(false));
  }

  useEffect(() => {
    cargarSolicitudes();
  }, []);

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
    <section style={{ marginTop: "2rem" }}>
      <h2>Menores a cargo</h2>

      <div className="tarjeta" style={{ marginTop: "1rem" }}>
        <form className="formulario" onSubmit={altaMenor}>
          <div className="campo">
            <label htmlFor="dniMenor">DNI del menor</label>
            <input
              id="dniMenor"
              type="text"
              inputMode="numeric"
              value={dni}
              onChange={(e) => setDni(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="nombreMenor">Nombre</label>
            <input
              id="nombreMenor"
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="apellidoMenor">Apellido</label>
            <input
              id="apellidoMenor"
              type="text"
              value={apellido}
              onChange={(e) => setApellido(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="fechaNacMenor">Fecha de nacimiento</label>
            <input
              id="fechaNacMenor"
              type="date"
              value={fechaNac}
              onChange={(e) => setFechaNac(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="passMenor">Contrasena</label>
            <input
              id="passMenor"
              type="password"
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="fotoDniMenor">Foto del DNI</label>
            <input
              id="fotoDniMenor"
              type="file"
              accept="image/*"
              onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
              required
            />
          </div>
          <label className="opcion" style={{ gap: "0.5rem" }}>
            <input
              type="checkbox"
              checked={consentimiento}
              onChange={(e) => setConsentimiento(e.target.checked)}
              required
            />
            Confirmo que soy el Adulto Responsable del menor y doy mi consentimiento explicito para crear su cuenta
          </label>

          {error && (
            <div className="alerta alerta--error" role="alert">
              {error}
            </div>
          )}
          {exito && (
            <div className="alerta alerta--exito" role="status">
              {exito}
            </div>
          )}

          <button type="submit" className="boton" disabled={procesando}>
            {procesando ? "Cargando..." : "Dar de alta"}
          </button>
        </form>
      </div>

      <h3 style={{ marginTop: "1.5rem" }}>Solicitudes pendientes</h3>
      {cargandoSolicitudes ? (
        <p style={{ color: "var(--color-texto-suave)" }}>Cargando...</p>
      ) : solicitudesError ? (
        <div className="alerta alerta--error" role="alert">
          No se pudieron cargar las solicitudes.
          <button
            type="button"
            className="boton boton--secundario"
            onClick={cargarSolicitudes}
            style={{ marginTop: "0.75rem", fontSize: "0.85rem", padding: "0.4rem 0.75rem" }}
          >
            Reintentar
          </button>
        </div>
      ) : solicitudes.length === 0 ? (
        <p style={{ color: "var(--color-texto-suave)" }}>No hay solicitudes pendientes.</p>
      ) : (
        <ul style={{ listStyle: "none", padding: 0, marginTop: "0.5rem" }}>
          {solicitudes.map((s) => (
            <li key={s.id} className="tarjeta" style={{ marginBottom: "0.75rem", padding: "1rem" }}>
              <strong>Solicitud #{s.id.slice(0, 8)}</strong>
              <p style={{ margin: "0.25rem 0", color: "var(--color-texto-suave)", fontSize: "0.9rem" }}>
                {formatFechaHoraEsAr(s.horarioPropuesto)}
              </p>
              <p style={{ margin: "0.25rem 0", fontSize: "0.85rem" }}>
                Estado: {LABEL_ESTADO_SOLICITUD[s.estado] ?? s.estado}
              </p>
              {s.expiraAt && (
                <p style={{ margin: "0.25rem 0", fontSize: "0.85rem", color: "var(--color-texto-suave)" }}>
                  Expira: {formatFechaHoraEsAr(s.expiraAt)}
                </p>
              )}
              {s.estado === "pendiente" && (
                <button
                  type="button"
                  className="boton"
                  disabled={aprobandoId === s.id}
                  onClick={() => aprobarSolicitud(s.id)}
                  style={{ marginTop: "0.5rem" }}
                >
                  {aprobandoId === s.id ? "Procesando..." : "Aprobar"}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <h3 style={{ marginTop: "1.5rem" }}>Baja de menor</h3>
      <div className="alerta alerta--informativa" role="status">
        El listado de menores esta pendiente en backend.
      </div>

      {menorAlta && (
        <div className="tarjeta" style={{ marginTop: "0.75rem", padding: "1rem" }}>
          {bajaPaso === "advertencia" ? (
            <>
              <p
                    role="alert"
                    style={{ color: "var(--color-aviso)", marginBottom: "0.5rem" }}
                  >
                    Este menor tiene reservas futuras. Se cancelaran.
                  </p>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <button
                  type="button"
                  className="boton"
                  disabled={bajaProcesando}
                  onClick={bajaMenorConfirmar}
                  style={{ background: "var(--color-peligro)" }}
                >
                  {bajaProcesando ? "Procesando..." : "Confirmar baja"}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={() => setBajaPaso("idle")}
                >
                  Cancelar
                </button>
              </div>
            </>
          ) : (
            <button
              type="button"
              className="boton boton--secundario"
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

      <main className="contenido">
        <h1>Mi cuenta</h1>
        <p>
          Tu espacio en Tinku. Busca un tutor, reserva una clase y segui tus
          reservas.
        </p>

        <div
          style={{
            display: "grid",
            gap: "0.75rem",
            margin: "1.5rem 0",
          }}
        >
          <Link
            href="/buscar"
            className="boton"
            style={{ textDecoration: "none", textAlign: "center" }}
          >
            Buscar tutores
          </Link>
          <Link
            href="/cuenta/reservas"
            className="boton boton--secundario"
            style={{ textDecoration: "none", textAlign: "center" }}
          >
            Mis reservas
          </Link>
        </div>

        <dl>
          <div className="perfil-fila">
            <dt>DNI</dt>
            <dd>{payload?.sub ?? "—"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Tipo de cuenta</dt>
            <dd>{payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Capacidad Estudiante</dt>
            <dd>{payload?.cap_est ? "Activa" : "Inactiva"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Adulto Responsable</dt>
            <dd>{payload?.cap_ar ? "Activa" : "Inactiva"}</dd>
          </div>
        </dl>

        {payload?.tipo === "TUTOR" && <PanelTutor tutorId={String(payload.sub)} />}
        {payload?.cap_ar === true && <PanelAdulto />}
      </main>
    </>
  );
}
