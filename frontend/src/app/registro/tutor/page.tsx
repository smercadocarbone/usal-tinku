"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";

interface CredencialResponse {
  id: string;
}

const TIPOS_CREDENCIAL = [
  { value: "TITULO", label: "Titulo" },
  { value: "CERTIFICADO_ANALITICO", label: "Certificado analitico" },
  { value: "MATRICULA", label: "Matricula" },
] as const;

type Paso = 1 | 2;

export default function RegistroTutorPage() {
  const [paso, setPaso] = useState<Paso>(1);

  const [dniDeclarado, setDniDeclarado] = useState("");
  const [nombreDeclarado, setNombreDeclarado] = useState("");
  const [apellidoDeclarado, setApellidoDeclarado] = useState("");
  const [fechaNacimientoDeclarada, setFechaNacimientoDeclarada] = useState("");
  const [password, setPassword] = useState("");
  const [fotoDni, setFotoDni] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [bloqueado, setBloqueado] = useState(false);

  async function onSubmitRegistro(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setError(null);

    if (!fotoDni) {
      setError("Subi una foto de tu DNI.");
      setEnviando(false);
      return;
    }

    const datos = {
      dniDeclarado,
      nombreDeclarado,
      apellidoDeclarado,
      fechaNacimientoDeclarada,
      password,
    };

    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);

    try {
      await api.post("/api/tutores/registro", form);
      setPaso(2);
    } catch (err) {
      if (err instanceof ApiError) {
        switch (err.status) {
          case 403:
            setBloqueado(true);
            break;
          case 409:
            setError(err.message || "Ya existe un tutor con ese DNI.");
            break;
          case 429:
            setError(
              `${err.message} (${err.detalles?.espera_restante_hs ?? "?"} hs de espera).`
            );
            break;
          default:
            setError(err.message || "No se pudo completar el registro.");
        }
      } else {
        setError("No se pudo completar el registro. Intenta de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  if (paso === 2) {
    return <PasoDos />;
  }

  return (
    <main className="pantalla">
      <div className="tarjeta tarjeta--ancha">
        <div className="marca">
          Tinku<span>.</span>
        </div>
        <h1>Registrate como tutor</h1>
        <p>
          Verificamos tu identidad con la foto de tu DNI y que seas mayor de 18
          anos para poder dar clases en Tinku.
        </p>

        {bloqueado && (
          <div className="alerta alerta--informativa" role="status">
            Sos menor de edad. No se puede crear la cuenta de tutor.
          </div>
        )}

        {!bloqueado && (
          <form className="formulario" onSubmit={onSubmitRegistro}>
            <div className="campo">
              <label htmlFor="dniDeclarado">DNI</label>
              <input
                id="dniDeclarado"
                type="text"
                inputMode="numeric"
                required
                value={dniDeclarado}
                onChange={(e) => setDniDeclarado(e.target.value)}
              />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
              <div className="campo">
                <label htmlFor="nombreDeclarado">Nombre</label>
                <input
                  id="nombreDeclarado"
                  type="text"
                  autoComplete="given-name"
                  required
                  value={nombreDeclarado}
                  onChange={(e) => setNombreDeclarado(e.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="apellidoDeclarado">Apellido</label>
                <input
                  id="apellidoDeclarado"
                  type="text"
                  autoComplete="family-name"
                  required
                  value={apellidoDeclarado}
                  onChange={(e) => setApellidoDeclarado(e.target.value)}
                />
              </div>
            </div>

            <div className="campo">
              <label htmlFor="fechaNacimientoDeclarada">Fecha de nacimiento</label>
              <input
                id="fechaNacimientoDeclarada"
                type="date"
                autoComplete="bday"
                required
                value={fechaNacimientoDeclarada}
                onChange={(e) => setFechaNacimientoDeclarada(e.target.value)}
              />
            </div>

            <div className="campo">
              <label htmlFor="password">Contrasena</label>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            <div className="campo">
              <label htmlFor="fotoDni">Foto de tu DNI (frente)</label>
              <input
                id="fotoDni"
                type="file"
                accept="image/*"
                required
                onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
              />
            </div>

            {error && (
              <div className="alerta alerta--error" role="alert">
                {error}
              </div>
            )}

            <button type="submit" className="boton" disabled={enviando}>
              {enviando ? "Verificando..." : "Crear cuenta"}
            </button>
          </form>
        )}

        <p className="pie-enlace">
          Ya tenes cuenta? <Link href="/login">Iniciar sesion</Link>
        </p>
      </div>
    </main>
  );
}

function PasoDos() {
  const router = useRouter();

  const [tipoCredencial, setTipoCredencial] = useState("TITULO");
  const [archivoCredencial, setArchivoCredencial] = useState<File | null>(null);
  const [enviandoCredencial, setEnviandoCredencial] = useState(false);
  const [okCredencial, setOkCredencial] = useState(false);
  const [errorCredencial, setErrorCredencial] = useState<string | null>(null);

  async function onSubmitCredencial(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviandoCredencial(true);
    setErrorCredencial(null);
    setOkCredencial(false);

    if (!archivoCredencial) {
      setErrorCredencial("Selecciona un archivo.");
      setEnviandoCredencial(false);
      return;
    }

    const form = new FormData();
    form.append(
      "datos",
      new Blob([JSON.stringify({ tipoDocumento: tipoCredencial })], {
        type: "application/json",
      })
    );
    form.append("archivo", archivoCredencial);

    try {
      await api.post<CredencialResponse>("/api/tutores/credenciales", form);
      setOkCredencial(true);
    } catch (err) {
      setErrorCredencial(
        err instanceof ApiError
          ? err.message || "No se pudo subir la credencial."
          : "No se pudo subir la credencial."
      );
    } finally {
      setEnviandoCredencial(false);
    }
  }

  return (
    <main className="pantalla">
      <div className="tarjeta tarjeta--ancha">
        <div className="marca">
          Tinku<span>.</span>
        </div>
        <h1>Cuenta de tutor creada</h1>
        <p>
          Ya podes iniciar sesion. Si queres, subi estos documentos ahora para
          que el equipo de Tinku los revise.
        </p>

        <form className="formulario" onSubmit={onSubmitCredencial}>
          <div className="campo">
            <label htmlFor="tipoCredencial">Tipo de credencial</label>
            <select
              id="tipoCredencial"
              value={tipoCredencial}
              onChange={(e) => setTipoCredencial(e.target.value)}
            >
              {TIPOS_CREDENCIAL.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="archivoCredencial">Archivo de credencial</label>
            <input
              id="archivoCredencial"
              type="file"
              accept=".pdf,image/*"
              required
              onChange={(e) => setArchivoCredencial(e.target.files?.[0] ?? null)}
            />
          </div>

          {errorCredencial && (
            <div className="alerta alerta--error" role="alert">
              {errorCredencial}
            </div>
          )}

          {okCredencial && (
            <div className="alerta alerta--exito" role="status">
              Credencial subida. Queda en revision por el equipo de Tinku.
            </div>
          )}

          <button
            type="submit"
            className="boton"
            disabled={enviandoCredencial || okCredencial}
          >
            {enviandoCredencial ? "Cargando..." : "Subir credencial"}
          </button>
        </form>

        <div className="alerta alerta--informativa" role="status">
          Los documentos quedan pendientes de revision. Podes completarlos
          despues desde tu cuenta.
        </div>

        <button
          className="boton"
          onClick={() => router.replace("/login?tutorRegistrado=1")}
        >
          Ir a iniciar sesion
        </button>
      </div>
    </main>
  );
}
