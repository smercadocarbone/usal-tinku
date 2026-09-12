"use client";

import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { AlertCircle, CloudCheck, Loader2, TrendingUp } from "lucide-react";

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
const ESTADO_INICIAL =
  typeof localStorage === "undefined" ? null : localStorage.getItem("tinku_precio");

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
  const [precio, setPrecio] = useState<string | null>(ESTADO_INICIAL);
  const [estado, setEstado] = useState<EstadoGuardado>("idle");
  const [mensajeError, setMensajeError] = useState("");
  const [provincia, setProvincia] = useState(PROVINCIA_POR_DEFECTO);
  const [referencia, setReferencia] = useState<ReferenciaRegional | null>(null);
  const [cargandoReferencia, setCargandoReferencia] = useState(false);
  const [referenciaAusente, setReferenciaAusente] = useState(false);
  const primeraCarga = useRef(true);

  // Sugerencia regional por provincia (M5 US-6).
  useEffect(() => {
    let activo = true;
    setCargandoReferencia(true);
    setReferenciaAusente(false);
    api
      .get<ReferenciaRegional>(`/api/pagos/precio-referencia/${encodeURIComponent(provincia)}`)
      .then((r) => {
        if (!activo) return;
        setReferencia(r);
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
    if (primeraCarga.current) {
      primeraCarga.current = false;
      return;
    }
    if (precioNumerico === null || !Number.isFinite(precioNumerico)) {
      setEstado("idle");
      return;
    }
    setEstado("guardando");
    const id = setTimeout(() => {
      api
        .put("/api/pagos/tarifa", { precio_sesion: precioNumerico })
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
      <h2 className="text-lg font-bold text-texto">Configuración de precio</h2>
      <p className="text-[0.9rem] text-texto-suave">
        Fijá cuánto cobrás por sesión. Se guarda solo y ese valor se congela en
        cada reserva que aceptes.
      </p>

      <div className="mt-4 flex items-end gap-3">
        <label className="flex flex-col gap-1 text-[0.85rem] font-semibold text-texto">
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
              className="w-40 rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-2xl font-bold text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1"
            />
            <span aria-hidden className="text-xl font-bold text-texto-suave">
              $
            </span>
          </div>
        </label>
        <span
          className="mb-2 flex items-center gap-1 text-[0.85rem] text-texto-suave"
          role="status"
        >
          {estado === "guardando" && (
            <>
              <Loader2 className="animate-spin" size={16} /> Guardando…
            </>
          )}
          {estado === "ok" && (
            <>
              <CloudCheck className="text-exito" size={16} /> Guardado automático
            </>
          )}
        </span>
      </div>

      {estado === "error" && (
        <div
          className="mt-2 flex w-fit items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro"
          role="alert"
        >
          <AlertCircle size={16} /> {mensajeError}
        </div>
      )}

      <label className="mt-5 flex flex-col gap-1 text-[0.85rem] font-semibold text-texto">
        Tu provincia (para la sugerencia de precio)
        <select
          value={provincia}
          onChange={(e) => setProvincia(e.target.value)}
          className="w-72 rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1"
        >
          {PROVINCIAS.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </label>

      {cargandoReferencia ? (
        <p className="mt-3 text-[0.85rem] text-texto-suave">Buscando referencia regional…</p>
      ) : rango ? (
        <div className="mt-4 rounded-xl border border-blue-100 bg-blue-50 p-4 text-blue-800">
          <p className="flex items-start gap-2 text-[0.9rem] font-medium">
            <TrendingUp className="mt-[0.1rem] shrink-0" size={18} />
            <span>
              Sugerencia inteligente: según el poder adquisitivo de tu región, los
              tutores de tu nivel cobran entre {formatear(rango[0])} y{" "}
              {formatear(rango[1])}. Ajustar tu precio a este rango puede aumentar
              tus reservas.
            </span>
          </p>
        </div>
      ) : referenciaAusente ? (
        <p className="mt-3 text-[0.85rem] text-texto-suave">
          Todavía no tenemos referencia de precios para {provincia}. Este dato no
          es obligatorio para publicar tus tutorías.
        </p>
      ) : null}
    </section>
  );
}