"use client";

import { useState } from "react";
import { mensajeDeError } from "@/lib/api";
import { fechaHoraLarga } from "@/lib/formatos";
import { responderReprogramacion, retirarReprogramacion, type PedidoReprogramacion } from "@/lib/reservas";
import { Alerta, Boton, ModalConfirmacion, useToast } from "@/components/ui";

export interface PedidoReprogramacionTarjetaProps {
  pedido: PedidoReprogramacion;
  /** Después de responder o retirar, la pantalla recarga la reserva. */
  onResuelto: (mensaje: string) => void;
}

/**
 * Pedido de reprogramación del Tutor (FR-RES-029..031). Quien pagó acepta el horario nuevo o
 * cancela con la devolución completa; si no responde a tiempo, la clase se cancela y se devuelve.
 * El Tutor lo ve esperando respuesta y lo puede retirar.
 */
export default function PedidoReprogramacionTarjeta({ pedido, onResuelto }: PedidoReprogramacionTarjetaProps) {
  const toast = useToast();
  const [enviando, setEnviando] = useState<"aceptar" | "rechazar" | "retirar" | null>(null);
  const [confirmarRechazo, setConfirmarRechazo] = useState(false);

  async function accion(tipo: "aceptar" | "rechazar" | "retirar") {
    setEnviando(tipo);
    try {
      if (tipo === "retirar") await retirarReprogramacion(pedido.reservaId);
      else await responderReprogramacion(pedido.reservaId, tipo === "aceptar");
      onResuelto(
        tipo === "aceptar"
          ? "Listo: la clase pasó al horario nuevo"
          : tipo === "rechazar"
            ? "Cancelamos la clase. Te devolvemos el total."
            : "Retiraste el pedido: la clase sigue en su horario"
      );
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos completar la acción."), { tono: "error" });
    } finally {
      setEnviando(null);
      setConfirmarRechazo(false);
    }
  }

  return (
    <Alerta
      tono="aviso"
      className="mt-6"
      titulo={pedido.puedoResponder ? "El tutor te propone otro horario" : "Propusiste otro horario"}
    >
      <div className="flex flex-col gap-3">
        <p>
          <span className="first-letter:uppercase">Pasar la clase al </span>
          <strong className="text-tinta">{fechaHoraLarga(pedido.horarioPropuesto)}</strong>.
        </p>
        {pedido.motivo && <p className="text-tinta-suave">&ldquo;{pedido.motivo}&rdquo;</p>}
        <p className="text-sm">
          {pedido.puedoResponder
            ? `Respondé antes del ${fechaHoraLarga(pedido.venceAt)}. Si no, la clase se cancela y te devolvemos el total.`
            : `Esperando respuesta hasta el ${fechaHoraLarga(pedido.venceAt)}. Si no responden, la clase se cancela y se le devuelve el total a quien pagó.`}
        </p>
        {pedido.puedoResponder && (
          <div className="flex flex-col gap-2 sm:flex-row">
            <Boton cargando={enviando === "aceptar"} textoCargando="Aceptando…" onClick={() => void accion("aceptar")}>
              Aceptar el horario nuevo
            </Boton>
            <Boton variante="secundario" className="text-peligro" onClick={() => setConfirmarRechazo(true)}>
              Cancelar y recibir la devolución
            </Boton>
          </div>
        )}
        {pedido.puedoRetirar && (
          <Boton variante="secundario" className="w-fit" cargando={enviando === "retirar"} onClick={() => void accion("retirar")}>
            Retirar el pedido
          </Boton>
        )}
      </div>
      <ModalConfirmacion
        abierto={confirmarRechazo}
        onCerrar={() => setConfirmarRechazo(false)}
        onConfirmar={() => accion("rechazar")}
        cargando={enviando === "rechazar"}
        titulo="¿Cancelar la clase?"
        textoConfirmar="Cancelar y recibir la devolución"
        tono="peligro"
      >
        <p>La clase se cancela y te devolvemos el total por el mismo medio de pago.</p>
      </ModalConfirmacion>
    </Alerta>
  );
}
