"use client";

import { useEffect, useState } from "react";
import { Alerta, Boton, CampoSelect, Cargando } from "@/components/ui";
import InsigniasEspecialidad from "@/components/tutores/InsigniasEspecialidad";
import { mensajeDeError } from "@/lib/api";
import {
  NIVELES,
  getMateriasCatalogo,
  getMisEspecialidades,
  guardarEspecialidades,
  type Especialidad,
  type NivelEspecialidad,
} from "@/lib/especialidades";

const MAX = 3;
type Fila = { materia: string; nivelMaximo: NivelEspecialidad | "" };

/**
 * ADR-M1-06: el Tutor elige de 1 a 3 especialidades (materia del catálogo + nivel máximo). Cada una
 * se muestra como verificada, con respaldo documental o declarada según las credenciales aprobadas.
 */
export default function EditorEspecialidades() {
  const [materias, setMaterias] = useState<string[] | null>(null);
  const [guardadas, setGuardadas] = useState<Especialidad[]>([]);
  const [filas, setFilas] = useState<Fila[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    Promise.all([getMateriasCatalogo(), getMisEspecialidades()])
      .then(([m, e]) => {
        setMaterias(m);
        setGuardadas(e);
        setFilas(e.length > 0 ? e.map((x) => ({ materia: x.materia, nivelMaximo: x.nivelMaximo })) : [{ materia: "", nivelMaximo: "" }]);
      })
      .catch((err) => setError(mensajeDeError(err, "No se pudieron cargar tus especialidades.")));
  }, []);

  function cambiar(i: number, cambio: Partial<Fila>) {
    setFilas((prev) => prev.map((f, j) => (j === i ? { ...f, ...cambio } : f)));
  }

  async function guardar() {
    if (filas.some((f) => !f.materia || !f.nivelMaximo)) {
      setError("Elegí la materia y el nivel de cada especialidad.");
      return;
    }
    setGuardando(true);
    setError(null);
    try {
      const r = await guardarEspecialidades(
        filas.map((f) => ({ materia: f.materia, nivelMaximo: f.nivelMaximo as NivelEspecialidad }))
      );
      setGuardadas(r);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudieron guardar tus especialidades."));
    } finally {
      setGuardando(false);
    }
  }

  if (materias === null && !error) return <Cargando>Cargando tus especialidades…</Cargando>;

  return (
    <section className="flex flex-col gap-4">
      <div>
        <h2 className="text-lg font-bold">Tus especialidades</h2>
        <p className="mt-1 text-sm text-tinta-suave">
          Elegí de 1 a {MAX} materias en las que sos fuerte y hasta qué nivel podés enseñarlas. Se muestran como
          verificadas cuando una credencial aprobada contra el registro oficial las respalda; si no, como declaradas.
        </p>
      </div>

      <InsigniasEspecialidad especialidades={guardadas} />

      {materias && (
        <div className="flex flex-col gap-3">
          {filas.map((f, i) => (
            <div key={i} className="flex flex-wrap items-end gap-3">
              <CampoSelect
                id={`materia-${i}`}
                etiqueta={`Materia ${i + 1}`}
                value={f.materia}
                onChange={(e) => cambiar(i, { materia: e.target.value })}
              >
                <option value="">Elegí una materia</option>
                {materias.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </CampoSelect>
              <CampoSelect
                id={`nivel-${i}`}
                etiqueta={`Nivel máximo ${i + 1}`}
                value={f.nivelMaximo}
                onChange={(e) => cambiar(i, { nivelMaximo: e.target.value as NivelEspecialidad })}
              >
                <option value="">Elegí un nivel</option>
                {NIVELES.map((n) => (
                  <option key={n.value} value={n.value}>
                    {n.label}
                  </option>
                ))}
              </CampoSelect>
              {filas.length > 1 && (
                <Boton variante="secundario" tamano="sm" onClick={() => setFilas((p) => p.filter((_, j) => j !== i))}>
                  Quitar
                </Boton>
              )}
            </div>
          ))}
          <div className="flex flex-wrap gap-2">
            {filas.length < MAX && (
              <Boton
                variante="secundario"
                tamano="sm"
                onClick={() => setFilas((p) => [...p, { materia: "", nivelMaximo: "" }])}
              >
                Agregar especialidad
              </Boton>
            )}
            <Boton tamano="sm" onClick={guardar} cargando={guardando} textoCargando="Guardando…">
              Guardar especialidades
            </Boton>
          </div>
        </div>
      )}
      {error && <Alerta tono="peligro">{error}</Alerta>}
    </section>
  );
}
