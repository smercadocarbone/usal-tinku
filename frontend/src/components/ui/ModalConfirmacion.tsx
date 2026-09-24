"use client";

import type { ReactNode } from "react";
import Boton from "./Boton";
import Modal from "./Modal";

export interface ModalConfirmacionProps {
  abierto: boolean;
  onCerrar: () => void;
  onConfirmar: () => void | Promise<void>;
  titulo: ReactNode;
  /** Qué va a pasar, escrito completo: la consecuencia se lee ANTES de confirmar. */
  children: ReactNode;
  /** Texto del botón = la acción ("Dar de baja a Sofía"). Nunca "Aceptar". */
  textoConfirmar: string;
  textoCancelar?: string;
  /** `peligro` para lo destructivo o irreversible. */
  tono?: "peligro" | "primario";
  cargando?: boolean;
}

/** Para acciones destructivas o irreversibles (UX-01 §3). */
export default function ModalConfirmacion({
  abierto,
  onCerrar,
  onConfirmar,
  titulo,
  children,
  textoConfirmar,
  textoCancelar = "Volver",
  tono = "peligro",
  cargando,
}: ModalConfirmacionProps) {
  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      titulo={titulo}
      variante="hoja"
      ancho="sm"
      pie={
        <>
          <Boton variante="secundario" onClick={onCerrar} disabled={cargando}>
            {textoCancelar}
          </Boton>
          <Boton variante={tono} onClick={() => void onConfirmar()} cargando={cargando}>
            {textoConfirmar}
          </Boton>
        </>
      }
    >
      <div className="text-[15px] leading-relaxed text-tinta-suave">{children}</div>
    </Modal>
  );
}
ModalConfirmacion.displayName = "ModalConfirmacion";
