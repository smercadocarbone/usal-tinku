"use client";

import { useRouter } from "next/navigation";
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
          Esta es la primera pantalla autenticada. A medida que se implementen
          los módulos (M2+, reservas, aula) aterrizarán acá.
        </p>

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