"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import { clearSession, getSession } from "@/lib/auth";

interface TutorPerfil {
  id: string;
  nombre: string;
  apellido: string;
  tipo: string;
  capacidadEstudiante: boolean;
  capacidadAdultoResponsable: boolean;
  materias: string[];
  nivel: string;
  calificacionPromedio: number | null;
  cantidadCalificaciones: number;
  precioHora?: number | null;
}

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

export default function TutorPerfilPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const session = getSession();
  const payload = session?.payload;

  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [noConfiable, setNoConfiable] = useState(false);
  const [enviandoNoConfiable, setEnviandoNoConfiable] = useState(false);
  const [mensajeNoConfiable, setMensajeNoConfiable] = useState<string | null>(null);
  const [mostrarAvisoMenores, setMostrarAvisoMenores] = useState(true);

  useEffect(() => {
    let activo = true;
    api
      .get<TutorPerfil>(`/api/tutores/${params.id}`)
      .then((p) => activo && setPerfil(p))
      .catch((err) => {
        if (err instanceof ApiError) {
          setError(
            err.status === 404
              ? "Tutor no encontrado."
              : err.message || "No se pudo cargar el perfil."
          );
        } else {
          setError("No se pudo cargar el perfil.");
        }
      })
      .finally(() => activo && setCargando(false));
    return () => {
      activo = false;
    };
  }, [params.id]);

  function logout() {
    clearSession();
    router.replace("/");
  }

  const esMenor = payload?.tipo === "MENOR";
  const puedeReservar = !esMenor;
  const esAdultoConAR = !esMenor && payload?.cap_ar === true;

  async function toggleNoConfiable(nuevoValor: boolean) {
    setEnviandoNoConfiable(true);
    setMensajeNoConfiable(null);
    setError(null);
    try {
      await api.patch("/api/autorizaciones/no-confiable", {
        tutorId: params.id,
        noConfiable: nuevoValor,
      });
      setNoConfiable(nuevoValor);
      setMensajeNoConfiable(
        nuevoValor
          ? "Tutor marcado como no confiable. Ya no aparece en los resultados de matching de tu cuenta."
          : "Tutor desmarcado como no confiable."
      );
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message || "No se pudo actualizar el estado de confianza.");
      } else {
        setError("No se pudo actualizar el estado de confianza.");
      }
    } finally {
      setEnviandoNoConfiable(false);
    }
  }

  return (
    <>
      <header className="cabecera">
        <div className="marca" style={{ marginBottom: 0 }}>
          Tinku<span>.</span>
        </div>
        <nav style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          <Link href="/buscar" style={{ fontSize: "0.9rem" }}>
            Buscar
          </Link>
          <button
            type="button"
            className="boton boton--secundario"
            onClick={logout}
          >
            Cerrar sesion
          </button>
        </nav>
      </header>

      <main className="contenido">
        {cargando && (
          <p style={{ color: "var(--color-texto-suave)" }}>Cargando perfil...</p>
        )}

        {error && (
          <div className="alerta alerta--error" role="alert">
            {error}
          </div>
        )}

        {perfil && (
          <div>
            <div
              className="tarjeta"
              style={{
                maxWidth: "none",
                padding: "2rem",
                marginBottom: "1rem",
              }}
            >
              <h1 style={{ fontSize: "1.6rem", marginBottom: "0.5rem" }}>
                {perfil.nombre} {perfil.apellido}
              </h1>

              {perfil.materias.length > 0 && (
                <div style={{ marginBottom: "1rem" }}>
                  {perfil.materias.map((m) => (
                    <span
                      key={m}
                      style={{
                        display: "inline-block",
                        margin: "0 0.35rem 0.35rem 0",
                        padding: "0.25rem 0.6rem",
                        fontSize: "0.8rem",
                        fontWeight: 600,
                        background: "#f0fdfa",
                        color: "var(--color-accent)",
                        borderRadius: "999px",
                      }}
                    >
                      {m}
                    </span>
                  ))}
                </div>
              )}

              <dl style={{ margin: 0 }}>
                {perfil.nivel && (
                  <div className="perfil-fila">
                    <dt>Nivel</dt>
                    <dd style={{ textTransform: "none" }}>{perfil.nivel}</dd>
                  </div>
                )}
                {typeof perfil.precioHora === "number" && (
                  <div className="perfil-fila">
                    <dt>Precio por hora</dt>
                    <dd style={{ textTransform: "none" }}>
                      ${perfil.precioHora.toLocaleString("es-AR")}
                    </dd>
                  </div>
                )}
                <div className="perfil-fila">
                  <dt>Calificacion</dt>
                  <dd style={{ textTransform: "none" }}>
                    {perfil.calificacionPromedio !== null &&
                    perfil.cantidadCalificaciones >= 5
                      ? `${perfil.calificacionPromedio.toFixed(1)} (${perfil.cantidadCalificaciones})`
                      : "Sin calificaciones suficientes"}
                  </dd>
                </div>
              </dl>
            </div>

            {puedeReservar ? (
              <Link
                href={`/reservar?tutor=${perfil.id}`}
                className="boton"
                style={{
                  textDecoration: "none",
                  display: "inline-block",
                }}
              >
                Reservar clase
              </Link>
            ) : (
              <div className="alerta alerta--informativa" role="status">
                Pedile a tu adulto responsable que te autorice a esta tutora/o.
              </div>
            )}

            {esAdultoConAR && (
              <div style={{ marginTop: "1.5rem" }}>
                <h2 style={{ fontSize: "1.1rem", marginBottom: "0.75rem" }}>
                  Autorizacion
                </h2>

                <div style={{ marginBottom: "1rem" }}>
                  <button
                    type="button"
                    className="boton"
                    onClick={() => {}}
                    disabled
                  >
                    Autorizar para mi menor
                  </button>
                  {mostrarAvisoMenores && (
                    <div
                      className="alerta alerta--informativa"
                      role="status"
                      style={{ marginTop: "0.5rem" }}
                    >
                      El listado de tus menores esta pendiente en backend. Cuando
                      este disponible, vas a poder autorizar tutores para cada
                      menor.
                    </div>
                  )}
                </div>

                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "0.75rem",
                    marginBottom: "0.5rem",
                  }}
                >
                  <label
                    htmlFor="no-confiable"
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "0.5rem",
                      cursor: enviandoNoConfiable ? "not-allowed" : "pointer",
                    }}
                  >
                    <input
                      id="no-confiable"
                      type="checkbox"
                      checked={noConfiable}
                      disabled={enviandoNoConfiable}
                      onChange={(e) => toggleNoConfiable(e.target.checked)}
                      style={{ width: "1.1rem", height: "1.1rem" }}
                    />
                    Marcar como no confiable
                  </label>
                </div>
                <p
                  style={{
                    fontSize: "0.85rem",
                    color: "var(--color-texto-suave)",
                    margin: "0 0 0.5rem 0",
                  }}
                >
                  Sacarlo de tus resultados de busqueda.
                </p>

                {mensajeNoConfiable && (
                  <div
                    className="alerta alerta--exito"
                    role="status"
                    style={{ marginTop: "0.5rem" }}
                  >
                    {mensajeNoConfiable}
                  </div>
                )}
              </div>
            )}

            {payload && (
              <p style={{ fontSize: "0.8rem", color: "var(--color-texto-suave)" }}>
                Sesion de {NOMBRE_TIPO[payload.tipo ?? ""] ?? payload.tipo ?? "usuario"}
              </p>
            )}
          </div>
        )}
      </main>
    </>
  );
}
