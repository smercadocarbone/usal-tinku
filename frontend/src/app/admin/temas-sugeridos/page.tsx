"use client";

import { useEffect, useMemo, useState } from "react";
import { getTemasSugeridos, mensajeDeError, resolverTemaPedido, type TemaPedido } from "@/lib/api";
import { Alerta, Boton, Cargando, EstadoVacio, Insignia, Tarjeta } from "@/components/ui";

function rotuloArea(t: TemaPedido): string {
  if (!t.materia) return "Sin área reconocida";
  const nivel = t.nivel ? ` · ${t.nivel.charAt(0).toUpperCase()}${t.nivel.slice(1)}` : "";
  return `${t.materia}${nivel}`;
}

/**
 * FR-ADM-009: lo que se busca y el catálogo no cubre, para actualizarlo (el catálogo es vivo,
 * ADR-M2-04). Agrupado por el área que el sistema reconoció; solo lo pedido varias veces.
 */
export default function AdminTemasSugeridosPage() {
  const [temas, setTemas] = useState<TemaPedido[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [procesando, setProcesando] = useState<string | null>(null);

  useEffect(() => {
    getTemasSugeridos()
      .then(setTemas)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) setProhibido(true);
        else setError(mensajeDeError(err, "No se pudieron cargar los temas sugeridos."));
        setTemas([]);
      });
  }, []);

  const porArea = useMemo(() => {
    const grupos = new Map<string, TemaPedido[]>();
    for (const t of temas ?? []) {
      const clave = rotuloArea(t);
      grupos.set(clave, [...(grupos.get(clave) ?? []), t]);
    }
    return [...grupos.entries()];
  }, [temas]);

  async function resolver(t: TemaPedido) {
    setProcesando(t.id);
    setError(null);
    try {
      await resolverTemaPedido(t.id);
      setTemas((prev) => (prev ? prev.filter((x) => x.id !== t.id) : prev));
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo marcar el tema como resuelto."));
    } finally {
      setProcesando(null);
    }
  }

  return (
    <section>
      <h2 className="text-2xl font-bold">Temas sugeridos</h2>
      <p className="mt-1 text-sm text-slate-500">
        Lo que se busca varias veces y ningún tutor da todavía. Sirve para actualizar el catálogo; no muestra
        quién lo buscó.
      </p>
      <div className="mt-4 space-y-4">
        {error && <Alerta tono="error">{error}</Alerta>}
        {prohibido ? (
          <Alerta tono="error">Esta sección es del rol de Moderación y Seguridad.</Alerta>
        ) : temas === null ? (
          <Cargando>Cargando temas sugeridos…</Cargando>
        ) : temas.length === 0 ? (
          <EstadoVacio titulo="No hay temas sugeridos">
            Aparecen cuando algo se busca varias veces y nadie lo enseña.
          </EstadoVacio>
        ) : (
          porArea.map(([area, lista]) => (
            <Tarjeta key={area} className="p-4">
              <h3 className="font-bold">{area}</h3>
              <ul className="mt-2 divide-y divide-borde">
                {lista.map((t) => (
                  <li key={t.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <span>
                      &ldquo;{t.texto}&rdquo; <Insignia>{t.veces} {t.veces === 1 ? "vez" : "veces"}</Insignia>
                    </span>
                    <Boton
                      variante="secundario"
                      tamano="sm"
                      cargando={procesando === t.id}
                      onClick={() => void resolver(t)}
                    >
                      Resuelto
                    </Boton>
                  </li>
                ))}
              </ul>
            </Tarjeta>
          ))
        )}
      </div>
    </section>
  );
}
