"use client";

import { useState } from "react";
import { mensajeDeError, presentarDenuncia, type MotivoDenuncia } from "@/lib/api";
import { ETIQUETA_MOTIVO_DENUNCIA } from "@/lib/etiquetas";
import { TIEMPOS } from "@/lib/tiempos";
import { Alerta, Boton, Campo, Modal, Selector } from "@/components/ui";

export interface FormularioDenunciaProps {
  abierto: boolean;
  onCerrar: () => void;
  /** Se llama con la denuncia ya registrada. */
  onEnviada?: () => void;
  /** A quién se denuncia. */
  denunciadoId: string;
  /** Nombre visible del denunciado ("Jorge M."), para el título. */
  nombre?: string;
  /** Si la denuncia nace de una clase puntual, no de un perfil en general. */
  sesionId?: string;
}

/**
 * Reportar un perfil o una clase (M9 US-1), en una hoja aparte: nunca al lado
 * del CTA principal (UX-04 §2). Un Menor no puede presentar una Denuncia
 * (Artículo II, FR-SEC-001): quien use el componente no lo ofrece a un Menor, y
 * el backend igual lo rechaza con 403.
 */
export default function FormularioDenuncia({ abierto, onCerrar, onEnviada, denunciadoId, nombre, sesionId }: FormularioDenunciaProps) {
  const [motivo, setMotivo] = useState<MotivoDenuncia>("comportamiento_inapropiado");
  const [evidenciaUrl, setEvidenciaUrl] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function enviar() {
    setEnviando(true);
    setError(null);
    try {
      await presentarDenuncia({ denunciadoId, sesionId, motivo, evidenciaUrl: evidenciaUrl.trim() || undefined });
      onEnviada?.();
      onCerrar();
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos registrar el reporte. Probá de nuevo."));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      variante="hoja"
      titulo={nombre ? `Reportar a ${nombre}` : "Reportar"}
      descripcion={`Lo revisa una persona del equipo de Moderación y Seguridad. Quien es reportado no sabe quién lo reportó, y tiene ${TIEMPOS.descargoHoras} hs para dar su versión.`}
      pie={
        <>
          <Boton variante="secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </Boton>
          <Boton variante="peligro" cargando={enviando} textoCargando="Enviando…" onClick={enviar}>
            Enviar denuncia
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-5 pb-2">
        <Selector id="motivoDenuncia" etiqueta="Motivo" value={motivo} onChange={(e) => setMotivo(e.target.value as MotivoDenuncia)}>
          {Object.entries(ETIQUETA_MOTIVO_DENUNCIA).map(([valor, texto]) => (
            <option key={valor} value={valor}>
              {texto}
            </option>
          ))}
        </Selector>
        <Campo
          id="evidenciaDenuncia"
          etiqueta="Link de evidencia (opcional)"
          type="url"
          placeholder="https://…"
          maxLength={500}
          value={evidenciaUrl}
          onChange={(e) => setEvidenciaUrl(e.target.value)}
        />
        {error && <Alerta tono="peligro">{error}</Alerta>}
      </div>
    </Modal>
  );
}
