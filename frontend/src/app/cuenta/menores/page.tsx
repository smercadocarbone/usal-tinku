"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api, ApiError, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import { Alerta, Boton, Campo, CampoSelect, Cargando, Tarjeta } from "@/components/ui";

const LABEL_ESTADO_SOLICITUD: Record<string, string> = {
  pendiente: "Pendiente",
  convertida: "Convertida en reserva",
  expirada: "Expirada",
  rechazada: "Rechazada",
};

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

export default function CuentaMenoresPage() {
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

  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [errorMenores, setErrorMenores] = useState<string | null>(null);
  const [menorBajaId, setMenorBajaId] = useState("");
  const [bajaPaso, setBajaPaso] = useState<"idle" | "advertencia">("idle");
  const [bajaProcesando, setBajaProcesando] = useState(false);

  const cargarMenores = useCallback(() => {
    setErrorMenores(null);
    getMenores()
      .then((lista) => {
        setMenores(lista);
        setMenorBajaId((actual) => actual || lista[0]?.id || "");
      })
      .catch((err) => setErrorMenores(mensajeDeError(err, "No se pudo cargar tu listado de menores.")));
  }, []);

  useEffect(() => {
    cargarMenores();
  }, [cargarMenores]);

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
      .then(() => {
        setExito("Menor dado de alta.");
        cargarMenores();
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
    if (!menorBajaId) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorBajaId}`)
      .then(() => {
        setExito("Menor dado de baja.");
        setBajaPaso("idle");
        setMenorBajaId("");
        cargarMenores();
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
    if (!menorBajaId) return;
    setBajaProcesando(true);
    api
      .delete(`/api/usuarios/menores/${menorBajaId}?confirmar=true`)
      .then(() => {
        setExito("Menor dado de baja.");
        setBajaPaso("idle");
        setMenorBajaId("");
        cargarMenores();
      })
      .catch((err) => {
        setError(err instanceof ApiError ? err.message : "Error inesperado.");
      })
      .finally(() => setBajaProcesando(false));
  }

  return (
    <section>
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

      {errorMenores && <Alerta tono="error">{errorMenores}</Alerta>}

      {!errorMenores && menores === null && <Cargando>Cargando tus menores…</Cargando>}

      {menores !== null && menores.length === 0 && (
        <p className="text-sm text-slate-500">No tenés menores a cargo todavía.</p>
      )}

      {menores !== null && menores.length > 0 && (
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
            <div className="flex flex-col gap-3">
              <CampoSelect
                id="menorBaja"
                etiqueta="Menor"
                value={menorBajaId}
                onChange={(e) => setMenorBajaId(e.target.value)}
              >
                {menores.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.nombre} {m.apellido}
                  </option>
                ))}
              </CampoSelect>
              <Boton
                variante="secundario"
                className="w-fit"
                cargando={bajaProcesando}
                textoCargando="Procesando..."
                onClick={bajaMenorSinConfirmar}
              >
                Dar de baja
              </Boton>
            </div>
          )}
        </Tarjeta>
      )}
    </section>
  );
}
