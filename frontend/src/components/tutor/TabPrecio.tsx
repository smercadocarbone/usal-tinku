"use client";

import { useEffect, useRef, useState } from "react";
import { Eye, TrendingUp } from "lucide-react";
import { api } from "@/lib/api";
import { formatearPesos } from "@/lib/formatos";
import { Alerta, Selector, useToast } from "@/components/ui";

const PROVINCIAS = [
  "Buenos Aires",
  "Catamarca",
  "Chaco",
  "Chubut",
  "Ciudad Autónoma de Buenos Aires",
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

interface ReferenciaRegional {
  provincia: string;
  valorSugerido: number;
}

interface Tarifa {
  precioSesion: number;
}

/**
 * Precio por clase del tutor (M5 US-6, UX-06 §4). Se guarda solo (PUT
 * /api/pagos/tarifa, `precioSesion` en camelCase — B14) y se congela en cada
 * reserva. El valor actual sale del backend (GET /api/pagos/tarifa), nunca de
 * localStorage. "Por clase": hasta FASE2-01 la tarifa es por sesión, no por hora.
 */
export default function TabPrecio() {
  const toast = useToast();
  const [precio, setPrecio] = useState<string>("");
  const [cargado, setCargado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [provincia, setProvincia] = useState("");
  const [referencia, setReferencia] = useState<ReferenciaRegional | null>(null);
  const guardado = useRef<number | null>(null);

  useEffect(() => {
    api
      .get<Tarifa | undefined>("/api/pagos/tarifa")
      .then((t) => {
        if (t?.precioSesion) {
          guardado.current = Number(t.precioSesion);
          setPrecio(String(Number(t.precioSesion)));
        }
      })
      .catch(() => undefined)
      .finally(() => setCargado(true));
  }, []);

  // Referencia regional (M5 US-6): si no hay para la provincia, no se muestra nada.
  useEffect(() => {
    if (!provincia) {
      setReferencia(null);
      return;
    }
    let vivo = true;
    api
      .get<ReferenciaRegional | undefined>(`/api/pagos/precio-referencia/${encodeURIComponent(provincia)}`)
      .then((r) => vivo && setReferencia(r ?? null))
      .catch(() => vivo && setReferencia(null));
    return () => {
      vivo = false;
    };
  }, [provincia]);

  const numero = precio === "" ? null : Number(precio);
  useEffect(() => {
    if (!cargado || numero === null || !Number.isFinite(numero) || numero <= 0 || numero === guardado.current) return;
    const id = window.setTimeout(() => {
      api
        .put("/api/pagos/tarifa", { precioSesion: numero })
        .then(() => {
          guardado.current = numero;
          setError(null);
          toast.mostrar("Guardamos tu precio");
        })
        .catch(() => setError("No pudimos guardar el precio. Probá cambiando el valor de nuevo."));
    }, 700);
    return () => window.clearTimeout(id);
  }, [numero, cargado, toast]);

  return (
    <section aria-label="Precio" className="flex flex-col gap-6">
      <div>
        <label htmlFor="precio" className="text-sm font-bold">
          Precio por clase (ARS)
        </label>
        <div className="mt-2 flex max-w-xs items-center rounded-control border border-borde-control bg-superficie px-4 focus-within:border-marca-700 focus-within:ring-4 focus-within:ring-marca-100">
          <span aria-hidden className="text-3xl font-extrabold text-tinta-tenue">
            $
          </span>
          <input
            id="precio"
            type="number"
            min={1}
            step={100}
            inputMode="numeric"
            value={precio}
            placeholder="0"
            disabled={!cargado}
            onChange={(e) => setPrecio(e.target.value)}
            className="tabular min-h-16 w-full bg-transparent px-2 text-3xl font-extrabold text-tinta focus:outline-none"
            aria-describedby="precio-ayuda"
          />
        </div>
        <p id="precio-ayuda" className="mt-2 text-[13px] text-tinta-tenue">
          Se guarda solo. Cada reserva congela el precio del momento en que se hizo.
        </p>
        {error && (
          <Alerta tono="peligro" className="mt-3">
            {error}
          </Alerta>
        )}
      </div>

      {numero !== null && numero > 0 && (
        <div className="flex items-start gap-3 rounded-2xl bg-fondo p-4">
          <Eye className="mt-0.5 size-5 shrink-0 text-marca-700" aria-hidden />
          <p className="text-[15px]">
            Así lo ven las familias: <strong>{formatearPesos(numero)} por clase</strong>.
          </p>
        </div>
      )}

      <div className="max-w-sm">
        <Selector id="provincia" etiqueta="Tu provincia (para ver el precio de referencia)" value={provincia} onChange={(e) => setProvincia(e.target.value)}>
          <option value="">Elegí tu provincia</option>
          {PROVINCIAS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </Selector>
      </div>
      {referencia && (
        <Alerta tono="info" titulo={`Precio de referencia en ${referencia.provincia}`} sinIcono>
          <span className="flex items-center gap-2">
            <TrendingUp className="size-4" aria-hidden /> Ronda los {formatearPesos(referencia.valorSugerido)} por clase.
          </span>
        </Alerta>
      )}
    </section>
  );
}
