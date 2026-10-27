"use client";

import { Boton, Modal } from "@/components/ui";

/**
 * El Tutor entra directo a la clase; el alumno espera en la sala hasta que el Tutor lo deja
 * entrar. "Ahora no" solo cierra el aviso: el botón de la barra lo vuelve a ofrecer.
 */
export default function DialogoAdmitir({
  abierto,
  nombre,
  onAdmitir,
  onPosponer,
}: {
  abierto: boolean;
  nombre: string;
  onAdmitir: () => void;
  onPosponer: () => void;
}) {
  return (
    <Modal
      abierto={abierto}
      onCerrar={onPosponer}
      titulo={`${nombre || "Tu alumno"} está en la sala de espera`}
      variante="centrado"
      ancho="sm"
      pie={
        <>
          <Boton onClick={onAdmitir}>Dejar entrar</Boton>
          <Boton variante="secundario" onClick={onPosponer}>
            Ahora no
          </Boton>
        </>
      }
    >
      <p className="text-[15px] text-tinta-suave">
        Cuando lo dejes entrar, empiezan a verse y escucharse.
      </p>
    </Modal>
  );
}
