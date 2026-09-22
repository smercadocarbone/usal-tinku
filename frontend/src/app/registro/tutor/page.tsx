"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { Alerta, Boton, Campo, CampoSelect, Tarjeta } from "@/components/ui";

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
      <Tarjeta className="w-full max-w-lg p-8">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <h1 className="mb-1 text-xl tracking-tight">Registrate como tutor</h1>
        <p className="mb-6 text-slate-500">
          Verificamos tu identidad con la foto de tu DNI y que seas mayor de 18
          anos para poder dar clases en Tinku.
        </p>

        {bloqueado && (
          <Alerta tono="aviso" className="mb-4">
            Sos menor de edad. No se puede crear la cuenta de tutor.
          </Alerta>
        )}

        {!bloqueado && (
          <form className="flex flex-col gap-4" onSubmit={onSubmitRegistro}>
            <Campo
              id="dniDeclarado"
              etiqueta="DNI"
              type="text"
              inputMode="numeric"
              required
              value={dniDeclarado}
              onChange={(e) => setDniDeclarado(e.target.value)}
            />

            <div className="grid grid-cols-2 gap-4">
              <Campo
                id="nombreDeclarado"
                etiqueta="Nombre"
                type="text"
                autoComplete="given-name"
                required
                value={nombreDeclarado}
                onChange={(e) => setNombreDeclarado(e.target.value)}
              />

              <Campo
                id="apellidoDeclarado"
                etiqueta="Apellido"
                type="text"
                autoComplete="family-name"
                required
                value={apellidoDeclarado}
                onChange={(e) => setApellidoDeclarado(e.target.value)}
              />
            </div>

            <Campo
              id="fechaNacimientoDeclarada"
              etiqueta="Fecha de nacimiento"
              type="date"
              autoComplete="bday"
              required
              value={fechaNacimientoDeclarada}
              onChange={(e) => setFechaNacimientoDeclarada(e.target.value)}
            />

            <Campo
              id="password"
              etiqueta="Contrasena"
              type="password"
              autoComplete="new-password"
              required
              minLength={8}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />

            <Campo
              id="fotoDni"
              etiqueta="Foto de tu DNI (frente)"
              type="file"
              accept="image/*"
              required
              onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
            />

            {error && <Alerta tono="error">{error}</Alerta>}

            <Boton type="submit" cargando={enviando} textoCargando="Verificando...">
              Crear cuenta
            </Boton>
          </form>
        )}

        <p className="mt-5 text-center text-sm text-slate-500">
          Ya tenes cuenta? <Link href="/login">Iniciar sesion</Link>
        </p>
      </Tarjeta>
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
      <Tarjeta className="w-full max-w-lg p-8">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <h1 className="mb-1 text-xl tracking-tight">Cuenta de tutor creada</h1>
        <p className="mb-6 text-slate-500">
          Ya podes iniciar sesion. Si queres, subi estos documentos ahora para
          que el equipo de Tinku los revise.
        </p>

        <form className="flex flex-col gap-4" onSubmit={onSubmitCredencial}>
          <CampoSelect
            id="tipoCredencial"
            etiqueta="Tipo de credencial"
            value={tipoCredencial}
            onChange={(e) => setTipoCredencial(e.target.value)}
          >
            {TIPOS_CREDENCIAL.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </CampoSelect>

          <Campo
            id="archivoCredencial"
            etiqueta="Archivo de credencial"
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            required
            onChange={(e) => setArchivoCredencial(e.target.files?.[0] ?? null)}
          />

          {errorCredencial && <Alerta tono="error">{errorCredencial}</Alerta>}

          {okCredencial && (
            <Alerta tono="exito">
              Credencial subida. Queda en revision por el equipo de Tinku.
            </Alerta>
          )}

          <Boton
            type="submit"
            disabled={okCredencial}
            cargando={enviandoCredencial}
            textoCargando="Cargando..."
          >
            Subir credencial
          </Boton>
        </form>

        <Alerta tono="aviso" className="mt-4">
          Los documentos quedan pendientes de revision. Podes completarlos
          despues desde tu cuenta.
        </Alerta>

        <Boton
          className="mt-4 w-full"
          onClick={() => router.replace("/login?tutorRegistrado=1")}
        >
          Ir a iniciar sesion
        </Boton>
      </Tarjeta>
    </main>
  );
}