"use client";

import { Fragment, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { Alerta, Boton, Campo, Tarjeta } from "@/components/ui";

type Rol = "adulto" | "tutor";

const PASOS = ["Rol", "Datos", "Verificación", "Acceso"];

function IconoCheck() {
  return (
    <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true" className="h-4 w-4">
      <path fillRule="evenodd" d="M16.704 4.153a.75.75 0 0 1 .143 1.052l-8 10.5a.75.75 0 0 1-1.127.075l-4.5-4.5a.75.75 0 0 1 1.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 0 1 1.05-.143Z" clipRule="evenodd" />
    </svg>
  );
}

function IconoSubida() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true" className="h-10 w-10 text-teal-500">
      <path strokeLinecap="round" strokeLinejoin="round" d="M12 16.5V9.75m0 0 3 3m-3-3-3 3M6.75 19.5a4.5 4.5 0 0 1-1.41-8.775 5.25 5.25 0 0 1 10.233-2.33 3 3 0 0 1 3.758 3.848A3.752 3.752 0 0 1 18 19.5H6.75Z" />
    </svg>
  );
}

/** Solo para los dos controles de subida del paso 2, que no entran en `Campo`. */
const labelCls = "text-sm font-medium text-slate-700";

export default function RegistroPage() {
  const router = useRouter();

  const [paso, setPaso] = useState(0);
  const [rol, setRol] = useState<Rol | null>(null);

  const [dni, setDni] = useState("");
  const [nombre, setNombre] = useState("");
  const [apellido, setApellido] = useState("");
  const [fechaNacimiento, setFechaNacimiento] = useState("");
  const [capacidadEstudiante, setCapacidadEstudiante] = useState(true);
  const [capacidadAdultoResponsable, setCapacidadAdultoResponsable] = useState(false);

  const [fotoDni, setFotoDni] = useState<File | null>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [aceptaTerminos, setAceptaTerminos] = useState(false);

  const [verificando, setVerificando] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [menorDeEdad, setMenorDeEdad] = useState(false);

  function irAPaso(n: number) {
    setError(null);
    setMenorDeEdad(false);
    setPaso(n);
  }

  function verificarDni() {
    if (!fotoDni) {
      setError("Subí la foto de tu DNI para verificarlo.");
      return;
    }
    setVerificando(true);
    setError(null);
    setMenorDeEdad(false);

    const datos = { dniDeclarado: dni, nombreDeclarado: nombre, apellidoDeclarado: apellido, fechaNacimientoDeclarada: fechaNacimiento };
    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);

    const base = rol === "adulto" ? "/api/usuarios" : "/api/tutores";
    api
      .post(`${base}/verificar-dni`, form)
      .then(() => setPaso(3))
      .catch(mapaError)
      .finally(() => setVerificando(false));
  }

  async function crearCuenta(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    setMenorDeEdad(false);

    const datos = {
      dniDeclarado: dni,
      nombreDeclarado: nombre,
      apellidoDeclarado: apellido,
      fechaNacimientoDeclarada: fechaNacimiento,
      email,
      password,
      ...(rol === "adulto" ? { capacidadEstudiante, capacidadAdultoResponsable } : {}),
    };

    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni as File);

    try {
      const base = rol === "adulto" ? "/api/usuarios" : "/api/tutores";
      await api.post(`${base}/registro`, form);
      router.replace("/login?registrado=1");
    } catch (err) {
      mapaError(err);
    } finally {
      setEnviando(false);
    }
  }

  function mapaError(err: unknown) {
    if (err instanceof ApiError) {
      switch (err.status) {
        case 403:
          setMenorDeEdad(true);
          break;
        case 429:
          setError(`${err.message} (${err.detalles?.espera_restante_hs ?? "?"} hs de espera).`);
          break;
        default:
          setError(err.message || "No se pudo completar el registro.");
      }
    } else {
      setError("No se pudo completar el registro. Intentá de nuevo.");
    }
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <Tarjeta className="w-full max-w-2xl p-8 sm:p-10">
        <div className="mb-8 text-lg font-bold tracking-tight text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>

        <ol className="mb-10 flex items-center" aria-label="Progreso del registro">
          {PASOS.map((nombrePaso, i) => (
            <Fragment key={nombrePaso}>
              <li className="flex flex-col items-center">
                <span
                  aria-current={i === paso ? "step" : undefined}
                  className={
                    i < paso
                      ? "flex h-9 w-9 items-center justify-center rounded-full bg-teal-700 text-white"
                      : i === paso
                        ? "flex h-9 w-9 items-center justify-center rounded-full bg-teal-700 text-sm font-semibold text-white ring-4 ring-teal-100"
                        : "flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-sm font-medium text-slate-600"
                  }
                >
                  {i < paso ? <IconoCheck /> : i + 1}
                </span>
                <span
                  className={
                    i === paso
                      ? "mt-2 whitespace-nowrap text-xs font-semibold text-slate-800"
                      : i < paso
                        ? "mt-2 whitespace-nowrap text-xs text-slate-600"
                        : "mt-2 whitespace-nowrap text-xs text-slate-500"
                  }
                >
                  {nombrePaso}
                </span>
              </li>
              {i < PASOS.length - 1 && (
                <li
                  className={`mx-2 mb-4 h-0.5 flex-1 rounded ${
                    i < paso ? "bg-teal-600" : "bg-slate-200"
                  }`}
                  aria-hidden="true"
                />
              )}
            </Fragment>
          ))}
        </ol>

        {paso === 0 && (
          <section className="flex flex-col gap-8">
            <header>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-800">
                ¿Quién va a usar Tinku?
              </h1>
              <p className="mt-2 text-slate-500">Elegí de qué lado estás para armarte la cuenta correcta.</p>
            </header>

            <div className="flex flex-col gap-4" role="group" aria-label="Tipo de cuenta">
              <button
                type="button"
                aria-pressed={rol === "adulto"}
                onClick={() => setRol("adulto")}
                className={`flex min-h-[140px] cursor-pointer flex-col justify-center gap-1 rounded-xl border p-6 text-left transition-all duration-300 hover:-translate-y-1 hover:shadow-md ${
                  rol === "adulto"
                    ? "border-teal-500 bg-teal-50 ring-2 ring-teal-500"
                    : "border-slate-200 bg-white"
                }`}
              >
                <span className="text-lg font-semibold text-slate-800">Soy mayor de edad</span>
                <span className="text-sm text-slate-500">Quiero tomar clases o tengo un menor a cargo.</span>
              </button>

              <button
                type="button"
                aria-pressed={rol === "tutor"}
                onClick={() => setRol("tutor")}
                className={`flex min-h-[140px] cursor-pointer flex-col justify-center gap-1 rounded-xl border p-6 text-left transition-all duration-300 hover:-translate-y-1 hover:shadow-md ${
                  rol === "tutor"
                    ? "border-teal-500 bg-teal-50 ring-2 ring-teal-500"
                    : "border-slate-200 bg-white"
                }`}
              >
                <span className="text-lg font-semibold text-slate-800">Soy Tutor</span>
                <span className="text-sm text-slate-500">Quiero dar clases y ofrecer mis tutorías.</span>
              </button>
            </div>

            <footer className="flex items-center justify-end gap-3">
              <Boton
                className="min-w-[120px]"
                disabled={!rol}
                onClick={() => irAPaso(1)}
              >
                Continuar
              </Boton>
            </footer>
          </section>
        )}

        {paso === 1 && (
          <section className="flex flex-col gap-8">
            <header>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-800">Tus datos</h1>
              <p className="mt-2 text-slate-500">Así figura en tu DNI. Los verificamos después con su foto.</p>
            </header>

            <form
              className="flex flex-col gap-6"
              onSubmit={(e) => {
                e.preventDefault();
                irAPaso(2);
              }}
            >
              <div className="grid grid-cols-2 gap-4">
                <Campo
                  id="nombre"
                  etiqueta="Nombre"
                  type="text"
                  autoComplete="given-name"
                  required
                  value={nombre}
                  onChange={(e) => setNombre(e.target.value)}
                />
                <Campo
                  id="apellido"
                  etiqueta="Apellido"
                  type="text"
                  autoComplete="family-name"
                  required
                  value={apellido}
                  onChange={(e) => setApellido(e.target.value)}
                />
              </div>

              <Campo
                id="dni"
                etiqueta="DNI"
                type="text"
                inputMode="numeric"
                autoComplete="username"
                required
                value={dni}
                onChange={(e) => setDni(e.target.value)}
              />

              <Campo
                id="fechaNacimiento"
                etiqueta="Fecha de nacimiento"
                type="date"
                autoComplete="bday"
                required
                value={fechaNacimiento}
                onChange={(e) => setFechaNacimiento(e.target.value)}
              />

              {rol === "adulto" && (
                <div className="flex flex-col gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4" role="group" aria-label="Para qué vas a usar Tinku">
                  <p className="text-sm font-medium text-slate-800">¿Para qué vas a usar Tinku?</p>
                  <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      className="mt-0.5 size-4 accent-teal-600"
                      checked={capacidadEstudiante}
                      onChange={(e) => setCapacidadEstudiante(e.target.checked)}
                    />
                    Tomar clases para mí
                  </label>
                  <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      className="mt-0.5 size-4 accent-teal-600"
                      checked={capacidadAdultoResponsable}
                      onChange={(e) => setCapacidadAdultoResponsable(e.target.checked)}
                    />
                    Gestionar clases para un menor a mi cargo
                  </label>
                  {!capacidadEstudiante && !capacidadAdultoResponsable && (
                    <span className="text-xs text-slate-500">
                      Elegí al menos una opción para continuar.
                    </span>
                  )}
                </div>
              )}

              <footer className="flex items-center justify-between gap-3">
                <Boton variante="fantasma" onClick={() => irAPaso(0)}>
                  Volver
                </Boton>
                <Boton
                  type="submit"
                  className="min-w-[120px]"
                  disabled={rol === "adulto" && !capacidadEstudiante && !capacidadAdultoResponsable}
                >
                  Continuar
                </Boton>
              </footer>
            </form>
          </section>
        )}

        {paso === 2 && (
          <section className="flex flex-col gap-8">
            <header>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-800">Verificá tu identidad</h1>
              <p className="mt-2 text-slate-500">Subí una foto de tu DNI (frente). Confirmamos tus datos y tu edad.</p>
            </header>

            <div className="flex flex-col gap-6">
              <div className="flex flex-col gap-2">
                <label htmlFor="fotoDni" className={labelCls}>Foto de tu DNI (frente)</label>
                <label
                  htmlFor="fotoDni"
                  className="flex min-h-[200px] cursor-pointer flex-col items-center justify-center gap-3 rounded-xl border-2 border-dashed border-slate-300 bg-slate-50 p-6 text-center transition-colors hover:border-teal-500 hover:bg-teal-50/50"
                >
                  <IconoSubida />
                  <span className="text-sm font-medium text-slate-700">
                    Arrastra el frente de tu DNI aquí
                  </span>
                  <span className="text-xs text-slate-500">
                    {fotoDni ? fotoDni.name : "o tocá para elegir el archivo · JPG o PNG"}
                  </span>
                  <input
                    id="fotoDni"
                    type="file"
                    accept="image/*"
                    required
                    onChange={(e) => setFotoDni(e.target.files?.[0] ?? null)}
                    className="sr-only"
                  />
                </label>
              </div>

              <div className="flex flex-col gap-2">
                <label htmlFor="fotoRostro" className={labelCls}>Foto de tu cara</label>
                {/* Sin `opacity` en el contenedor: atenuar todo el bloque
                    también atenúa la explicación de por qué está deshabilitado,
                    y ese texto es justamente el que la persona necesita leer.
                    Lo "apagado" se comunica con el fondo y el cursor. */}
                <div className="flex min-h-[110px] cursor-not-allowed flex-col items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-100 p-4 text-center">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.5} aria-hidden="true" className="h-6 w-6 text-slate-500">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M16.5 10.5V6.75a4.5 4.5 0 1 0-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 0 0 2.25-2.25v-6.75a2.25 2.25 0 0 0-2.25-2.25H6.75a2.25 2.25 0 0 0-2.25 2.25v6.75a2.25 2.25 0 0 0 2.25 2.25Z" />
                  </svg>
                  <input
                    id="fotoRostro"
                    type="file"
                    accept="image/*"
                    disabled
                    className="sr-only"
                  />
                  <span className="text-xs text-slate-600">
                    La comparación facial es un paso que se habilita próximamente.
                  </span>
                </div>
              </div>

              <footer className="flex items-center justify-between gap-3">
                <Boton variante="fantasma" onClick={() => irAPaso(1)}>
                  Volver
                </Boton>
                <Boton
                  className="min-w-[120px]"
                  disabled={!fotoDni}
                  cargando={verificando}
                  textoCargando="Verificando…"
                  onClick={verificarDni}
                >
                  Verificar
                </Boton>
              </footer>
            </div>
          </section>
        )}

        {paso === 3 && (
          <section className="flex flex-col gap-8">
            <header>
              <h1 className="text-2xl font-semibold tracking-tight text-slate-800">Creá tu acceso</h1>
              <p className="mt-2 text-slate-500">Tu identidad fue verificada. Faltan tus credenciales.</p>
            </header>

            <form className="flex flex-col gap-6" onSubmit={crearCuenta}>
              <Campo
                id="email"
                etiqueta="Email"
                type="email"
                autoComplete="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />

              <Campo
                id="password"
                etiqueta="Contraseña"
                type="password"
                autoComplete="new-password"
                required
                minLength={8}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />

              <div className="flex items-center gap-3 text-sm text-slate-500 before:flex-1 before:h-px before:bg-slate-200 before:content-[''] after:flex-1 after:h-px after:bg-slate-200 after:content-['']">
                o
              </div>

              <Boton variante="secundario" className="w-full text-sm" disabled>
                Continuar con Google
              </Boton>
              <span className="text-xs text-slate-500">Ingreso con Google disponible próximamente.</span>

              <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  className="mt-0.5 size-4 accent-teal-600"
                  checked={aceptaTerminos}
                  required
                  onChange={(e) => setAceptaTerminos(e.target.checked)}
                />
                Acepto los Términos y Condiciones
              </label>
              <Alerta tono="info" className="border-dashed p-4 text-xs leading-snug">
                <strong>Versión provisoria.</strong> Al crear tu cuenta confirmás que sos
                mayor de 18 años, que los datos cargados son verdaderos y que tus clases
                quedan cubiertas por el protocolo de seguridad de la plataforma. El texto
                completo se publicará antes del lanzamiento.
              </Alerta>

              <footer className="flex items-center justify-between gap-3">
                <Boton variante="fantasma" onClick={() => irAPaso(2)}>
                  Volver
                </Boton>
                <Boton
                  type="submit"
                  className="min-w-[120px]"
                  cargando={enviando}
                  textoCargando="Creando cuenta…"
                >
                  Crear cuenta
                </Boton>
              </footer>
            </form>
          </section>
        )}

        {menorDeEdad && (
          <Alerta tono="aviso" className="mt-4">
            <strong>Sos menor de edad.</strong> Un Adulto Responsable debe
            crearte el perfil. No se creó ninguna cuenta.
          </Alerta>
        )}

        {error && (
          <Alerta tono="error" className="mt-4">
            {error}
          </Alerta>
        )}

        <p className="mt-8 text-center text-sm text-slate-500">
          ¿Ya tenés cuenta? <Link href="/login" className="text-teal-700 hover:underline">Iniciar sesión</Link>
          <br />
          ¿Querés dar clases?{" "}
          <Link href="/registro/tutor" className="text-teal-700 hover:underline">Registrate como tutor</Link>
        </p>
      </Tarjeta>
    </main>
  );
}