"use client";

import { formatearHora } from "@/lib/formatos";

export interface TimeSlot {
  id: string;
  startTime: string;
  endTime: string;
  isAvailable: boolean;
}

interface DynamicTimeSlotPickerProps {
  selectedDate: Date;
  durationMinutes: number;
  slots: TimeSlot[];
  selectedSlotId: string | null;
  onSelectSlot: (id: string) => void;
  onChangeDate: (newDate: Date) => void;
  isMinorRole: boolean;
  isLoading: boolean;
  isConflictError: boolean;
}

type Periodo = "manana" | "tarde" | "noche";

const PERIODOS: { id: Periodo; etiqueta: string }[] = [
  { id: "manana", etiqueta: "Mañana" },
  { id: "tarde", etiqueta: "Tarde" },
  { id: "noche", etiqueta: "Noche" },
];

function periodoDe(horaLocal: number): Periodo {
  if (horaLocal < 12) return "manana";
  if (horaLocal < 19) return "tarde";
  return "noche";
}

function agrupar(slots: TimeSlot[]): { id: Periodo; etiqueta: string; slots: TimeSlot[] }[] {
  return PERIODOS.map((p) => ({
    ...p,
    slots: slots.filter(
      (s) => periodoDe(new Date(s.startTime).getHours()) === p.id
    ),
  })).filter((g) => g.slots.length > 0);
}

function pillClasses(slot: TimeSlot, seleccionado: boolean, enConflicto: boolean): string {
  const base = "flex min-h-[44px] items-center justify-center gap-1 rounded-full px-3 text-sm font-semibold";
  if (!slot.isAvailable) {
    return `${base} cursor-not-allowed border border-borde bg-fondo text-texto-suave opacity-50 line-through`;
  }
  if (seleccionado) {
    const conflicto = enConflicto ? "animate-shake border-peligro text-peligro" : "border-accent bg-teal-50 text-accent";
    return `${base} cursor-pointer border-2 shadow-tarjeta ${conflicto}`;
  }
  return `${base} cursor-pointer border border-borde bg-superficie text-texto transition-colors enabled:hover:border-accent enabled:hover:bg-teal-50`;
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      className="h-4 w-4"
      aria-hidden="true"
    >
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 10l3 3 6-6" />
    </svg>
  );
}

function EstadoVacio() {
  return (
    <div
      role="status"
      className="flex flex-col items-center gap-2 rounded-tarjeta border border-dashed border-borde bg-superficie px-6 py-8 text-center"
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={1.5}
        className="h-10 w-10 text-texto-suave"
        aria-hidden="true"
      >
        <rect x="3" y="4" width="18" height="18" rx="2" />
        <path d="M8 2v4M16 2v4M3 10h18" />
      </svg>
      <p className="font-semibold text-texto">No hay horarios disponibles este día</p>
      <p className="text-[0.9rem] text-texto-suave">
        Usá las flechas para buscar otro día.
      </p>
    </div>
  );
}

export default function DynamicTimeSlotPicker({
  selectedDate,
  durationMinutes,
  slots,
  selectedSlotId,
  onSelectSlot,
  onChangeDate,
  isMinorRole,
  isLoading,
  isConflictError,
}: DynamicTimeSlotPickerProps) {
  const grupos = agrupar(slots);
  const slotElegido = slots.find((s) => s.id === selectedSlotId) ?? null;
  const etiquetaFecha = selectedDate.toLocaleDateString("es-AR", {
    weekday: "long",
    day: "numeric",
    month: "long",
  });

  function cambiarDia(delta: number) {
    onChangeDate(
      new Date(
        selectedDate.getFullYear(),
        selectedDate.getMonth(),
        selectedDate.getDate() + delta,
        12
      )
    );
  }

  const botonNav =
    "flex h-11 w-11 shrink-0 cursor-pointer items-center justify-center rounded-full border border-borde bg-superficie text-xl text-texto transition-colors enabled:hover:border-accent enabled:hover:text-accent";

  return (
    <div>
      <div className="mb-4 flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => cambiarDia(-1)}
          aria-label="Día anterior"
          className={botonNav}
        >
          ‹
        </button>
        <div className="text-center">
          <p className="text-[1.05rem] font-semibold capitalize">{etiquetaFecha}</p>
          <p className="text-[0.85rem] text-texto-suave">
            Clase de {durationMinutes} minutos
          </p>
        </div>
        <button
          type="button"
          onClick={() => cambiarDia(1)}
          aria-label="Día siguiente"
          className={botonNav}
        >
          ›
        </button>
      </div>

      {isLoading ? (
        <p role="status" className="py-6 text-center text-[0.9rem] text-texto-suave">
          Cargando horarios disponibles...
        </p>
      ) : grupos.length === 0 ? (
        <EstadoVacio />
      ) : (
        <>
          <div className="flex flex-col gap-5">
            {grupos.map((g) => (
              <section key={g.id} aria-label={g.etiqueta}>
                <h3 className="mb-2 text-[0.8rem] font-semibold tracking-wide text-texto-suave uppercase">
                  {g.etiqueta}
                </h3>
                <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
                  {g.slots.map((s) => {
                    const seleccionado = s.id === selectedSlotId;
                    return (
                      <button
                        key={s.id}
                        type="button"
                        disabled={!s.isAvailable}
                        aria-pressed={seleccionado}
                        onClick={() => onSelectSlot(s.id)}
                        className={pillClasses(s, seleccionado, isConflictError && seleccionado)}
                      >
                        {seleccionado && <CheckIcon />}
                        {formatearHora(s.startTime)}
                      </button>
                    );
                  })}
                </div>
              </section>
            ))}
          </div>

          {isConflictError && (
            <div
              role="alert"
              className="mt-4 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro"
            >
              Este horario acaba de ser tomado. Por favor, elige otro.
            </div>
          )}
        </>
      )}

      {selectedSlotId && slotElegido && (
        <div className="sticky bottom-0 mt-6 border-t border-borde bg-white p-4 shadow-[0_-4px_6px_-1px_rgba(0,0,0,0.1)]">
          <p className="mb-2 text-[0.85rem] text-texto-suave">
            {etiquetaFecha} &middot; {formatearHora(slotElegido.startTime)} –{" "}
            {formatearHora(slotElegido.endTime)}
          </p>
          <button
            type="submit"
            disabled={isConflictError}
            className="w-full cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isMinorRole ? "Enviar Solicitud de Aprobación" : "Confirmar y Pagar"}
          </button>
        </div>
      )}
    </div>
  );
}