"use client";

import { useEffect } from "react";

/**
 * Último recurso: se usa solo si el error ocurre en el propio `layout` raíz,
 * donde `error.tsx` ya no puede montarse. Por eso reemplaza `<html>`/`<body>`
 * enteros y NO puede apoyarse en la librería de UI ni en `globals.css` — tiene
 * que sostenerse con estilos inline, sin asumir que el CSS cargó.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("[tinku] error global:", error);
  }, [error]);

  return (
    <html lang="es-AR">
      <body
        style={{
          margin: 0,
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          fontFamily:
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif",
          background: "#f8fafc",
          color: "#1e293b",
        }}
      >
        <main style={{ maxWidth: "32rem", padding: "2rem", textAlign: "center" }}>
          <p style={{ fontWeight: 700, fontSize: "1.125rem" }}>
            Tinku<span style={{ color: "#0d9488" }}>.</span>
          </p>
          <h1 style={{ fontSize: "1.25rem", marginTop: "1.5rem" }}>
            La aplicación no pudo cargar
          </h1>
          <p style={{ color: "#64748b", marginTop: "0.5rem" }}>
            Reintentá en un momento. Si sigue pasando, avisanos.
          </p>
          {error.digest && (
            <p style={{ color: "#64748b", fontSize: "0.75rem", marginTop: "1rem" }}>
              Código de referencia: {error.digest}
            </p>
          )}
          <button
            type="button"
            onClick={reset}
            style={{
              marginTop: "1.5rem",
              cursor: "pointer",
              borderRadius: "0.5rem",
              border: "none",
              background: "#0d9488",
              color: "#fff",
              fontWeight: 600,
              padding: "0.625rem 1rem",
            }}
          >
            Reintentar
          </button>
        </main>
      </body>
    </html>
  );
}
