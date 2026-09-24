"use client";

import { useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ApiError, resetearPassword } from "@/lib/api";
import { Alerta, Boton, Campo, RequisitosPassword } from "@/components/ui";
import { LARGO_MINIMO_PASSWORD } from "@/lib/password";
import PantallaAuth from "@/components/auth/PantallaAuth";

export default function ResetearPasswordPage() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState(false);

  // B3: leer window.location en el render produce mismatch de hidratación
  // (el servidor pinta sin token → el cliente pinta con token). Con
  // useSyncExternalStore el snapshot de servidor es estable ("") y recién en
  // el cliente se lee el query string, sin error de hidratación.
  const token = useSyncExternalStore(
    () => () => {},
    () => new URLSearchParams(window.location.search).get("token") ?? "",
    () => "",
  );

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setError(null);

    if (password !== confirmacion) {
      setError("Las contraseñas no coinciden.");
      return;
    }
    if (!token) {
      setError("El enlace no es válido. Solicitá uno nuevo.");
      return;
    }

    setEnviando(true);
    try {
      await resetearPassword(token, password);
      setExito(true);
      setTimeout(() => router.replace("/login"), 2000);
    } catch (err) {
      setError(
        err instanceof ApiError && err.message
          ? err.message
          : "No se pudo cambiar la contraseña. Intentá de nuevo."
      );
    } finally {
      setEnviando(false);
    }
  }

  return (
    <PantallaAuth>
        <h1 className="text-[32px] font-extrabold">Elegí una contraseña nueva</h1>

        {!token && (
          <Alerta tono="error" className="mt-6">
            Este enlace ya no es válido.{" "}
            <Link href="/recuperar-password" className="font-semibold underline">Pedí uno nuevo</Link>.
          </Alerta>
        )}

        {exito ? (
          <Alerta tono="exito" className="mt-4">
            Tu contraseña se actualizó. Te llevamos a iniciar sesión…
          </Alerta>
        ) : (
          token && (
            <form className="mt-6 flex flex-col gap-5" onSubmit={onSubmit}>
              <Campo
                id="passwordNueva"
                etiqueta="Contraseña nueva"
                variante="password"
                autoComplete="new-password"
                required
                minLength={LARGO_MINIMO_PASSWORD}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              {password && <RequisitosPassword password={password} />}
              <Campo
                id="passwordConfirmacion"
                etiqueta="Repetí la contraseña"
                variante="password"
                autoComplete="new-password"
                required
                minLength={LARGO_MINIMO_PASSWORD}
                value={confirmacion}
                onChange={(e) => setConfirmacion(e.target.value)}
              />

              {error && <Alerta tono="error">{error}</Alerta>}

              <Boton type="submit" tamano="lg" anchoCompleto cargando={enviando} textoCargando="Guardando…">
                Guardar contraseña nueva
              </Boton>
            </form>
          )
        )}
    </PantallaAuth>
  );
}
