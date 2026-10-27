"use client";

import { Boton, CampoSelect, Modal } from "@/components/ui";

export interface Dispositivo {
  deviceId: string;
  label: string;
}

interface Grupo {
  id: string;
  etiqueta: string;
  lista: Dispositivo[];
  actual: string | undefined;
  onCambiar: (deviceId: string) => void;
}

/** Configuración de audio y video desde el menú "⋮" del aula: qué cámara, micrófono y parlante usar. */
export default function DialogoConfiguracion({
  abierto,
  onCerrar,
  camaras,
  microfonos,
  parlantes,
  camaraId,
  microfonoId,
  parlanteId,
  onCambiarCamara,
  onCambiarMicrofono,
  onCambiarParlante,
}: {
  abierto: boolean;
  onCerrar: () => void;
  camaras: Dispositivo[];
  microfonos: Dispositivo[];
  /** Vacío si el navegador no deja elegir la salida de audio (Safari, Firefox). */
  parlantes: Dispositivo[];
  camaraId: string | undefined;
  microfonoId: string | undefined;
  parlanteId: string | undefined;
  onCambiarCamara: (deviceId: string) => void;
  onCambiarMicrofono: (deviceId: string) => void;
  onCambiarParlante: (deviceId: string) => void;
}) {
  const grupos: Grupo[] = [
    { id: "cfg-camara", etiqueta: "Cámara", lista: camaras, actual: camaraId, onCambiar: onCambiarCamara },
    { id: "cfg-microfono", etiqueta: "Micrófono", lista: microfonos, actual: microfonoId, onCambiar: onCambiarMicrofono },
    { id: "cfg-parlante", etiqueta: "Parlante", lista: parlantes, actual: parlanteId, onCambiar: onCambiarParlante },
  ];

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo="Configuración de audio y video"
      variante="hoja"
      ancho="sm"
      pie={<Boton onClick={onCerrar}>Listo</Boton>}
    >
      <div className="flex flex-col gap-4">
        {grupos
          .filter((g) => g.lista.length > 0)
          .map((g) => (
            <CampoSelect
              key={g.id}
              id={g.id}
              etiqueta={g.etiqueta}
              value={g.actual ?? g.lista[0]?.deviceId}
              onChange={(e) => g.onCambiar(e.target.value)}
            >
              {g.lista.map((d) => (
                <option key={d.deviceId} value={d.deviceId}>
                  {d.label}
                </option>
              ))}
            </CampoSelect>
          ))}
        {camaras.length === 0 && microfonos.length === 0 && (
          <p className="text-[15px] text-tinta-suave">
            No encontramos cámaras ni micrófonos. Revisá los permisos del navegador.
          </p>
        )}
      </div>
    </Modal>
  );
}
