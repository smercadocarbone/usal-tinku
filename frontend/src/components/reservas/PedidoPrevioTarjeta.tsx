"use client";

import { useEffect, useState } from "react";
import { MessageSquareText } from "lucide-react";
import { mensajeDeError } from "@/lib/api";
import { getPedido, guardarPedido, type PedidoPrevio } from "@/lib/reservas";
import { AreaTexto, Boton, Tarjeta, useToast } from "@/components/ui";

const MAX_TEXTO = 1000;

export interface PedidoPrevioTarjetaProps {
  reservaId: string;
  /** Quien pagó: lo escribe (con un Menor, siempre el Adulto Responsable). */
  soyPagador: boolean;
  /** El Tutor lo lee. */
  soyTutor: boolean;
  /** La reserva todavía admite cambios (pendiente de pago o confirmada, antes de empezar). */
  reservaEditable: boolean;
}

/**
 * Pedido previo a la clase (FR-RES-027): "qué querés ver", en texto. Lo escribe quien pagó y lo lee
 * el Tutor. No es un chat: va en un solo sentido.
 */
export default function PedidoPrevioTarjeta({ reservaId, soyPagador, soyTutor, reservaEditable }: PedidoPrevioTarjetaProps) {
  const toast = useToast();
  const [pedido, setPedido] = useState<PedidoPrevio | null | undefined>(undefined);
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState("");
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getPedido(reservaId)
      .then((p) => setPedido(p ?? null))
      .catch(() => setPedido(null));
  }, [reservaId]);

  if (pedido === undefined || (!soyPagador && !soyTutor)) return null;
  // El Tutor solo ve la tarjeta si hay algo que leer.
  if (soyTutor && !pedido) return null;

  const puedeEditar = soyPagador && (pedido ? pedido.editable : reservaEditable);

  function empezar() {
    setTexto(pedido?.texto ?? "");
    setError(null);
    setEditando(true);
  }

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      setPedido(await guardarPedido(reservaId, texto));
      setEditando(false);
      toast.mostrar("Le mandamos tu pedido al tutor");
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos guardar el pedido."));
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Tarjeta className="mt-10" aria-labelledby="titulo-pedido">
      <h2 id="titulo-pedido" className="flex items-center gap-2 text-lg font-bold">
        <MessageSquareText className="size-5 text-marca-700" aria-hidden />
        {soyTutor ? "Qué quiere ver en la clase" : "Qué querés ver en la clase"}
      </h2>

      {editando ? (
        <div className="mt-4 flex flex-col gap-4">
          <AreaTexto
            id="pedido-texto"
            etiqueta="Contale al tutor qué necesitás"
            ayuda="Por ejemplo: el tema, qué te cuesta o para cuándo es el examen. No pongas teléfonos ni mails: los tapamos."
            value={texto}
            maxLength={MAX_TEXTO}
            contador
            onChange={(e) => setTexto(e.target.value)}
          />
          {error && <p className="text-sm font-semibold text-peligro" role="alert">{error}</p>}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Boton onClick={() => void guardar()} cargando={guardando} textoCargando="Guardando…" disabled={!texto.trim()}>
              Mandar al tutor
            </Boton>
            <Boton variante="secundario" onClick={() => setEditando(false)}>
              Cancelar
            </Boton>
          </div>
        </div>
      ) : pedido ? (
        <div className="mt-3 flex flex-col gap-3">
          <p className="whitespace-pre-line text-[15px] leading-relaxed text-tinta-suave">{pedido.texto}</p>
          {puedeEditar && (
            <Boton variante="secundario" className="w-fit" onClick={empezar}>
              Cambiar el pedido
            </Boton>
          )}
        </div>
      ) : (
        <div className="mt-3 flex flex-col gap-3">
          <p className="text-[15px] text-tinta-suave">
            Contale al tutor qué querés ver. Así la clase arranca directo en lo que necesitás.
          </p>
          {puedeEditar && (
            <Boton variante="secundario" className="w-fit" onClick={empezar}>
              Escribir el pedido
            </Boton>
          )}
        </div>
      )}
    </Tarjeta>
  );
}
