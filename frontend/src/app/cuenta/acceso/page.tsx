"use client";

import { LARGO_MINIMO_PASSWORD } from "@/lib/password";
import { useEffect, useState, type FormEvent } from "react";
import {
  actualizarEmail,
  cambiarPassword,
  getPerfilPropio,
  mensajeDeError,
  type PerfilPropio,
} from "@/lib/api";
import { RequisitosPassword, Alerta, Boton, Campo, Cargando, Tarjeta } from "@/components/ui";

export default function CuentaAccesoPage() {
  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  const [cargandoPerfil, setCargandoPerfil] = useState(true);

  const [nuevoEmail, setNuevoEmail] = useState("");
  const [guardandoEmail, setGuardandoEmail] = useState(false);
  const [errorEmail, setErrorEmail] = useState<string | null>(null);
  const [exitoEmail, setExitoEmail] = useState(false);

  const [passwordActual, setPasswordActual] = useState("");
  const [passwordNueva, setPasswordNueva] = useState("");
  const [guardandoPassword, setGuardandoPassword] = useState(false);
  const [errorPassword, setErrorPassword] = useState<string | null>(null);
  const [exitoPassword, setExitoPassword] = useState(false);

  useEffect(() => {
    getPerfilPropio()
      .then((p) => {
        setPerfil(p);
        setNuevoEmail(p.email ?? "");
      })
      .catch(() => setPerfil(null))
      .finally(() => setCargandoPerfil(false));
  }, []);

  async function onSubmitEmail(e: FormEvent) {
    e.preventDefault();
    setErrorEmail(null);
    setExitoEmail(false);
    setGuardandoEmail(true);
    try {
      const p = await actualizarEmail(nuevoEmail);
      setPerfil(p);
      setExitoEmail(true);
    } catch (err) {
      setErrorEmail(mensajeDeError(err, "No se pudo actualizar el email."));
    } finally {
      setGuardandoEmail(false);
    }
  }

  async function onSubmitPassword(e: FormEvent) {
    e.preventDefault();
    setErrorPassword(null);
    setExitoPassword(false);
    setGuardandoPassword(true);
    try {
      await cambiarPassword(passwordActual, passwordNueva);
      setExitoPassword(true);
      setPasswordActual("");
      setPasswordNueva("");
    } catch (err) {
      setErrorPassword(mensajeDeError(err, "No se pudo cambiar la contraseña."));
    } finally {
      setGuardandoPassword(false);
    }
  }

  return (
    <section>
      <h2 className="text-2xl font-bold">Seguridad y acceso</h2>
      <p className="mt-1 text-[15px] text-tinta-suave">Ingresás con tu DNI y tu contraseña. El email lo usamos para cosas de tu cuenta.</p>

      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-2">
        <Tarjeta>
          <h3 className="mb-4 text-lg font-bold">Email</h3>
          {cargandoPerfil ? (
            <Cargando>Cargando…</Cargando>
          ) : (
            <form className="flex flex-col gap-4" onSubmit={onSubmitEmail}>
              <Campo
                id="nuevoEmail"
                etiqueta="Email"
                type="email"
                autoComplete="email"
                required
                value={nuevoEmail}
                onChange={(e) => setNuevoEmail(e.target.value)}
              />
              {errorEmail && <Alerta tono="peligro">{errorEmail}</Alerta>}
              {exitoEmail && <Alerta tono="exito">Email actualizado.</Alerta>}
              <Boton
                type="submit"
                className="w-fit"
                cargando={guardandoEmail}
                textoCargando="Guardando…"
                disabled={!nuevoEmail || nuevoEmail === perfil?.email}
              >
                Guardar email
              </Boton>
            </form>
          )}
        </Tarjeta>

        <Tarjeta>
          <h3 className="mb-4 text-lg font-bold">Contraseña</h3>
          <form className="flex flex-col gap-4" onSubmit={onSubmitPassword}>
            <Campo
              id="passwordActual"
              etiqueta="Contraseña actual"
              variante="password"
              autoComplete="current-password"
              required
              value={passwordActual}
              onChange={(e) => setPasswordActual(e.target.value)}
            />
            <Campo
              id="passwordNueva"
              etiqueta="Contraseña nueva"
              variante="password"
              autoComplete="new-password"
              required
              minLength={LARGO_MINIMO_PASSWORD}
              value={passwordNueva}
              onChange={(e) => setPasswordNueva(e.target.value)}
            />
            {passwordNueva && <RequisitosPassword password={passwordNueva} />}
            {errorPassword && <Alerta tono="peligro">{errorPassword}</Alerta>}
            {exitoPassword && <Alerta tono="exito">Contraseña actualizada.</Alerta>}
            <Boton
              type="submit"
              className="w-fit"
              cargando={guardandoPassword}
              textoCargando="Guardando…"
            >
              Cambiar contraseña
            </Boton>
          </form>
        </Tarjeta>
      </div>
    </section>
  );
}
