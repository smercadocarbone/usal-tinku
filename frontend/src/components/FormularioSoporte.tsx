"use client";

import { useState } from "react";
import { crearTicketSoporte, mensajeDeError } from "@/lib/api";
import { Alerta, AreaTexto, Boton } from "@/components/ui";

export interface FormularioSoporteProps {
  /**
   * Tiene que ser uno de los orígenes ya registrados en
   * `admin.mapeo_origen_rol` (hoy: `M1.credencial_agotada`,
   * `M5.pago_fallido`) — la tabla tiene una FK contra ese origen, así que
   * cualquier otro valor devuelve 422 y no crea nada. No hay un origen
   * genérico todavía: por eso este componente no es un botón de "contactar a
   * soporte" universal, solo sirve donde el origen ya existe.
   */
  origenModulo: string;
  asunto: string;
  detalleInicial: string;
}

export default function FormularioSoporte({
  origenModulo,
  asunto,
  detalleInicial,
}: FormularioSoporteProps) {
  const [abierto, setAbierto] = useState(false);
  const [detalle, setDetalle] = useState(detalleInicial);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviado, setEnviado] = useState(false);

  async function enviar() {
    setEnviando(true);
    setError(null);
    try {
      await crearTicketSoporte({ origenModulo, asunto, detalle });
      setEnviado(true);
      setAbierto(false);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo enviar tu consulta a soporte."));
    } finally {
      setEnviando(false);
    }
  }

  if (enviado) {
    return <Alerta tono="exito">Recibimos tu consulta. El equipo de soporte la va a revisar y te va a contactar.</Alerta>;
  }

  return (
    <div className="mt-3">
      <Boton variante="secundario" tamano="sm" onClick={() => setAbierto((v) => !v)}>
        Contactar a soporte
      </Boton>

      {abierto && (
        <div className="mt-3 flex max-w-md flex-col gap-3 rounded-tarjeta border border-borde bg-superficie p-4">
          <AreaTexto
            id="detalleSoporte"
            etiqueta="Contanos qué pasó"
            rows={3}
            maxLength={1000}
            value={detalle}
            onChange={(e) => setDetalle(e.target.value)}
          />

          {error && <Alerta tono="peligro">{error}</Alerta>}

          <Boton
            tamano="sm"
            cargando={enviando}
            textoCargando="Enviando…"
            onClick={enviar}
            disabled={!detalle.trim()}
          >
            Enviar a soporte
          </Boton>
        </div>
      )}
    </div>
  );
}
