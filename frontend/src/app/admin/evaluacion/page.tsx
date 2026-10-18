"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import { Alerta, Boton, Campo, Cargando, Tarjeta } from "@/components/ui";
import { mensajeDeError } from "@/lib/api";
import { getIndicadoresEvaluacion, type IndicadoresEvaluacion } from "@/lib/evaluacion";

function porcentaje(v: number | null): string {
  return v === null ? "Sin datos" : `${(v * 100).toLocaleString("es-AR", { maximumFractionDigits: 1 })} %`;
}

function decimal(v: number | null, digitos = 2): string {
  return v === null ? "Sin datos" : v.toLocaleString("es-AR", { maximumFractionDigits: digitos });
}

function Indicador({ titulo, valor, detalle, meta }: { titulo: string; valor: string; detalle: string; meta: string }) {
  return (
    <Tarjeta as="article">
      <h3 className="text-sm font-semibold text-tinta-suave">{titulo}</h3>
      <p className="mt-1 text-3xl font-extrabold">{valor}</p>
      <p className="mt-1 text-sm">{detalle}</p>
      <p className="mt-2 text-xs text-tinta-suave">Meta: {meta}</p>
    </Tarjeta>
  );
}

/**
 * T-TES-11b §4.3: indicadores de las sesiones moderadas que reemplazan al piloto. Solo lectura,
 * cualquier Admin. Los números son agregados (sin datos por persona).
 */
export default function AdminEvaluacionPage() {
  const [desde, setDesde] = useState("");
  const [hasta, setHasta] = useState("");
  const [datos, setDatos] = useState<IndicadoresEvaluacion | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const consultar = useCallback((d: string, h: string) => {
    setCargando(true);
    setError(null);
    getIndicadoresEvaluacion(d || undefined, h || undefined)
      .then(setDatos)
      .catch((err) => setError(mensajeDeError(err, "No pudimos calcular los indicadores.")))
      .finally(() => setCargando(false));
  }, []);

  useEffect(() => {
    consultar("", "");
  }, [consultar]);

  return (
    <section>
      <h2 className="text-2xl font-bold">Evaluación</h2>
      <p className="mt-1 text-sm text-tinta-suave">
        Indicadores de las sesiones moderadas con usuarios (búsquedas, aceptación de sugerencias, recomendación y
        calificaciones).
      </p>

      <form
        className="mt-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          consultar(desde, hasta);
        }}
      >
        <Campo id="eval-desde" etiqueta="Desde" type="date" value={desde} onChange={(e) => setDesde(e.target.value)} />
        <Campo id="eval-hasta" etiqueta="Hasta" type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} />
        <Boton type="submit">Ver período</Boton>
      </form>

      <div className="mt-6">
        {cargando ? (
          <Cargando>Calculando indicadores…</Cargando>
        ) : error || !datos ? (
          <Alerta tono="error" className="flex items-start gap-2">
            <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
            <span>{error ?? "No pudimos calcular los indicadores."}</span>
          </Alerta>
        ) : (
          <>
            {!datos.habilitada && (
              <Alerta tono="info" className="mb-4" titulo="La evaluación está apagada">
                No se están registrando búsquedas ni respuestas nuevas. Los números son de lo que se registró mientras
                estuvo encendida.
              </Alerta>
            )}
            <div className="grid gap-4 sm:grid-cols-2">
              <Indicador
                titulo="Aceptación de sugerencias (I-01)"
                valor={porcentaje(datos.i01.tasa)}
                detalle={`${datos.i01.busquedasConAceptacion} de ${datos.busquedas.conResultado} búsquedas con resultado: se abrió el perfil o se reservó un tutor de los primeros ${datos.topK}.`}
                meta="más de 60 %"
              />
              <Indicador
                titulo="Recomendación (NPS)"
                valor={decimal(datos.nps.valor, 1)}
                detalle={`${datos.nps.respuestas} respuestas: ${datos.nps.promotores} promotores, ${datos.nps.pasivos} pasivos, ${datos.nps.detractores} detractores.`}
                meta="positivo"
              />
              <Indicador
                titulo="Calidad percibida (I-02)"
                valor={datos.i02.promedio === null ? "Sin datos" : `${decimal(datos.i02.promedio)} / 5`}
                detalle={`${datos.i02.calificaciones} calificaciones de alumnos a tutores en el período.`}
                meta="4,0 o más"
              />
              <Indicador
                titulo="Búsquedas sin resultado"
                valor={String(datos.busquedas.sinResultado)}
                detalle={`de ${datos.busquedas.total} búsquedas registradas.`}
                meta="se compara con la evaluación del buscador"
              />
            </div>

            <h3 className="mt-6 text-lg font-bold">Aceptaciones por posición</h3>
            {datos.i01.porPosicion.length === 0 ? (
              <p className="mt-2 text-sm text-tinta-suave">Todavía no hay aceptaciones.</p>
            ) : (
              <table className="mt-2 w-full max-w-md text-left text-sm">
                <thead>
                  <tr className="border-b border-borde">
                    <th className="py-2 font-semibold">Posición en la lista</th>
                    <th className="py-2 font-semibold">Búsquedas</th>
                  </tr>
                </thead>
                <tbody>
                  {datos.i01.porPosicion.map((p) => (
                    <tr key={p.posicion} className="border-b border-borde">
                      <td className="py-2">{p.posicion}</td>
                      <td className="py-2">{p.cantidad}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <p className="mt-2 text-sm text-tinta-suave">
              Perfiles abiertos: {datos.i01.aceptacionesPerfil} · Reservas: {datos.i01.aceptacionesReserva}
            </p>
          </>
        )}
      </div>
    </section>
  );
}
