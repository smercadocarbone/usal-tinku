"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { getSession } from "@/lib/auth";
import { api, ApiError } from "@/lib/api";
import Cabecera from "@/components/Cabecera";
import TabHorarios from "@/components/tutor/TabHorarios";
import TabMaterias from "@/components/tutor/TabMaterias";
import TabPrecio from "@/components/tutor/TabPrecio";
import { Alerta, Boton, Campo, Cargando, PanelTab, Tabs, Tarjeta, clasesBoton } from "@/components/ui";

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

const LABEL_ESTADO_SOLICITUD: Record<string, string> = {
  pendiente: "Pendiente",
  convertida: "Convertida en reserva",
  expirada: "Expirada",
  rechazada: "Rechazada",
};

const TABS = [
  { id: "horarios", label: "Mis Horarios" },
  { id: "materias", label: "Mis Materias" },
  { id: "precio", label: "Configuración de Precio" },
] as const;

type IdTab = (typeof TABS)[number]["id"];

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
  const [tab, setTab] = useState<IdTab>("horarios");

  return (
    <section className="mt-8">
      <Alerta tono="aviso" className="w-fit">
        Tus credenciales estan en revision por el equipo de Tinku.
      </Alerta>

      <div className="mt-6 flex flex-col gap-6 lg:flex-row">
        {/* Sidebar */}
        <aside className="w-full shrink-0 lg:w-52">
          <nav
            className="no-scrollbar flex gap-2 overflow-x-auto border-b border-slate-200 pb-3 lg:flex-col lg:gap-1 lg:border-0 lg:pb-0"
            aria-label="Navegación del panel del tutor"
          >
            {TABS.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                aria-pressed={tab === t.id}
                className={
                  tab === t.id
                    ? "cursor-pointer whitespace-nowrap rounded-lg bg-teal-700 px-3.5 py-2 text-left text-sm font-semibold text-white transition-all duration-200"
                    : "cursor-pointer whitespace-nowrap rounded-lg px-3.5 py-2 text-left text-sm font-medium text-slate-500 transition-all duration-200 hover:bg-slate-100 hover:text-slate-800"
                }
              >
                {t.label}
              </button>
            ))}
            <Link
              href="/cuenta/reservas"
              className="whitespace-nowrap rounded-lg px-3.5 py-2 text-left text-sm font-medium text-slate-500 transition-all duration-200 hover:bg-slate-100 hover:text-slate-800"
            >
              Mis reservas
            </Link>
            <Link
              href="/buscar"
              className="whitespace-nowrap rounded-lg px-3.5 py-2 text-left text-sm font-medium text-slate-500 transition-all duration-200 hover:bg-slate-100 hover:text-slate-800"
            >
              Buscar tutores
            </Link>
          </nav>
        </aside>

        {/* Área principal */}
        <div className="min-w-0 flex-1 rounded-xl bg-slate-50 p-4 sm:p-6">
          <Tabs
            opciones={TABS}
            activo={tab}
            onCambio={setTab}
            etiqueta="Secciones del panel del tutor"
          />

          <Tarjeta className="mt-6 w-full">
            {tab === "horarios" && (
              <PanelTab id="horarios">
                <TabHorarios tutorId={tutorId} />
              </PanelTab>
            )}
            {tab === "materias" && (
              <PanelTab id="materias">
                <TabMaterias />
              </PanelTab>
            )}
            {tab === "precio" && (
              <PanelTab id="precio">
                <TabPrecio />
              </PanelTab>
            )}
          </Tarjeta>
        </div>
      </div>
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
      <h2 className="text-lg font-semibold text-slate-800">Menores a cargo</h2>

      <Tarjeta className="mt-4 w-full max-w-sm p-8">
        <form className="flex flex-col gap-4" onSubmit={altaMenor}>
          <Campo
            id="dniMenor"
            etiqueta="DNI del menor"
            type="text"
            inputMode="numeric"
            value={dni}
            onChange={(e) => setDni(e.target.value)}
            required
          />
          <Campo
            id="nombreMenor"
            etiqueta="Nombre"
            type="text"
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
            required
          />
          <Campo
            id="apellidoMenor"
            etiqueta="Apellido"
            type="text"
            value={apellido}
            onChange={(e) => setApellido(e.target.value)}
            required
          />
          <Campo
            id="fechaNacMenor"
            etiqueta="Fecha de nacimiento"
            type="date"
            value={fechaNac}
            onChange={(e) => setFechaNac(e.target.value)}
            required
          />
          <Campo
            id="passMenor"
            etiqueta="Contrasena"
            type="password"
            minLength={8}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Campo
            id="fotoDniMenor"
            etiqueta="Foto del DNI"
            type="file"
            accept="image/*"
            onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
            required
          />
          <label className="flex cursor-pointer items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1 accent-teal-600"
              checked={consentimiento}
              onChange={(e) => setConsentimiento(e.target.checked)}
              required
            />
            Confirmo que soy el Adulto Responsable del menor y doy mi consentimiento explicito para crear su cuenta
          </label>

          {error && <Alerta tono="error">{error}</Alerta>}
          {exito && <Alerta tono="exito">{exito}</Alerta>}

          <Boton type="submit" cargando={procesando} textoCargando="Cargando...">
            Dar de alta
          </Boton>
        </form>
      </Tarjeta>

      <h3 className="mt-6 text-base font-semibold text-slate-800">Solicitudes pendientes</h3>
      {cargandoSolicitudes ? (
        <Cargando>Cargando...</Cargando>
      ) : solicitudesError ? (
        <Alerta tono="error" className="w-fit">
          No se pudieron cargar las solicitudes.
          <Boton
            variante="secundario"
            tamano="sm"
            className="mt-3 flex"
            onClick={cargarSolicitudes}
          >
            Reintentar
          </Boton>
        </Alerta>
      ) : solicitudes.length === 0 ? (
        <p className="text-slate-500">No hay solicitudes pendientes.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {solicitudes.map((s) => (
            <Tarjeta as="li" key={s.id} className="mb-3 w-full max-w-sm p-4">
              <strong>Solicitud #{s.id.slice(0, 8)}</strong>
              <p className="my-1 text-sm text-slate-500">
                {formatFechaHoraEsAr(s.horarioPropuesto)}
              </p>
              <p className="my-1 text-sm">
                Estado: {LABEL_ESTADO_SOLICITUD[s.estado] ?? s.estado}
              </p>
              {s.expiraAt && (
                <p className="my-1 text-sm text-slate-500">
                  Expira: {formatFechaHoraEsAr(s.expiraAt)}
                </p>
              )}
              {s.estado === "pendiente" && (
                <Boton
                  className="mt-2"
                  cargando={aprobandoId === s.id}
                  textoCargando="Procesando..."
                  onClick={() => aprobarSolicitud(s.id)}
                >
                  Aprobar
                </Boton>
              )}
            </Tarjeta>
          ))}
        </ul>
      )}

      <h3 className="mt-6 text-base font-semibold text-slate-800">Baja de menor</h3>
      <Alerta tono="aviso" className="w-fit">
        El listado de menores esta pendiente en backend.
      </Alerta>

      {menorAlta && (
        <Tarjeta className="mt-3 w-full max-w-sm p-4">
          {bajaPaso === "advertencia" ? (
            <>
              <p role="alert" className="mb-2 text-amber-800">
                Este menor tiene reservas futuras. Se cancelaran.
              </p>
              <div className="flex gap-2">
                <Boton
                  className="bg-red-700 enabled:hover:bg-red-800"
                  cargando={bajaProcesando}
                  textoCargando="Procesando..."
                  onClick={bajaMenorConfirmar}
                >
                  Confirmar baja
                </Boton>
                <Boton variante="secundario" onClick={() => setBajaPaso("idle")}>
                  Cancelar
                </Boton>
              </div>
            </>
          ) : (
            <Boton
              variante="secundario"
              cargando={bajaProcesando}
              textoCargando="Procesando..."
              onClick={bajaMenorSinConfirmar}
            >
              {`Dar de baja a ${menorAlta.nombre} ${menorAlta.apellido}`}
            </Boton>
          )}
        </Tarjeta>
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

      <main className="mx-auto max-w-5xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Mi cuenta</h1>
        <p className="mt-1 text-sm text-slate-500">
          Tu espacio en Tinku. Buscá un tutor, reservá una clase y seguí tus
          reservas.
        </p>

        <div className="my-6 grid gap-3 sm:grid-cols-2">
          <Link
            href="/buscar"
            className={clasesBoton("primario", "md", "rounded-2xl px-5 py-4")}
          >
            Buscar tutores
          </Link>
          <Link
            href="/cuenta/reservas"
            className={clasesBoton("secundario", "md", "rounded-2xl bg-white px-5 py-4 shadow-sm")}
          >
            Mis reservas
          </Link>
        </div>

        <dl className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
            <dt className="text-sm font-semibold text-slate-800">DNI</dt>
            <dd className="m-0 text-right text-sm text-slate-600 capitalize">{payload?.sub ?? "—"}</dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
            <dt className="text-sm font-semibold text-slate-800">Tipo de cuenta</dt>
            <dd className="m-0 text-right text-sm text-slate-600 capitalize">
              {payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}
            </dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
            <dt className="text-sm font-semibold text-slate-800">Capacidad Estudiante</dt>
            <dd className="m-0 text-right text-sm text-slate-600 capitalize">
              {payload?.cap_est ? "Activa" : "Inactiva"}
            </dd>
          </div>
          <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
            <dt className="text-sm font-semibold text-slate-800">Adulto Responsable</dt>
            <dd className="m-0 text-right text-sm text-slate-600 capitalize">
              {payload?.cap_ar ? "Activa" : "Inactiva"}
            </dd>
          </div>
        </dl>

        {payload?.tipo === "TUTOR" && <PanelTutor tutorId={String(payload.sub)} />}
        {payload?.cap_ar === true && <PanelAdulto />}
      </main>
    </>
  );
}