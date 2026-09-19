"use client";

import { useState } from "react";
import { Flag } from "lucide-react";
import {
  mensajeDeError,
  presentarDenuncia,
  type MotivoDenuncia,
} from "@/lib/api";
import { Alerta, Boton, CampoSelect } from "@/components/ui";

const ETIQUETA_MOTIVO: Record<MotivoDenuncia, string> = {
  comportamiento_inapropiado: "Comportamiento inapropiado",
  incumplimiento: "No cumplió lo acordado",
  fraude: "Fraude",
  contenido_ilegal: "Contenido ilegal",
  acoso: "Acoso",
};

export interface FormularioDenunciaProps {
  /** A quién se denuncia. */
  denunciadoId: string;
  /** Si la denuncia nace de una clase puntual, no de un perfil en general. */
  sesionId?: string;
}

/**
 * Botón "Denunciar" + formulario inline. Un Menor no puede presentar una
 * Denuncia (Artículo II, FR-SEC-001) — el backend ya lo rechaza con 403 sin
 * importar lo que mande el cliente, así que acá alcanza con no mostrar el
 * botón si la sesión es de un Menor (lo decide quien use el componente).
 */
export default function FormularioDenuncia({ denunciadoId, sesionId }: FormularioDenunciaProps) {
  const [abierto, setAbierto] = useState(false);
  const [motivo, setMotivo] = useState<MotivoDenuncia>("comportamiento_inapropiado");
  const [evidenciaUrl, setEvidenciaUrl] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviada, setEnviada] = useState(false);

  async function enviar() {
    setEnviando(true);
    setError(null);
    try {
      await presentarDenuncia({
        denunciadoId,
        sesionId,
        motivo,
        evidenciaUrl: evidenciaUrl.trim() || undefined,
      });
      setEnviada(true);
      setAbierto(false);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo registrar la denuncia."));
    } finally {
      setEnviando(false);
    }
  }

  if (enviada) {
    return (
      <Alerta tono="exito">
        Denuncia registrada. El equipo de Moderación y Seguridad la va a revisar.
      </Alerta>
    );
  }

  return (
    <div>
      <Boton
        variante="secundario"
        tamano="sm"
        onClick={() => setAbierto((v) => !v)}
        className="text-red-700 hover:border-red-300 hover:bg-red-50"
      >
        <Flag size={14} aria-hidden />
        Denunciar
      </Boton>

      {abierto && (
        <div className="mt-3 flex max-w-sm flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <CampoSelect
            id="motivoDenuncia"
            etiqueta="Motivo"
            value={motivo}
            onChange={(e) => setMotivo(e.target.value as MotivoDenuncia)}
          >
            {(Object.keys(ETIQUETA_MOTIVO) as MotivoDenuncia[]).map((m) => (
              <option key={m} value={m}>
                {ETIQUETA_MOTIVO[m]}
              </option>
            ))}
          </CampoSelect>

          <div className="flex flex-col gap-1.5">
            <label htmlFor="evidenciaDenuncia" className="text-sm font-semibold text-slate-800">
              Link de evidencia (opcional)
            </label>
            <input
              id="evidenciaDenuncia"
              type="url"
              placeholder="https://…"
              maxLength={500}
              value={evidenciaUrl}
              onChange={(e) => setEvidenciaUrl(e.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
            />
          </div>

          {error && <Alerta tono="error">{error}</Alerta>}

          <Boton tamano="sm" cargando={enviando} textoCargando="Enviando…" onClick={enviar}>
            Enviar denuncia
          </Boton>
        </div>
      )}
    </div>
  );
}
