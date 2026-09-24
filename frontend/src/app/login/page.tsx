"use client";

import { useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError } from "@/lib/api";
import { inicioPorRol, payloadDeToken } from "@/lib/auth";
import { iniciarSesion, siguienteSeguro } from "@/lib/sesion";
import { Alerta, Boton, Campo } from "@/components/ui";
import PantallaAuth from "@/components/auth/PantallaAuth";

export default function LoginPage() {
  const router = useRouter();
  const [dni, setDni] = useState("");
  const [password, setPassword] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // B3: el query se lee con useSyncExternalStore (snapshot de servidor estable).
  const queryParams = useSyncExternalStore(
    () => () => {},
    () => window.location.search,
    () => ""
  );
  const query = new URLSearchParams(queryParams);
  const expirado = query.get("expirado") === "1";
  const registrado = query.get("registrado") === "1";
  const tutorRegistrado = query.get("tutorRegistrado") === "1";
  const siguiente = siguienteSeguro(query.get("siguiente"));
  const vieneDeReserva = !!siguiente && (siguiente.startsWith("/reservar") || siguiente.startsWith("/tutores"));

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setEnviando(true);
    setError(null);
    try {
      const token = await iniciarSesion(dni, password);
      router.replace(siguiente ?? inicioPorRol(payloadDeToken(token)));
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) {
        setError(err.message || "Demasiados intentos. Probá de nuevo en unos minutos.");
      } else if (err instanceof ApiError && err.status === 403) {
        setError(err.message || "Tu cuenta no está habilitada para ingresar.");
      } else if (err instanceof ApiError && (err.status === 401 || err.status === 400)) {
        // Genérico a propósito: no revelar si el DNI existe.
        setError("DNI o contraseña incorrectos.");
      } else {
        setError(
          err instanceof ApiError && err.message
            ? err.message
            : "No pudimos conectarnos. Revisá tu conexión y probá de nuevo."
        );
      }
    } finally {
      setEnviando(false);
    }
  }

  const hrefRegistro = siguiente ? `/registro?siguiente=${encodeURIComponent(siguiente)}` : "/registro";

  return (
    <PantallaAuth
      pie={
        <>
          ¿No tenés cuenta? <Link href={hrefRegistro} className="font-semibold">Creala en 3 minutos</Link>
        </>
      }
    >
      <h1 className="text-[32px] font-extrabold">Ingresá a Tinku</h1>
      <p className="mt-2 text-[16px] text-tinta-suave">
        {vieneDeReserva ? "Ingresá para continuar con tu reserva." : "Con tu DNI y tu contraseña."}
      </p>

      <div className="mt-6 flex flex-col gap-3">
        {expirado && <Alerta tono="aviso">Tu sesión expiró. Ingresá de nuevo para continuar.</Alerta>}
        {registrado && <Alerta tono="exito">Cuenta creada. Ya podés ingresar.</Alerta>}
        {tutorRegistrado && <Alerta tono="exito">Cuenta de tutor creada. Ingresá con tu DNI y contraseña.</Alerta>}
      </div>

      <form className="mt-6 flex flex-col gap-5" onSubmit={onSubmit}>
        <Campo
          id="dni"
          etiqueta="DNI"
          variante="dni"
          autoComplete="username"
          required
          value={dni}
          onValor={setDni}
          placeholder="12.345.678"
        />
        <Campo
          id="password"
          etiqueta="Contraseña"
          variante="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <div className="-mt-2 text-right">
          <Link href="/recuperar-password" className="text-sm font-semibold">
            ¿Olvidaste tu contraseña?
          </Link>
        </div>

        {error && <Alerta tono="peligro">{error}</Alerta>}

        <Boton type="submit" tamano="lg" anchoCompleto cargando={enviando} textoCargando="Ingresando…">
          Ingresar
        </Boton>
      </form>
    </PantallaAuth>
  );
}
