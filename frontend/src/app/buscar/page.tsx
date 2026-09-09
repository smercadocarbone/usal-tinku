"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { clearSession, getSession } from "@/lib/auth";

interface ResultadoBusqueda {
  tutorId: string;
  score: number;
  noAutorizado: boolean;
}

interface TutorBasico {
  id: string;
  nombre: string;
  apellido: string;
}

interface BusquedaGuardada {
  id: string;
  textoBusqueda: string;
  createdAt: string;
}

export default function BuscarPage() {
  const router = useRouter();
  const session = getSession();
  const payload = session?.payload;

  const [texto, setTexto] = useState("");
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultados, setResultados] = useState<ResultadoBusqueda[]>([]);
  const [tutores, setTutores] = useState<Map<string, TutorBasico>>(new Map());
  const [buscado, setBuscado] = useState(false);

  const [guardadas, setGuardadas] = useState<BusquedaGuardada[]>([]);
  const [cargandoGuardadas, setCargandoGuardadas] = useState(false);
  const [mostrarGuardadas, setMostrarGuardadas] = useState(false);

  async function buscar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!texto.trim()) return;

    setBuscando(true);
    setError(null);
    setResultados([]);
    setTutores(new Map());
    setBuscado(false);

    try {
      const resultados = await api.post<ResultadoBusqueda[]>(
        "/api/busquedas",
        { textoBusqueda: texto.trim() }
      );
      setResultados(resultados);
      setBuscado(true);

      const tutorIds = [...new Set(resultados.map((r) => r.tutorId))];
      const nuevos = new Map<string, TutorBasico>();
      await Promise.allSettled(
        tutorIds.map(async (id) => {
          try {
            const t = await api.get<TutorBasico>(`/api/tutores/${id}`);
            nuevos.set(id, t);
          } catch {
            nuevos.set(id, { id, nombre: "Tutor", apellido: id.slice(0, 8) });
          }
        })
      );
      setTutores(nuevos);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo completar la busqueda.");
      }
    } finally {
      setBuscando(false);
    }
  }

  async function guardarBusqueda() {
    if (!texto.trim()) return;
    try {
      const guardada = await api.post<BusquedaGuardada>(
        "/api/busquedas/guardadas",
        { textoBusqueda: texto.trim() }
      );
      setGuardadas((prev) => [guardada, ...prev]);
    } catch {
      // silencioso — no es crítico
    }
  }

  async function cargarGuardadas() {
    setCargandoGuardadas(true);
    try {
      const lista = await api.get<BusquedaGuardada[]>(
        "/api/busquedas/guardadas"
      );
      setGuardadas(lista);
      setMostrarGuardadas(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      }
    } finally {
      setCargandoGuardadas(false);
    }
  }

  async function ejecutarGuardada(guardada: BusquedaGuardada) {
    setTexto(guardada.textoBusqueda);
    setMostrarGuardadas(false);
    setBuscando(true);
    setError(null);
    setResultados([]);
    setTutores(new Map());
    setBuscado(false);

    try {
      const resultados = await api.post<ResultadoBusqueda[]>(
        `/api/busquedas/guardadas/${guardada.id}/ejecutar`
      );
      setResultados(resultados);
      setBuscado(true);

      const tutorIds = [...new Set(resultados.map((r) => r.tutorId))];
      const nuevos = new Map<string, TutorBasico>();
      await Promise.allSettled(
        tutorIds.map(async (id) => {
          try {
            const t = await api.get<TutorBasico>(`/api/tutores/${id}`);
            nuevos.set(id, t);
          } catch {
            nuevos.set(id, { id, nombre: "Tutor", apellido: id.slice(0, 8) });
          }
        })
      );
      setTutores(nuevos);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      }
    } finally {
      setBuscando(false);
    }
  }

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
        <nav style={{ display: "flex", gap: "1rem", alignItems: "center" }}>
          <Link href="/cuenta" style={{ fontSize: "0.9rem" }}>
            Mi cuenta
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
        <h1 style={{ fontSize: "1.3rem", letterSpacing: "-0.01em" }}>
          Buscar tutores
        </h1>
        <p style={{ color: "var(--color-texto-suave)", marginTop: 0 }}>
          Describe lo que necesitas y encontramos al Tutor mas relevante.
        </p>

        <form
          onSubmit={buscar}
          style={{ display: "flex", gap: "0.5rem", marginBottom: "1.5rem" }}
        >
          <input
            type="text"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Ej: clases de matematica para secundario"
            required
            maxLength={500}
            style={{
              flex: 1,
              padding: "0.65rem 0.75rem",
              border: "1px solid var(--color-borde)",
              borderRadius: "8px",
              fontSize: "1rem",
              background: "var(--color-superficie)",
              color: "var(--color-texto)",
            }}
          />
          <button
            type="submit"
            className="boton"
            disabled={buscando || !texto.trim()}
          >
            {buscando ? "Buscando..." : "Buscar"}
          </button>
        </form>

        <div style={{ display: "flex", gap: "0.75rem", marginBottom: "1.5rem" }}>
          <button
            type="button"
            className="boton boton--secundario"
            onClick={guardarBusqueda}
            disabled={!texto.trim() || buscando}
            style={{ fontSize: "0.85rem", padding: "0.5rem 0.75rem" }}
          >
            Guardar busqueda
          </button>
          <button
            type="button"
            className="boton boton--secundario"
            onClick={cargarGuardadas}
            disabled={cargandoGuardadas}
            style={{ fontSize: "0.85rem", padding: "0.5rem 0.75rem" }}
          >
            {cargandoGuardadas ? "Cargando..." : "Ver guardadas"}
          </button>
        </div>

        {mostrarGuardadas && (
          <div style={{ marginBottom: "1.5rem" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                marginBottom: "0.5rem",
              }}
            >
              <h2 style={{ fontSize: "1rem", margin: 0 }}>Guardadas</h2>
              <button
                type="button"
                onClick={() => setMostrarGuardadas(false)}
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--color-texto-suave)",
                  cursor: "pointer",
                  fontSize: "0.85rem",
                }}
              >
                Cerrar
              </button>
            </div>
            {guardadas.length === 0 ? (
              <p
                style={{
                  color: "var(--color-texto-suave)",
                  fontSize: "0.9rem",
                  margin: 0,
                }}
              >
                No tenes busquedas guardadas.
              </p>
            ) : (
              <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
                {guardadas.map((g) => (
                  <li
                    key={g.id}
                    style={{
                      padding: "0.6rem 0",
                      borderBottom: "1px solid var(--color-borde)",
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                    }}
                  >
                    <span style={{ fontSize: "0.9rem" }}>{g.textoBusqueda}</span>
                    <button
                      type="button"
                      onClick={() => ejecutarGuardada(g)}
                      style={{
                        background: "none",
                        border: "none",
                        color: "var(--color-accent)",
                        cursor: "pointer",
                        fontSize: "0.85rem",
                        fontWeight: 600,
                      }}
                    >
                      Ejecutar
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}

        {error && (
          <div className="alerta alerta--error" role="alert" style={{ marginBottom: "1rem" }}>
            {error}
          </div>
        )}

        {buscado && resultados.length === 0 && (
          <div
            style={{
              textAlign: "center",
              padding: "2rem 1rem",
              color: "var(--color-texto-suave)",
            }}
          >
            No se encontraron tutores para esa busqueda.
          </div>
        )}

        {resultados.length > 0 && (
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {resultados.map((r) => {
              const tutor = tutores.get(r.tutorId);
              return (
                <li
                  key={r.tutorId}
                  style={{
                    padding: "1rem",
                    marginBottom: "0.5rem",
                    background: "var(--color-superficie)",
                    border: "1px solid var(--color-borde)",
                    borderRadius: "var(--radio)",
                    display: "flex",
                    justifyContent: "space-between",
                    alignItems: "center",
                  }}
                >
                  <div>
                    <div style={{ fontWeight: 600 }}>
                      {tutor
                        ? `${tutor.nombre} ${tutor.apellido}`
                        : "Cargando..."}
                    </div>
                    <div
                      style={{
                        fontSize: "0.8rem",
                        color: "var(--color-texto-suave)",
                        marginTop: "0.15rem",
                      }}
                    >
                      Relevancia: {Math.round(r.score * 100)}%
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                    {r.noAutorizado && (
                      <span
                        style={{
                          fontSize: "0.75rem",
                          color: "var(--color-aviso)",
                          fontWeight: 600,
                        }}
                      >
                        No autorizado
                      </span>
                    )}
                    <Link
                      href={`/tutores/${r.tutorId}`}
                      className="boton boton--secundario"
                      style={{
                        fontSize: "0.85rem",
                        padding: "0.4rem 0.75rem",
                        textDecoration: "none",
                      }}
                    >
                      Ver perfil
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </main>
    </>
  );
}
