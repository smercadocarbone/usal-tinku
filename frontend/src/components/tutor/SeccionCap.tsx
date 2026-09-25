"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ShieldCheck } from "lucide-react";
import { getEstadoPerfilTutor, mensajeDeError, subirCap, type CapPropio } from "@/lib/api";
import { Alerta, Boton, Campo, Cargando, SubidaArchivo } from "@/components/ui";

function fechaLarga(iso: string): string {
  return new Date(`${iso}T12:00:00`).toLocaleDateString("es-AR", { day: "numeric", month: "long", year: "numeric" });
}

/**
 * T03: el Certificado de Antecedentes Penales es OPCIONAL y solo hace falta para dar clases
 * a menores (DT6). Un Tutor que enseña solo a adultos no tiene que hacer nada acá.
 */
export default function SeccionCap() {
  const [cap, setCap] = useState<CapPropio | null | undefined>(undefined);
  const [habilitado, setHabilitado] = useState(false);
  const [quiere, setQuiere] = useState(false);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [fechaEmision, setFechaEmision] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(() => {
    getEstadoPerfilTutor()
      .then((e) => {
        setCap(e.cap ?? null);
        setHabilitado(e.habilitadoParaMenores ?? false);
      })
      .catch(() => setCap(null));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function enviar(e: FormEvent) {
    e.preventDefault();
    if (!archivo || !fechaEmision) {
      setError("Adjuntá el PDF y la fecha de emisión que figura en el certificado.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await subirCap(archivo, fechaEmision);
      setArchivo(null);
      cargar();
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos cargar el certificado."));
    } finally {
      setEnviando(false);
    }
  }

  if (cap === undefined) return <Cargando>Cargando…</Cargando>;

  const puedeCargar = !cap || cap.estado === "RECHAZADO" || cap.estado === "VENCIDO" || (quiere && !habilitado);
  const estado = (() => {
    if (habilitado && cap) return <Alerta tono="exito">Estás habilitado para dar clases a menores hasta el {fechaLarga(cap.venceAt)}.</Alerta>;
    switch (cap?.estado) {
      case "PENDIENTE":
        return <Alerta tono="aviso">Tu certificado está en revisión por el equipo de Tinku.</Alerta>;
      case "EN_REVISION_LEGAL":
        return <Alerta tono="aviso">Tu certificado necesita una revisión legal. Mientras tanto no podés dar clases a menores; te avisamos cuando haya una decisión.</Alerta>;
      case "RECHAZADO":
        return <Alerta tono="peligro">Tu certificado fue rechazado, así que no podés dar clases a menores. Si fue por el archivo, podés volver a cargarlo.</Alerta>;
      case "VENCIDO":
        return <Alerta tono="peligro">Tu certificado venció (dura 12 meses desde que se emite). Cargá uno nuevo para volver a dar clases a menores.</Alerta>;
      default:
        return null;
    }
  })();

  return (
    <div className="flex flex-col gap-4">
      <p className="flex gap-2.5 text-sm text-tinta-suave">
        <ShieldCheck className="size-5 shrink-0 text-marca-700" aria-hidden />
        Solo hace falta si querés dar clases a chicos y chicas menores de 18. Para enseñar a adultos no tenés que cargar nada.
      </p>
      {estado}
      {!cap && !quiere ? (
        <Boton variante="secundario" className="w-fit" onClick={() => setQuiere(true)}>
          Quiero dar clases a menores
        </Boton>
      ) : (
        puedeCargar && (
          <form className="flex max-w-md flex-col gap-4" onSubmit={enviar}>
            <p className="text-sm text-tinta-suave">
              Pedí el <strong>Certificado de Antecedentes Penales</strong> en argentina.gob.ar o en Mi Argentina y subí el PDF tal
              como lo descargaste. Lo revisa una persona del equipo de Tinku y vale 12 meses desde que se emite.
            </p>
            <SubidaArchivo
              id="archivoCap"
              etiqueta="Certificado (PDF)"
              formatosTexto="PDF"
              accept="application/pdf"
              maxMb={5}
              archivo={archivo}
              onCambio={setArchivo}
              ayuda="Solo lo ve el equipo de moderación. Las familias ven únicamente si estás habilitado."
            />
            <Campo
              id="fechaEmisionCap"
              etiqueta="Fecha de emisión"
              type="date"
              value={fechaEmision}
              max={new Date().toISOString().slice(0, 10)}
              onChange={(e) => setFechaEmision(e.target.value)}
            />
            {error && <Alerta tono="peligro">{error}</Alerta>}
            <Boton type="submit" className="w-fit" cargando={enviando} textoCargando="Cargando…">
              Cargar certificado
            </Boton>
          </form>
        )
      )}
    </div>
  );
}
