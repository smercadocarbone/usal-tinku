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
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <div className="w-full max-w-[32rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <div className="mb-6 text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>
        <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Registrate como tutor</h1>
        <p className="mb-6 text-texto-suave">
          Verificamos tu identidad con la foto de tu DNI y que seas mayor de 18
          anos para poder dar clases en Tinku.
        </p>

        {bloqueado && (
          <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
            Sos menor de edad. No se puede crear la cuenta de tutor.
          </div>
        )}

        {!bloqueado && (
          <form className="flex flex-col gap-4" onSubmit={onSubmitRegistro}>
            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="dniDeclarado" className="text-[0.85rem] font-semibold">DNI</label>
              <input
                id="dniDeclarado"
                type="text"
                inputMode="numeric"
                required
                value={dniDeclarado}
                onChange={(e) => setDniDeclarado(e.target.value)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="nombreDeclarado" className="text-[0.85rem] font-semibold">Nombre</label>
                <input
                  id="nombreDeclarado"
                  type="text"
                  autoComplete="given-name"
                  required
                  value={nombreDeclarado}
                  onChange={(e) => setNombreDeclarado(e.target.value)}
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                />
              </div>

              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="apellidoDeclarado" className="text-[0.85rem] font-semibold">Apellido</label>
                <input
                  id="apellidoDeclarado"
                  type="text"
                  autoComplete="family-name"
                  required
                  value={apellidoDeclarado}
                  onChange={(e) => setApellidoDeclarado(e.target.value)}
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                />
              </div>
            </div>

            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="fechaNacimientoDeclarada" className="text-[0.85rem] font-semibold">Fecha de nacimiento</label>
              <input
                id="fechaNacimientoDeclarada"
                type="date"
                autoComplete="bday"
                required
                value={fechaNacimientoDeclarada}
                onChange={(e) => setFechaNacimientoDeclarada(e.target.value)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>

            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="password" className="text-[0.85rem] font-semibold">Contrasena</label>
              <input
                id="password"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>

            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="fotoDni" className="text-[0.85rem] font-semibold">Foto de tu DNI (frente)</label>
              <input
                id="fotoDni"
                type="file"
                accept="image/*"
                required
                onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              />
            </div>

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
                {error}
              </div>
            )}

            <button
              type="submit"
              className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
              disabled={enviando}
            >
              {enviando ? "Verificando..." : "Crear cuenta"}
            </button>
          </form>
        )}

        <p className="mt-5 text-center text-[0.9rem] text-texto-suave">
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
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <div className="w-full max-w-[32rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <div className="mb-6 text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>
        <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Cuenta de tutor creada</h1>
        <p className="mb-6 text-texto-suave">
          Ya podes iniciar sesion. Si queres, subi estos documentos ahora para
          que el equipo de Tinku los revise.
        </p>

        <form className="flex flex-col gap-4" onSubmit={onSubmitCredencial}>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="tipoCredencial" className="text-[0.85rem] font-semibold">Tipo de credencial</label>
            <select
              id="tipoCredencial"
              value={tipoCredencial}
              onChange={(e) => setTipoCredencial(e.target.value)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {TIPOS_CREDENCIAL.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="archivoCredencial" className="text-[0.85rem] font-semibold">Archivo de credencial</label>
            <input
              id="archivoCredencial"
              type="file"
              accept=".pdf,image/*"
              required
              onChange={(e) => setArchivoCredencial(e.target.files?.[0] ?? null)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          {errorCredencial && (
            <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
              {errorCredencial}
            </div>
          )}

          {okCredencial && (
            <div className="rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito" role="status">
              Credencial subida. Queda en revision por el equipo de Tinku.
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={enviandoCredencial || okCredencial}
          >
            {enviandoCredencial ? "Cargando..." : "Subir credencial"}
          </button>
        </form>

        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
          Los documentos quedan pendientes de revision. Podes completarlos
          despues desde tu cuenta.
        </div>

        <button
          className="mt-4 w-full cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover"
          onClick={() => router.replace("/login?tutorRegistrado=1")}
        >
          Ir a iniciar sesion
        </button>
      </div>
    </main>
  );
}