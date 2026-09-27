"use client";

import { useEffect, useRef, useState } from "react";
import { CalendarDays, Eye, TrendingUp, Wallet } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { formatearPesos } from "@/lib/formatos";
import { Alerta, Boton, Selector, useToast } from "@/components/ui";

/** ADR-M5-03: tope del descuento del paquete (el backend rechaza más con 422). */
export const DESCUENTO_MAXIMO_PAQUETE = 30;

/** Precio de una clase del paquete: mismo redondeo que `ReservaService.precioDe` (HALF_UP a centavos). */
export function precioClasePaquete(precioHora: number, minutos: number, descuento: number): number {
  return Math.round((precioHora * minutos * (100 - descuento)) / 60) / 100;
}


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

/** Lo que le queda al Tutor: mismo redondeo a centavos que `ComisionPlataforma` del backend. */
export function netoPorHora(precio: number, comisionPorcentaje: number): number {
  const comision = Math.round(precio * comisionPorcentaje) / 100;
  return Math.round((precio - comision) * 100) / 100;
}

interface ReferenciaRegional {
  provincia: string;
  valorSugerido: number;
}

interface Tarifa {
  precioHora: number | null;
  /** T06: piso por hora vigente (el backend rechaza con 422 por debajo). */
  pisoHora?: number | null;
  /** FR-PAG-019: comisión de Tinku, para mostrar cuánto le queda al Tutor. */
  comisionPorcentaje?: number;
  /** FR-RES-032: el Tutor ofrece el paquete del mes y con qué descuento. */
  paqueteHabilitado?: boolean;
  paqueteDescuentoPorcentaje?: number;
}

/**
 * Precio POR HORA del tutor (M5 US-6, UX-06 §4, D6). Se guarda solo (PUT
 * /api/pagos/tarifa, `precioHora` en camelCase) y cada reserva congela
 * precioHora × minutos / 60. El valor actual sale del backend
 * (GET /api/pagos/tarifa), nunca de localStorage.
 */
export default function TabPrecio() {
  const toast = useToast();
  const [precio, setPrecio] = useState<string>("");
  const [cargado, setCargado] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [provincia, setProvincia] = useState("");
  const [referencia, setReferencia] = useState<ReferenciaRegional | null>(null);
  const [piso, setPiso] = useState<number | null>(null);
  const [comision, setComision] = useState<number | null>(null);
  // Espejo del ref para poder mostrarlo en el render (lint react/refs).
  const [precioGuardado, setPrecioGuardado] = useState<number | null>(null);
  const guardado = useRef<number | null>(null);
  const [paquete, setPaquete] = useState<{ habilitado: boolean; descuento: number }>({ habilitado: false, descuento: 0 });

  useEffect(() => {
    api
      .get<Tarifa | undefined>("/api/pagos/tarifa")
      .then((t) => {
        if (t?.pisoHora) setPiso(Number(t.pisoHora));
        if (t?.comisionPorcentaje != null) setComision(Number(t.comisionPorcentaje));
        if (t?.paqueteHabilitado != null) setPaquete({ habilitado: t.paqueteHabilitado, descuento: Number(t.paqueteDescuentoPorcentaje ?? 0) });
        if (t?.precioHora) {
          guardado.current = Number(t.precioHora);
          setPrecioGuardado(Number(t.precioHora));
          setPrecio(String(Number(t.precioHora)));
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
  const bajoPiso = numero !== null && piso !== null && numero < piso;
  // PT4: el piso no es retroactivo — una tarifa vieja por debajo sigue cobrando hasta que la edite.
  const guardadaBajoPiso = precioGuardado !== null && piso !== null && precioGuardado < piso && numero === precioGuardado;
  useEffect(() => {
    if (!cargado || numero === null || !Number.isFinite(numero) || numero <= 0 || numero === guardado.current) return;
    if (piso !== null && numero < piso) return;
    const id = window.setTimeout(() => {
      api
        .put("/api/pagos/tarifa", { precioHora: numero })
        .then(() => {
          guardado.current = numero;
          setPrecioGuardado(numero);
          setError(null);
          toast.mostrar("Guardamos tu precio");
        })
        .catch((err: unknown) => {
          const pisoDelError = err instanceof ApiError ? err.detalles?.pisoHora : undefined;
          if (err instanceof ApiError && err.status === 422 && pisoDelError != null) setPiso(Number(pisoDelError));
          else setError("No pudimos guardar el precio. Probá cambiando el valor de nuevo.");
        });
    }, 700);
    return () => window.clearTimeout(id);
  }, [numero, cargado, piso, toast]);

  return (
    <section aria-label="Precio" className="flex flex-col gap-6">
      <div>
        <label htmlFor="precio" className="text-sm font-bold">
          Precio por hora (ARS)
        </label>
        <div className="mt-2 flex max-w-xs items-center rounded-control border border-borde-control bg-superficie px-4 focus-within:border-marca-700 focus-within:ring-4 focus-within:ring-marca-100">
          <span aria-hidden className="text-3xl font-extrabold text-tinta-tenue">
            $
          </span>
          <input
            id="precio"
            type="number"
            min={piso ?? 1}
            step={100}
            inputMode="numeric"
            value={precio}
            placeholder="0"
            disabled={!cargado}
            onChange={(e) => setPrecio(e.target.value)}
            className="tabular min-h-16 w-full bg-transparent px-2 text-3xl font-extrabold text-tinta focus:outline-none"
            aria-describedby="precio-ayuda"
            aria-invalid={bajoPiso || undefined}
          />
        </div>
        <p id="precio-ayuda" className="mt-2 text-[13px] text-tinta-tenue">
          Se guarda solo. Una clase de 30 minutos cobra la mitad; cada reserva congela el precio del momento en que se hizo.
        </p>
        {piso !== null && (
          <p className="mt-1 text-[13px] text-tinta-tenue">El mínimo es {formatearPesos(piso)} por hora.</p>
        )}
        {bajoPiso && !guardadaBajoPiso && (
          <Alerta tono="peligro" className="mt-3">
            No se guardó: el precio por hora no puede ser menor a {formatearPesos(piso!)}.
          </Alerta>
        )}
        {guardadaBajoPiso && (
          <Alerta tono="aviso" className="mt-3">
            Tu precio actual quedó por debajo del mínimo de {formatearPesos(piso!)} por hora. Sigue vigente, pero para
            cambiarlo vas a tener que subirlo al menos a ese valor.
          </Alerta>
        )}
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
            Así lo ven las familias: <strong>{formatearPesos(numero)} por hora</strong>.
          </p>
        </div>
      )}

      {numero !== null && numero > 0 && comision !== null && (
        <div className="flex items-start gap-3 rounded-2xl bg-marca-50 p-4" aria-live="polite">
          <Wallet className="mt-0.5 size-5 shrink-0 text-marca-700" aria-hidden />
          <div className="text-[15px]">
            <p>
              Te quedan <strong>{formatearPesos(netoPorHora(numero, comision))} por hora</strong>
              {" "}({formatearPesos(netoPorHora(numero / 2, comision))} por una clase de 30 minutos).
            </p>
            <p className="mt-1 text-[13px] text-tinta-suave">
              Tinku se queda con el {comision} %. MercadoPago te cobra aparte su propia comisión, según el plazo de
              acreditación que tengas en tu cuenta.
            </p>
          </div>
        </div>
      )}

      {precioGuardado !== null && <PaqueteDelMes precioHora={precioGuardado} piso={piso} valor={paquete} onGuardado={setPaquete} />}

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
            <TrendingUp className="size-4" aria-hidden /> Ronda los {formatearPesos(referencia.valorSugerido)} por hora.
          </span>
        </Alerta>
      )}
    </section>
  );
}

/**
 * Paquete del mes (FR-RES-032, ADR-M5-03): 4 clases semanales pagadas juntas, con un descuento
 * opcional. Se guarda con `PUT /api/pagos/tarifa/paquete`; el backend valida tope y piso.
 */
function PaqueteDelMes({
  precioHora,
  piso,
  valor,
  onGuardado,
}: {
  precioHora: number;
  piso: number | null;
  valor: { habilitado: boolean; descuento: number };
  onGuardado: (v: { habilitado: boolean; descuento: number }) => void;
}) {
  const toast = useToast();
  const [error, setError] = useState<string | null>(null);
  const [descuento, setDescuento] = useState(String(valor.descuento));
  // Si el valor del servidor llega después del primer render, se refleja en el campo.
  const [ultimoValor, setUltimoValor] = useState(valor.descuento);
  if (ultimoValor !== valor.descuento) {
    setUltimoValor(valor.descuento);
    setDescuento(String(valor.descuento));
  }

  const guardar = (habilitado: boolean, d: number) => {
    api
      .put("/api/pagos/tarifa/paquete", { habilitado, descuentoPorcentaje: d })
      .then(() => {
        setError(null);
        onGuardado({ habilitado, descuento: d });
        toast.mostrar(habilitado ? "Guardamos tu paquete del mes" : "Ya no ofrecés el paquete del mes");
      })
      .catch((err: unknown) => {
        setError(err instanceof ApiError && err.status === 422 ? err.message : "No pudimos guardar el paquete. Probá de nuevo.");
      });
  };

  const d = descuento === "" ? 0 : Number(descuento);
  const fueraDeRango = !Number.isInteger(d) || d < 0 || d > DESCUENTO_MAXIMO_PAQUETE;
  const horaConDescuento = (precioHora * (100 - d)) / 100;
  const bajoPiso = piso !== null && horaConDescuento < piso;

  return (
    <div className="rounded-2xl border border-borde p-4">
      <label className="flex items-start gap-3" aria-label="Ofrecer el paquete del mes">
        <input
          type="checkbox"
          aria-label="Ofrecer el paquete del mes"
          className="mt-1 size-5 accent-marca-700"
          checked={valor.habilitado}
          onChange={(e) => guardar(e.target.checked, fueraDeRango || bajoPiso ? 0 : d)}
        />
        <span>
          <span className="flex items-center gap-2 font-bold">
            <CalendarDays className="size-4 text-marca-700" aria-hidden /> Ofrecer el paquete del mes
          </span>
          <span className="block text-[13px] text-tinta-tenue">
            4 clases, una por semana, en el mismo día y horario, con un solo pago. Si cancelás vos una clase, se le
            devuelve esa clase; si la cancela el alumno con menos de 24 hs, la cobrás igual.
          </span>
        </span>
      </label>
      {valor.habilitado && (
        <div className="mt-4 flex flex-wrap items-end gap-3">
          <div>
            <label htmlFor="descuento-paquete" className="text-sm font-bold">
              Descuento (%)
            </label>
            <input
              id="descuento-paquete"
              type="number"
              min={0}
              max={DESCUENTO_MAXIMO_PAQUETE}
              step={1}
              inputMode="numeric"
              value={descuento}
              onChange={(e) => setDescuento(e.target.value)}
              aria-invalid={fueraDeRango || bajoPiso || undefined}
              className="tabular mt-1 block min-h-11 w-24 rounded-control border border-borde-control bg-superficie px-3 text-lg font-bold"
            />
          </div>
          <Boton variante="secundario" disabled={fueraDeRango || bajoPiso || d === valor.descuento} onClick={() => guardar(true, d)}>
            Guardar descuento
          </Boton>
          <p className="basis-full text-[13px] text-tinta-tenue">
            Un paquete de 4 clases de 1 hora sale {formatearPesos(precioClasePaquete(precioHora, 60, fueraDeRango ? 0 : d) * 4)}.
            {" "}Hasta {DESCUENTO_MAXIMO_PAQUETE} %.
          </p>
          {fueraDeRango && (
            <Alerta tono="peligro" className="basis-full">
              El descuento va de 0 a {DESCUENTO_MAXIMO_PAQUETE} %, sin decimales.
            </Alerta>
          )}
          {!fueraDeRango && bajoPiso && (
            <Alerta tono="peligro" className="basis-full">
              Con ese descuento la hora queda por debajo del mínimo de {formatearPesos(piso!)}.
            </Alerta>
          )}
        </div>
      )}
      {error && (
        <Alerta tono="peligro" className="mt-3">
          {error}
        </Alerta>
      )}
    </div>
  );
}
