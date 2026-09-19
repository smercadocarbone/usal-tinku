"use client";

import { useState } from "react";
import { actualizarPrecioRegional, mensajeDeError, type PrecioRegional } from "@/lib/api";
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
