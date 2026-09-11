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
    <main className="pantalla">
      <div className="tarjeta tarjeta--ancha">
        <div className="marca">
          Tinku<span>.</span>
        </div>

        {paso === 0 && (
          <>
            <h1>¿Quién va a usar Tinku?</h1>
            <p>Elegí de qué lado estás para armarte la cuenta correcta.</p>

            <div className="rol-opciones" role="group" aria-label="Tipo de cuenta">
              <button
                type="button"
                className={rol === "adulto" ? "rol-opcion rol-opcion--seleccionada" : "rol-opcion"}
                aria-pressed={rol === "adulto"}
                onClick={() => setRol("adulto")}
              >
                <strong>Soy mayor de edad</strong>
                <span>Quiero tomar clases o tengo un menor a cargo.</span>
              </button>

              <button
                type="button"
                className={rol === "tutor" ? "rol-opcion rol-opcion--seleccionada" : "rol-opcion"}
                aria-pressed={rol === "tutor"}
                onClick={() => setRol("tutor")}
              >
                <strong>Soy Tutor</strong>
                <span>Quiero dar clases y ofrecer mis tutorías.</span>
              </button>
            </div>

            <button type="button" className="boton" disabled={!rol} onClick={() => setPaso(1)}>
              Continuar
            </button>
          </>
        )}

        {paso === 1 && (
          <>
            <h1>Tus datos</h1>
            <p>Así figura en tu DNI. Los verificamos después con su foto.</p>

            <form
              className="formulario"
              onSubmit={(e) => {
                e.preventDefault();
                setPaso(2);
              }}
            >
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem" }}>
                <div className="campo">
                  <label htmlFor="nombre">Nombre</label>
                  <input
                    id="nombre"
                    type="text"
                    autoComplete="given-name"
                    required
                    value={nombre}
                    onChange={(e) => setNombre(e.target.value)}
                  />
                </div>
                <div className="campo">
                  <label htmlFor="apellido">Apellido</label>
                  <input
                    id="apellido"
                    type="text"
                    autoComplete="family-name"
                    required
                    value={apellido}
                    onChange={(e) => setApellido(e.target.value)}
                  />
                </div>
              </div>

              <div className="campo">
                <label htmlFor="dni">DNI</label>
                <input
                  id="dni"
                  type="text"
                  inputMode="numeric"
                  autoComplete="username"
                  required
                  value={dni}
                  onChange={(e) => setDni(e.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="fechaNacimiento">Fecha de nacimiento</label>
                <input
                  id="fechaNacimiento"
                  type="date"
                  autoComplete="bday"
                  required
                  value={fechaNacimiento}
                  onChange={(e) => setFechaNacimiento(e.target.value)}
                />
              </div>

              {rol === "adulto" && (
                <div className="opciones" role="group" aria-label="Para qué vas a usar Tinku">
                  <p>¿Para qué vas a usar Tinku?</p>
                  <label className="opcion">
                    <input
                      type="checkbox"
                      checked={capacidadEstudiante}
                      onChange={(e) => setCapacidadEstudiante(e.target.checked)}
                    />
                    Tomar clases para mí
                  </label>
                  <label className="opcion">
                    <input
                      type="checkbox"
                      checked={capacidadAdultoResponsable}
                      onChange={(e) => setCapacidadAdultoResponsable(e.target.checked)}
                    />
                    Gestionar clases para un menor a mi cargo
                  </label>
                  {!capacidadEstudiante && !capacidadAdultoResponsable && (
                    <span className="nota nota--alerta">
                      Elegí al menos una opción para continuar.
                    </span>
                  )}
                </div>
              )}

              <div style={{ display: "flex", gap: "0.75rem" }}>
                <button type="button" className="boton boton--secundario" onClick={() => setPaso(0)}>
                  Volver
                </button>
                <button
                  type="submit"
                  className="boton"
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
            <h1>Verificá tu identidad</h1>
            <p>Subí una foto de tu DNI (frente). Confirmamos tus datos y tu edad.</p>

            <div className="formulario">
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

              <div className="campo">
                <label htmlFor="fotoRostro">Foto de tu cara</label>
                <input
                  id="fotoRostro"
                  type="file"
                  accept="image/*"
                  disabled
                />
                <span className="nota">
                  La comparación facial es un paso que se habilita próximamente.
                </span>
              </div>

              <div style={{ display: "flex", gap: "0.75rem" }}>
                <button type="button" className="boton boton--secundario" onClick={() => setPaso(1)}>
                  Volver
                </button>
                <button type="button" className="boton" disabled={verificando || !fotoDni} onClick={verificarDni}>
                  {verificando ? "Verificando…" : "Verificar"}
                </button>
              </div>
            </div>
          </>
        )}

        {paso === 3 && (
          <>
            <h1>Creá tu acceso</h1>
            <p>Tu identidad fue verificada. Faltan tus credenciales.</p>

            <form className="formulario" onSubmit={crearCuenta}>
              <div className="campo">
                <label htmlFor="email">Email</label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="password">Contraseña</label>
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

              <div className="separador">o</div>

              <button type="button" className="boton boton--secundario" disabled>
                Continuar con Google
              </button>
              <span className="nota">Ingreso con Google disponible próximamente.</span>

              <label className="opcion">
                <input
                  type="checkbox"
                  checked={aceptaTerminos}
                  required
                  onChange={(e) => setAceptaTerminos(e.target.checked)}
                />
                Acepto los Términos y Condiciones
              </label>
              <div className="terminos">
                <p>
                  <strong>Versión provisoria.</strong> Al crear tu cuenta confirmás que sos
                  mayor de 18 años, que los datos cargados son verdaderos y que tus clases
                  quedan cubiertas por el protocolo de seguridad de la plataforma. El texto
                  completo se publicará antes del lanzamiento.
                </p>
              </div>

              <div style={{ display: "flex", gap: "0.75rem" }}>
                <button type="button" className="boton boton--secundario" onClick={() => setPaso(2)}>
                  Volver
                </button>
                <button type="submit" className="boton" disabled={enviando}>
                  {enviando ? "Creando cuenta…" : "Crear cuenta"}
                </button>
              </div>
            </form>
          </>
        )}

        {menorDeEdad && (
          <div className="alerta alerta--informativa" role="status">
            <strong>Sos menor de edad.</strong> Un Adulto Responsable debe
            crearte el perfil. No se creó ninguna cuenta.
          </div>
        )}

        {error && (
          <div className="alerta alerta--error" role="alert">
            {error}
          </div>
        )}

        <p className="pie-enlace">
          ¿Ya tenés cuenta? <Link href="/login">Iniciar sesión</Link>
          <br />
          ¿Querés dar clases?{" "}
          <Link href="/registro/tutor">Registrate como tutor</Link>
        </p>

        <div className="progreso" aria-label="Progreso del registro">
          {PASOS.map((nombrePaso, i) => (
            <div
              key={nombrePaso}
              className={i <= paso ? "progreso-segmento progreso-segmento--activo" : "progreso-segmento"}
              title={nombrePaso}
            />
          ))}
        </div>
        <div className="progreso-etiqueta">
          Paso {paso + 1} de {PASOS.length}: {PASOS[paso]}
        </div>
      </div>
    </main>
  );
}