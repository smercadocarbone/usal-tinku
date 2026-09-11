"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";

type Rol = "adulto" | "tutor";

const PASOS = ["Rol", "Datos", "Verificación", "Acceso"];

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
      <div className="w-full max-w-[32rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <div className="mb-6 text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>

        {paso === 0 && (
          <>
            <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">¿Quién va a usar Tinku?</h1>
            <p className="mb-6 text-texto-suave">Elegí de qué lado estás para armarte la cuenta correcta.</p>

            <div className="mb-4 flex flex-col gap-3" role="group" aria-label="Tipo de cuenta">
              <button
                type="button"
                aria-pressed={rol === "adulto"}
                onClick={() => setRol("adulto")}
                className={
                  rol === "adulto"
                    ? "flex cursor-pointer flex-col gap-1 rounded-[10px] border border-accent bg-teal-50 p-4 text-left text-[0.95rem] shadow-[0_0_0_1px_#0d9488] enabled:hover:border-accent enabled:hover:bg-teal-50"
                    : "flex cursor-pointer flex-col gap-1 rounded-[10px] border border-borde bg-superficie p-4 text-left text-[0.95rem] enabled:hover:border-accent enabled:hover:bg-teal-50"
                }
              >
                <strong>Soy mayor de edad</strong>
                <span className="text-[0.85rem] text-texto-suave">Quiero tomar clases o tengo un menor a cargo.</span>
              </button>

              <button
                type="button"
                aria-pressed={rol === "tutor"}
                onClick={() => setRol("tutor")}
                className={
                  rol === "tutor"
                    ? "flex cursor-pointer flex-col gap-1 rounded-[10px] border border-accent bg-teal-50 p-4 text-left text-[0.95rem] shadow-[0_0_0_1px_#0d9488] enabled:hover:border-accent enabled:hover:bg-teal-50"
                    : "flex cursor-pointer flex-col gap-1 rounded-[10px] border border-borde bg-superficie p-4 text-left text-[0.95rem] enabled:hover:border-accent enabled:hover:bg-teal-50"
                }
              >
                <strong>Soy Tutor</strong>
                <span className="text-[0.85rem] text-texto-suave">Quiero dar clases y ofrecer mis tutorías.</span>
              </button>
            </div>

            <button
              type="button"
              className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
              disabled={!rol}
              onClick={() => setPaso(1)}
            >
              Continuar
            </button>
          </>
        )}

        {paso === 1 && (
          <>
            <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Tus datos</h1>
            <p className="mb-6 text-texto-suave">Así figura en tu DNI. Los verificamos después con su foto.</p>

            <form
              className="flex flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault();
                setPaso(2);
              }}
            >
              <div className="grid grid-cols-2 gap-4">
                <div className="flex flex-col gap-[0.35rem]">
                  <label htmlFor="nombre" className="text-[0.85rem] font-semibold">Nombre</label>
                  <input
                    id="nombre"
                    type="text"
                    autoComplete="given-name"
                    required
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                    className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </div>
                <div className="flex flex-col gap-[0.35rem]">
                  <label htmlFor="apellido" className="text-[0.85rem] font-semibold">Apellido</label>
                  <input
                    id="apellido"
                    type="text"
                    autoComplete="family-name"
                    required
                    value={apellido}
                    onChange={(e) => setApellido(e.target.value)}
                    className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </div>
              </div>

              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="dni" className="text-[0.85rem] font-semibold">DNI</label>
                <input
                  id="dni"
                  type="text"
                  inputMode="numeric"
                  autoComplete="username"
                  required
                  value={dni}
                  onChange={(e) => setDni(e.target.value)}
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                />
              </div>

              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="fechaNacimiento" className="text-[0.85rem] font-semibold">Fecha de nacimiento</label>
                <input
                  id="fechaNacimiento"
                  type="date"
                  autoComplete="bday"
                  required
                  value={fechaNacimiento}
                  onChange={(e) => setFechaNacimiento(e.target.value)}
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                />
              </div>

              {rol === "adulto" && (
                <div className="flex flex-col gap-2 rounded-lg border border-borde bg-stone-50 p-3" role="group" aria-label="Para qué vas a usar Tinku">
                  <p className="mb-1 text-[0.85rem] font-semibold text-texto">¿Para qué vas a usar Tinku?</p>
                  <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
                    <input
                      type="checkbox"
                      className="mt-[0.2rem] accent-accent"
                      checked={capacidadEstudiante}
                      onChange={(e) => setCapacidadEstudiante(e.target.checked)}
                    />
                    Tomar clases para mí
                  </label>
                  <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
                    <input
                      type="checkbox"
                      className="mt-[0.2rem] accent-accent"
                      checked={capacidadAdultoResponsable}
                      onChange={(e) => setCapacidadAdultoResponsable(e.target.checked)}
                    />
                    Gestionar clases para un menor a mi cargo
                  </label>
                  {!capacidadEstudiante && !capacidadAdultoResponsable && (
                    <span className="text-[0.8rem] text-texto-suave">
                      Elegí al menos una opción para continuar.
                    </span>
                  )}
                </div>
              )}

              <div className="flex gap-3">
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                  onClick={() => setPaso(0)}
                >
                  Volver
                </button>
                <button
                  type="submit"
                  className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={rol === "adulto" && !capacidadEstudiante && !capacidadAdultoResponsable}
                >
                  Continuar
                </button>
              </div>
            </form>
          </>
        )}

        {paso === 2 && (
          <>
            <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Verificá tu identidad</h1>
            <p className="mb-6 text-texto-suave">Subí una foto de tu DNI (frente). Confirmamos tus datos y tu edad.</p>

            <div className="flex flex-col gap-4">
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

              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="fotoRostro" className="text-[0.85rem] font-semibold">Foto de tu cara</label>
                <input
                  id="fotoRostro"
                  type="file"
                  accept="image/*"
                  disabled
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto disabled:cursor-not-allowed disabled:opacity-60"
                />
                <span className="text-[0.8rem] text-texto-suave">
                  La comparación facial es un paso que se habilita próximamente.
                </span>
              </div>

              <div className="flex gap-3">
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                  onClick={() => setPaso(1)}
                >
                  Volver
                </button>
                <button
                  type="button"
                  className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={verificando || !fotoDni}
                  onClick={verificarDni}
                >
                  {verificando ? "Verificando…" : "Verificar"}
                </button>
              </div>
            </div>
          </>
        )}

        {paso === 3 && (
          <>
            <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Creá tu acceso</h1>
            <p className="mb-6 text-texto-suave">Tu identidad fue verificada. Faltan tus credenciales.</p>

            <form className="flex flex-col gap-4" onSubmit={crearCuenta}>
              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="email" className="text-[0.85rem] font-semibold">Email</label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
                />
              </div>

              <div className="flex flex-col gap-[0.35rem]">
                <label htmlFor="password" className="text-[0.85rem] font-semibold">Contraseña</label>
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

              <div className="flex items-center gap-3 text-[0.85rem] text-texto-suave before:flex-1 before:h-px before:bg-borde before:content-[''] after:flex-1 after:h-px after:bg-borde after:content-['']">
                o
              </div>

              <button
                type="button"
                className="cursor-not-allowed rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent opacity-60"
                disabled
              >
                Continuar con Google
              </button>
              <span className="text-[0.8rem] text-texto-suave">Ingreso con Google disponible próximamente.</span>

              <label className="flex cursor-pointer items-start gap-2 text-[0.9rem]">
                <input
                  type="checkbox"
                  className="mt-[0.2rem] accent-accent"
                  checked={aceptaTerminos}
                  required
                  onChange={(e) => setAceptaTerminos(e.target.checked)}
                />
                Acepto los Términos y Condiciones
              </label>
              <div className="rounded-lg border border-dashed border-borde bg-stone-50 p-3">
                <p className="m-0 text-[0.8rem] leading-snug text-texto-suave">
                  <strong>Versión provisoria.</strong> Al crear tu cuenta confirmás que sos
                  mayor de 18 años, que los datos cargados son verdaderos y que tus clases
                  quedan cubiertas por el protocolo de seguridad de la plataforma. El texto
                  completo se publicará antes del lanzamiento.
                </p>
              </div>

              <div className="flex gap-3">
                <button
                  type="button"
                  className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                  onClick={() => setPaso(2)}
                >
                  Volver
                </button>
                <button
                  type="submit"
                  className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
                  disabled={enviando}
                >
                  {enviando ? "Creando cuenta…" : "Crear cuenta"}
                </button>
              </div>
            </form>
          </>
        )}

        {menorDeEdad && (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
            <strong>Sos menor de edad.</strong> Un Adulto Responsable debe
            crearte el perfil. No se creó ninguna cuenta.
          </div>
        )}

        {error && (
          <div className="mt-4 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
            {error}
          </div>
        )}

        <p className="mt-5 text-center text-[0.9rem] text-texto-suave">
          ¿Ya tenés cuenta? <Link href="/login">Iniciar sesión</Link>
          <br />
          ¿Querés dar clases?{" "}
          <Link href="/registro/tutor">Registrate como tutor</Link>
        </p>

        <div className="mt-7 flex gap-[0.35rem]" aria-label="Progreso del registro">
          {PASOS.map((nombrePaso, i) => (
            <div
              key={nombrePaso}
              className={i <= paso ? "h-1 flex-1 rounded bg-accent" : "h-1 flex-1 rounded bg-borde"}
              title={nombrePaso}
            />
          ))}
        </div>
        <div className="mt-[0.4rem] text-center text-[0.75rem] text-texto-suave">
          Paso {paso + 1} de {PASOS.length}: {PASOS[paso]}
        </div>
      </div>
    </main>
  );
}