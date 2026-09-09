"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";

export default function RegistroPage() {
  const router = useRouter();

  const [dniDeclarado, setDniDeclarado] = useState("");
  const [nombreDeclarado, setNombreDeclarado] = useState("");
  const [apellidoDeclarado, setApellidoDeclarado] = useState("");
  const [fechaNacimientoDeclarada, setFechaNacimientoDeclarada] = useState("");
  const [password, setPassword] = useState("");
  const [capacidadEstudiante, setCapacidadEstudiante] = useState(true);
  const [capacidadAdultoResponsable, setCapacidadAdultoResponsable] =
    useState(false);
  const [fotoDni, setFotoDni] = useState<File | null>(null);

  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [menorDeEdad, setMenorDeEdad] = useState(false);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    setMenorDeEdad(false);

    if (!fotoDni) {
      setError("Subí una foto de tu DNI.");
      setEnviando(false);
      return;
    }

    const datos = {
      dniDeclarado,
      nombreDeclarado,
      apellidoDeclarado,
      fechaNacimientoDeclarada,
      password,
      capacidadEstudiante,
      capacidadAdultoResponsable,
    };

    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);

    try {
      await api.post("/api/usuarios/registro", form);
      router.replace("/login?registrado=1");
    } catch (err) {
      if (err instanceof ApiError) {
        switch (err.status) {
          case 403:
            // US-1: el OCR determinó que es menor de edad → pantalla informativa.
            setMenorDeEdad(true);
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
        setError("No se pudo completar el registro. Intentá de nuevo.");
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="pantalla">
      <div className="tarjeta tarjeta--ancha">
        <div className="marca">
          Tinku<span>.</span>
        </div>
        <h1>Crear mi cuenta</h1>
        <p>
Verificamos tu identidad con la foto de tu DNI y que seas mayor de 18
        años para acceder a las clases.
        </p>

        {menorDeEdad && (
          <div className="alerta alerta--informativa" role="status">
            <strong>Sos menor de edad.</strong> Un Adulto Responsable debe
            crearte el perfil. No se creó ninguna cuenta.
          </div>
        )}

        <form className="formulario" onSubmit={onSubmit}>
          <div className="campo">
            <label htmlFor="dniDeclarado">DNI</label>
            <input
              id="dniDeclarado"
              type="text"
              inputMode="numeric"
              autoComplete="username"
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

          <div className="opciones" role="group" aria-label="Capacidades">
            <p>¿Qué vas a usar Tinku?</p>
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
            {enviando ? "Verificando…" : "Crear cuenta"}
          </button>
        </form>

        <p className="pie-enlace">
          ¿Ya tenés cuenta? <Link href="/login">Iniciar sesión</Link>
        </p>
      </div>
    </main>
  );
}