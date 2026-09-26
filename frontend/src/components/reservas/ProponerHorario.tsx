"use client";

import { useEffect, useMemo, useState } from "react";
import { api, mensajeDeError } from "@/lib/api";
import { diaCorto, horaCorta } from "@/lib/formatos";
import { proximosDias, type Franja } from "@/lib/agenda";
import { pedirReprogramacion, type PedidoReprogramacion, type Reserva } from "@/lib/reservas";
import { TIEMPOS } from "@/lib/tiempos";
import { cn } from "@/lib/cn";
import { Alerta, AreaTexto, Boton, Chip, Modal } from "@/components/ui";

interface TimeSlot {
  startTime: string;
  isAvailable: boolean;
}

export interface ProponerHorarioProps {
  abierto: boolean;
  onCerrar: () => void;
  reserva: Reserva;
  onPedido: (p: PedidoReprogramacion) => void;
}

/**
 * El Tutor propone otro horario para una clase confirmada (FR-RES-029). Muestra solo bloques
 * libres de sus franjas con la duración de la clase; el alumno (o su adulto responsable) decide.
 */
export default function ProponerHorario({ abierto, onCerrar, reserva, onPedido }: ProponerHorarioProps) {
  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [dia, setDia] = useState<string | null>(null);
  const [slots, setSlots] = useState<Record<string, TimeSlot[] | null>>({});
  const [elegido, setElegido] = useState<string | null>(null);
  const [motivo, setMotivo] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const duracion = reserva.duracionMinutos ?? 60;

  useEffect(() => {
    if (!abierto || franjas) return;
    api
      .get<Franja[]>(`/api/tutores/${reserva.tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => setFranjas([]));
  }, [abierto, franjas, reserva.tutorId]);

  const dias = useMemo(() => (franjas ? proximosDias(franjas, TIEMPOS.horizonteProximoHorarioDias) : []), [franjas]);

  useEffect(() => {
    if (!dia || dia in slots) return;
    setSlots((s) => ({ ...s, [dia]: null }));
    api
      .get<TimeSlot[]>(`/api/tutores/${reserva.tutorId}/horarios?fecha=${dia}&duracionMinutos=${duracion}`)
      .then((lista) => setSlots((s) => ({ ...s, [dia]: lista })))
      .catch(() => setSlots((s) => ({ ...s, [dia]: [] })));
  }, [dia, slots, reserva.tutorId, duracion]);

  const libres = (dia ? (slots[dia] ?? []) : []).filter(
    (t) => t.isAvailable && new Date(t.startTime).getTime() !== new Date(reserva.horario).getTime()
  );

  async function enviar() {
    if (!elegido) return;
    setEnviando(true);
    setError(null);
    try {
      onPedido(await pedirReprogramacion(reserva.id, elegido, motivo));
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos mandar el pedido."));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      variante="hoja"
      titulo="Proponer otro horario"
      descripcion="Le mandamos tu propuesta a quien pagó la clase: puede aceptarla o cancelar con la devolución completa."
      pie={
        <>
          <Boton variante="secundario" onClick={onCerrar}>
            Volver
          </Boton>
          <Boton disabled={!elegido} cargando={enviando} textoCargando="Enviando…" onClick={() => void enviar()}>
            Mandar la propuesta
          </Boton>
        </>
      }
    >
      <div className="flex flex-col gap-4 pb-2">
        <p className="text-sm text-tinta-suave">
          Mientras no respondan, la clase sigue en su horario. Si no responden hasta una hora antes, se cancela y se
          devuelve el total.
        </p>
        {franjas === null ? (
          <p role="status" className="text-sm text-tinta-tenue">
            Cargando tus horarios…
          </p>
        ) : dias.length === 0 ? (
          <p className="text-[15px] text-tinta-suave">No tenés horarios publicados en las próximas dos semanas.</p>
        ) : (
          <fieldset>
            <legend className="mb-2 text-sm font-bold">Día</legend>
            <div className="flex flex-wrap gap-2">
              {dias.map((d) => (
                <Chip
                  key={d.fecha}
                  activo={dia === d.fecha}
                  onClick={() => {
                    setDia(d.fecha);
                    setElegido(null);
                  }}
                >
                  <span className="capitalize">{diaCorto(d.referencia)}</span>
                </Chip>
              ))}
            </div>
          </fieldset>
        )}
        {dia && (
          <fieldset>
            <legend className="mb-2 text-sm font-bold">Horario</legend>
            {slots[dia] === null ? (
              <p role="status" className="text-sm text-tinta-tenue">
                Buscando horarios libres…
              </p>
            ) : libres.length === 0 ? (
              <p className="text-[15px] text-tinta-suave">Ese día no te queda un horario libre de esta duración.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {libres.map((t) => (
                  <button
                    key={t.startTime}
                    type="button"
                    aria-pressed={elegido === t.startTime}
                    onClick={() => setElegido(t.startTime)}
                    className={cn(
                      "min-h-11 cursor-pointer rounded-control border-2 px-4 font-semibold tabular",
                      elegido === t.startTime ? "border-tinta" : "border-borde hover:border-borde-fuerte"
                    )}
                  >
                    {horaCorta(t.startTime)}
                  </button>
                ))}
              </div>
            )}
          </fieldset>
        )}
        <AreaTexto
          id="motivo-reprogramacion"
          etiqueta="Motivo (opcional)"
          value={motivo}
          maxLength={300}
          contador
          onChange={(e) => setMotivo(e.target.value)}
          placeholder="Por ejemplo: me surgió un turno médico"
        />
        {error && <Alerta tono="peligro">{error}</Alerta>}
      </div>
    </Modal>
  );
}
