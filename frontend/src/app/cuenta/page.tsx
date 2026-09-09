"use client";

import { useRouter } from "next/navigation";
import Link from "next/link";
import { clearSession, getSession } from "@/lib/auth";

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

export default function CuentaPage() {
  const router = useRouter();
  const session = getSession();
  const payload = session?.payload;

  function logout() {
    clearSession();
    router.replace("/");
  }

  return (
    <>
      <header className="cabecera">
        <div className="marca" style={{ marginBottom: 0 }}>
          Tinku<span>.</span>
        </div>
        <button type="button" className="boton boton--secundario" onClick={logout}>
          Cerrar sesión
        </button>
      </header>

      <main className="contenido">
        <h1>Mi cuenta</h1>
        <p>
          Tu espacio en Tinku. Busca un tutor, reserva una clase y segui tus
          reservas.
        </p>

        <div
          style={{
            display: "grid",
            gap: "0.75rem",
            margin: "1.5rem 0",
          }}
        >
          <Link
            href="/buscar"
            className="boton"
            style={{ textDecoration: "none", textAlign: "center" }}
          >
            Buscar tutores
          </Link>
          <Link
            href="/cuenta/reservas"
            className="boton boton--secundario"
            style={{ textDecoration: "none", textAlign: "center" }}
          >
            Mis reservas
          </Link>
        </div>

        <dl>
          <div className="perfil-fila">
            <dt>DNI</dt>
            <dd>{payload?.sub ?? "—"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Tipo de cuenta</dt>
            <dd>{payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Capacidad Estudiante</dt>
            <dd>{payload?.cap_est ? "Activa" : "Inactiva"}</dd>
          </div>
          <div className="perfil-fila">
            <dt>Adulto Responsable</dt>
            <dd>{payload?.cap_ar ? "Activa" : "Inactiva"}</dd>
          </div>
        </dl>
      </main>
    </>
  );
}