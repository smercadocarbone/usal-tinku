"use client";

import { useEffect, useState } from "react";
import { actualizarPrecioRegional, getPreciosRegionales, mensajeDeError, type PrecioRegional } from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";
import { Alerta, Boton, Campo, CampoSelect, EstadoVacio } from "@/components/ui";

const PROVINCIAS = [
  "Buenos Aires",
  "CABA",
  "Catamarca",
  "Chaco",
  "Chubut",
  "Córdoba",
  "Corrientes",
  "Entre Ríos",
  "Formosa",
  "Jujuy",
  "La Pampa",
  "La Rioja",
  "Mendoza",
  "Misiones",
  "Neuquén",
  "Río Negro",
  "Salta",
  "San Juan",
  "San Luis",
  "Santa Cruz",
  "Santa Fe",
  "Santiago del Estero",
  "Tierra del Fuego",
  "Tucumán",
];

export default function PreciosRegionales() {
  const [provincia, setProvincia] = useState(PROVINCIAS[0]);
  const [valorSugerido, setValorSugerido] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ultimaVersion, setUltimaVersion] = useState<PrecioRegional | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [vigentes, setVigentes] = useState<PrecioRegional[] | null>(null);

  // R-F F-9 (T5-H4): la tabla vigente; vacía, se explica qué cargar.
  useEffect(() => {
    getPreciosRegionales()
      .then(setVigentes)
      .catch(() => setVigentes([]));
  }, [ultimaVersion]);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const valor = Number(valorSugerido);
    if (!valor || valor <= 0) {
      setError("Ingresá un valor sugerido mayor a 0.");
      return;
    }
    setEnviando(true);
    setError(null);
    setUltimaVersion(null);
    try {
      const resultado = await actualizarPrecioRegional(provincia, valor);
      setUltimaVersion(resultado);
      setValorSugerido("");
    } catch (err) {
      if (err && typeof err === "object" && "status" in err && err.status === 403) {
        setProhibido(true);
      } else {
        setError(mensajeDeError(err, "No se pudo guardar el precio de referencia."));
      }
    } finally {
      setEnviando(false);
    }
  }

  if (prohibido) {
    return (
      <EstadoVacio>
        No tenés permiso de Soporte Financiero para editar precios regionales.
      </EstadoVacio>
    );
  }

  return (
    <div className="max-w-md">
      {vigentes !== null &&
        (vigentes.length === 0 ? (
          <Alerta tono="info" className="mb-4">
            Todavía no hay precios cargados. Cargá al menos el de cada provincia donde haya Tutores: es la
            referencia que ven al ponerse una tarifa.
          </Alerta>
        ) : (
          <ul className="mb-4 list-none p-0 text-sm">
            {vigentes.map((p) => (
              <li key={p.provincia} className="flex justify-between border-b border-slate-200 py-2">
                <span>{p.provincia}</span>
                <span className="tabular-nums">
                  {formatearPrecio(p.valorSugerido)} · v{p.version}
                </span>
              </li>
            ))}
          </ul>
        ))}
      <p className="mb-4 text-sm text-slate-500">
        Cada envío agrega una versión nueva para la provincia elegida — nunca
        sobreescribe la vigente. Un Tutor que ya fijó su precio contra una
        versión anterior no se ve afectado (FR-PAG-006).
      </p>

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <CampoSelect
          id="provincia"
          etiqueta="Provincia"
          value={provincia}
          onChange={(e) => setProvincia(e.target.value)}
        >
          {PROVINCIAS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </CampoSelect>

        <Campo
          id="valorSugerido"
          etiqueta="Valor sugerido (por hora)"
          type="number"
          min={0}
          step="0.01"
          required
          value={valorSugerido}
          onChange={(e) => setValorSugerido(e.target.value)}
        />

        {error && <Alerta tono="error">{error}</Alerta>}

        {ultimaVersion && (
          <Alerta tono="exito">
            {ultimaVersion.provincia}: versión {ultimaVersion.version} —{" "}
            {formatearPrecio(ultimaVersion.valorSugerido)} vigente desde ahora.
          </Alerta>
        )}

        <Boton
          type="submit"
          className="w-fit"
          cargando={enviando}
          textoCargando="Guardando…"
        >
          Publicar nueva versión
        </Boton>
      </form>
    </div>
  );
}
