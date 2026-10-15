"use client";

import { useEffect, useRef, useState } from "react";
import {
  getArchivoCap,
  getColaCap,
  mensajeDeError,
  revisarCap,
  verificarFirmaCap,
  type AccionRevisionCap,
  type CapCola,
  type CategoriaAntecedenteCap,
  type EstadoFirmaCap,
} from "@/lib/api";
import { Alerta, Boton, CampoSelect, Cargando, Insignia, Tarjeta, type TonoInsignia } from "@/components/ui";

/** ADR-M1-10: estado de la firma digital según el validador oficial. */
const FIRMA: Record<EstadoFirmaCap, { texto: string; tono: TonoInsignia }> = {
  NO_VERIFICADA: { texto: "Firma sin verificar", tono: "neutro" },
  PENDIENTE: { texto: "Firma: en cola del validador", tono: "info" },
  EN_PROCESO: { texto: "Firma: verificando", tono: "info" },
  VALIDA: { texto: "Firma digital válida", tono: "exito" },
  INVALIDA: { texto: "Firma inválida", tono: "peligro" },
  FIRMANTE_NO_RECONOCIDO: { texto: "Firmante no reconocido", tono: "peligro" },
  ERROR: { texto: "No se pudo verificar la firma", tono: "aviso" },
};

/** Con el validador encendido, solo se aprueba con la firma válida (el backend lo exige igual). */
function firmaBloqueaAprobar(estado: EstadoFirmaCap): boolean {
  return estado !== undefined && estado !== "VALIDA" && estado !== "NO_VERIFICADA";
}

/** Qué informa el certificado (BR-CAP-01 y BR-CAP-02, Spec_M1). */
const CATEGORIAS: { value: "" | CategoriaAntecedenteCap; label: string }[] = [
  { value: "", label: "No informa antecedentes" },
  { value: "INTEGRIDAD_SEXUAL", label: "Contra la integridad sexual (abuso, grooming, etc.) — rechazo" },
  { value: "VINCULADO_A_MENORES", label: "Delito vinculado a menores — rechazo" },
  { value: "HOMICIDIO", label: "Homicidio o tentativa — rechazo" },
  { value: "OTRO", label: "Otro antecedente o proceso en trámite — revisión legal" },
];

function fecha(iso: string): string {
  return new Date(iso.length === 10 ? `${iso}T12:00:00` : iso).toLocaleDateString("es-AR");
}

/**
 * T03 §2.4: cola del CAP para Moderación y Seguridad. Lo que se elige en "qué informa"
 * manda sobre el botón (el backend lo aplica igual): un antecedente de la lista rechaza,
 * cualquier otro queda en revisión legal y no habilita.
 */
export default function ColaCap() {
  const [cola, setCola] = useState<CapCola[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [categorias, setCategorias] = useState<Record<string, "" | CategoriaAntecedenteCap>>({});
  const [procesando, setProcesando] = useState<string | null>(null);
  const [documento, setDocumento] = useState<{ id: string; url: string } | null>(null);
  const urlAbierta = useRef<string | null>(null);

  function cargar() {
    setError(null);
    getColaCap()
      .then(setCola)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) setProhibido(true);
        else setError(mensajeDeError(err, "No se pudo cargar la cola de certificados."));
        setCola([]);
      });
  }

  useEffect(cargar, []);
  useEffect(() => () => {
    if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
  }, []);

  async function verDocumento(id: string) {
    if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
    urlAbierta.current = null;
    if (documento?.id === id) return setDocumento(null);
    try {
      const url = URL.createObjectURL(await getArchivoCap(id));
      urlAbierta.current = url;
      setDocumento({ id, url });
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo abrir el certificado."));
    }
  }

  async function volverAVerificar(c: CapCola) {
    setProcesando(c.id);
    setError(null);
    try {
      const actualizado = await verificarFirmaCap(c.id);
      setCola((prev) => (prev ? prev.map((x) => (x.id === c.id ? actualizado : x)) : prev));
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo pedir la verificación de la firma."));
    } finally {
      setProcesando(null);
    }
  }

  async function revisar(c: CapCola, accion: AccionRevisionCap) {
    setProcesando(c.id);
    setError(null);
    try {
      await revisarCap(c.id, accion, categorias[c.id] || null);
      setCola((prev) => (prev ? prev.filter((x) => x.id !== c.id) : prev));
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo registrar la revisión."));
    } finally {
      setProcesando(null);
    }
  }

  if (cola === null) return <Cargando>Cargando certificados…</Cargando>;
  if (prohibido) return <p className="text-sm text-slate-500">No tenés permiso de Moderación y Seguridad para ver esta cola.</p>;

  return (
    <div className="flex flex-col gap-3">
      {error && <Alerta tono="error">{error}</Alerta>}
      {cola.length === 0 ? (
        <p className="text-sm text-slate-500">No hay certificados para revisar.</p>
      ) : (
        <ul className="flex flex-col gap-3">
          {cola.map((c) => (
            <Tarjeta as="li" key={c.id} className="flex flex-col gap-3 p-5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold text-slate-800">{c.tutorNombre} {c.tutorApellido}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    Emitido {fecha(c.fechaEmision)} · vence {fecha(c.venceAt)} · intento {c.numeroIntento} · enviado {fecha(c.createdAt)}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Insignia tono={(FIRMA[c.firmaEstado] ?? FIRMA.NO_VERIFICADA).tono}>{(FIRMA[c.firmaEstado] ?? FIRMA.NO_VERIFICADA).texto}</Insignia>
                  {c.estado === "EN_REVISION_LEGAL" && <Insignia tono="aviso">En revisión legal</Insignia>}
                </div>
              </div>
              {c.firmaEstado && c.firmaEstado !== "NO_VERIFICADA" && (
                <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-600">
                  <p className="min-w-0">
                    {c.firmaFirmante && <span className="block font-medium text-slate-700">Firmante: {c.firmaFirmante}</span>}
                    {c.firmaDetalle && <span className="block">{c.firmaDetalle}</span>}
                    {c.firmaSha256 && (
                      <span className="block break-all font-mono" title="Hash del archivo que se verificó">SHA-256: {c.firmaSha256}</span>
                    )}
                  </p>
                  {(c.firmaEstado === "ERROR" || c.firmaEstado === "INVALIDA" || c.firmaEstado === "FIRMANTE_NO_RECONOCIDO") && (
                    <Boton variante="secundario" tamano="sm" disabled={procesando === c.id} onClick={() => void volverAVerificar(c)}>
                      Volver a verificar
                    </Boton>
                  )}
                </div>
              )}
              <div className="flex flex-wrap items-end gap-2">
                <Boton variante="secundario" tamano="sm" onClick={() => void verDocumento(c.id)}>
                  {documento?.id === c.id ? "Ocultar certificado" : "Ver certificado"}
                </Boton>
                <div className="min-w-64 flex-1">
                  <CampoSelect
                    id={`categoria-${c.id}`}
                    etiqueta="Qué informa el certificado"
                    value={categorias[c.id] ?? ""}
                    onChange={(e) => setCategorias((p) => ({ ...p, [c.id]: e.target.value as "" | CategoriaAntecedenteCap }))}
                  >
                    {CATEGORIAS.map((o) => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </CampoSelect>
                </div>
                <Boton
                  tamano="sm"
                  disabled={procesando === c.id || (!categorias[c.id] && firmaBloqueaAprobar(c.firmaEstado))}
                  title={!categorias[c.id] && firmaBloqueaAprobar(c.firmaEstado) ? "Para aprobar, la firma digital tiene que estar verificada." : undefined}
                  onClick={() => void revisar(c, "APROBAR")}
                >
                  Registrar revisión
                </Boton>
                <Boton variante="peligro" tamano="sm" disabled={procesando === c.id} onClick={() => void revisar(c, "RECHAZAR")}>
                  Rechazar (documento inválido)
                </Boton>
              </div>
              {documento?.id === c.id && (
                // Sin sandbox: Chrome bloquea su visor de PDF dentro de un iframe con sandbox; el
                // backend ya verificó que es un PDF y lo sirve como application/pdf.
                // oxlint-disable-next-line react/iframe-missing-sandbox
                <iframe src={documento.url} title={`Certificado de ${c.tutorNombre} ${c.tutorApellido}`} className="h-[70vh] w-full rounded-lg border border-slate-200" />
              )}
            </Tarjeta>
          ))}
        </ul>
      )}
    </div>
  );
}
