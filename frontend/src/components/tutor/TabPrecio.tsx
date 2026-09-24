"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { TrendingUp } from "lucide-react";
import { Alerta, IndicadorGuardado } from "@/components/ui";

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

const PROVINCIA_POR_DEFECTO = "Buenos Aires";
const CLAVE_PRECIO_LOCAL = "tinku_precio";

type EstadoGuardado = "idle" | "guardando" | "ok" | "error";

interface ReferenciaRegional {
  provincia: string;
  valorSugerido: number;
  version: number;
  vigenteDesde: string;
}

function redondearA100(valor: number): number {
  return Math.round(valor / 100) * 100;
}

/** Rango sugerido a partir del valor de referencia único del backend. */
function rangoSugerido(valor: number): [number, number] {
  return [redondearA100(valor * 0.85), redondearA100(valor * 1.15)];
}

export default function TabPrecio() {
  const [precio, setPrecio] = useState<string | null>(null);
  const [estado, setEstado] = useState<EstadoGuardado>("idle");
  const [mensajeError, setMensajeError] = useState("");
  const [provincia, setProvincia] = useState(PROVINCIA_POR_DEFECTO);
  const [referencia, setReferencia] = useState<ReferenciaRegional | null>(null);
  const [cargandoReferencia, setCargandoReferencia] = useState(false);
  const [referenciaAusente, setReferenciaAusente] = useState(false);
  // B3: localStorage solo se lee tras el montaje, nunca en el render.
  // `listo` evita que el auto-guardado corra por el setPrecio del restore.
  const listo = useRef(false);
  const precioRestaurado = useRef<number | null>(null);

  useEffect(() => {
    const guardado = localStorage.getItem(CLAVE_PRECIO_LOCAL);
    if (guardado !== null) {
      const n = Number(guardado);
      if (Number.isFinite(n)) {
        precioRestaurado.current = n;
        setPrecio(guardado);
      }
    }
    listo.current = true;
  }, []);

  // Sugerencia regional por provincia (M5 US-6).
  useEffect(() => {
    let activo = true;
    setCargandoReferencia(true);
    setReferenciaAusente(false);
    api
      .get<ReferenciaRegional>(`/api/pagos/precio-referencia/${encodeURIComponent(provincia)}`)
      .then((r) => {
        if (!activo) return;
        if (r) {
          setReferencia(r);
        } else {
          // 204 = sin referencia para la provincia (B11): estado vacío
          // esperado, no un error.
          setReferencia(null);
          setReferenciaAusente(true);
        }
      })
      .catch(() => {
        if (!activo) return;
        setReferencia(null);
        setReferenciaAusente(true);
      })
      .finally(() => {
        if (activo) setCargandoReferencia(false);
      });
    return () => {
      activo = false;
    };
  }, [provincia]);

  // Auto-guardado del precio (PU /api/pagos/tarifa, FR-PAG-006).
  const precioNumerico = precio === null || precio === "" ? null : Number(precio);
  useEffect(() => {
    if (!listo.current || precioNumerico === precioRestaurado.current) {
      return;
    }
    if (precioNumerico === null || !Number.isFinite(precioNumerico)) {
      setEstado("idle");
      return;
    }
    setEstado("guardando");
    const id = setTimeout(() => {
      api
        .put("/api/pagos/tarifa", { precioSesion: precioNumerico })
        .then(() => {
          setMensajeError("");
          setEstado("ok");
        })
        .catch(() => {
          setMensajeError(
            "No se pudo guardar el precio. Intentá de nuevo cambiando el valor."
          );
          setEstado("error");
        });
    }, 500);
    return () => clearTimeout(id);
  }, [precioNumerico]);

  const rango = referencia ? rangoSugerido(referencia.valorSugerido) : null;
  const formatear = (v: number) => `$${v.toLocaleString("es-AR")}`;

  return (
    <section aria-label="Configuración de precio">
      <h2 className="text-lg font-bold text-slate-800">Configuración de precio</h2>
      <p className="text-sm text-slate-500">
        Fijá cuánto cobrás por sesión. Se guarda solo y ese valor se congela en
        cada reserva que aceptes.
      </p>

      <div className="mt-4 flex items-end gap-3">
        <label className="flex flex-col gap-1 text-sm font-semibold text-slate-800">
          Precio por sesión (ARS)
          <div className="flex items-center gap-2">
            <input
              type="number"
              min={1}
              step={100}
              inputMode="numeric"
              value={precio ?? ""}
              placeholder="0"
              onChange={(e) => setPrecio(e.target.value === "" ? "" : e.target.value)}
              className="w-40 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-2xl font-bold text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
            />
            <span aria-hidden className="text-xl font-bold text-slate-500">
              $
            </span>
          </div>
        </label>
        {(estado === "guardando" || estado === "ok") && (
          <IndicadorGuardado estado={estado} className="mb-2" />
        )}
      </div>

      {estado === "error" && (
        <IndicadorGuardado estado="error" mensajeError={mensajeError} className="mt-2" />
      )}

      <label className="mt-5 flex flex-col gap-1 text-sm font-semibold text-slate-800">
        Tu provincia (para la sugerencia de precio)
        <select
          value={provincia}
          onChange={(e) => setProvincia(e.target.value)}
          className="w-72 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
        >
          {PROVINCIAS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </label>

      {cargandoReferencia ? (
        <p className="mt-3 text-sm text-slate-500">Buscando referencia regional…</p>
      ) : rango ? (
        <Alerta tono="dato" className="mt-4 flex items-start gap-2 font-medium">
          <TrendingUp className="mt-0.5 shrink-0" size={18} aria-hidden />
          <span>
            Sugerencia inteligente: según el poder adquisitivo de tu región, los
            tutores de tu nivel cobran entre {formatear(rango[0])} y{" "}
            {formatear(rango[1])}. Ajustar tu precio a este rango puede aumentar
            tus reservas.
          </span>
        </Alerta>
      ) : referenciaAusente ? (
        <p className="mt-3 text-sm text-slate-500">
          Todavía no tenemos referencia de precios para {provincia}. Este dato no
          es obligatorio para publicar tus tutorías.
        </p>
      ) : null}
    </section>
  );
}