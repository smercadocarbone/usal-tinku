"use client";

import { useEffect, useState } from "react";
import { actualizarAceptaMenores, getEstadoPerfilTutor, mensajeDeError } from "@/lib/api";
import SeccionCap from "@/components/tutor/SeccionCap";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Cargando, Interruptor, ModalConfirmacion, Tarjeta, useToast } from "@/components/ui";

/**
 * Mi cuenta → Clases con menores: el Tutor elige si da clases a menores y, si sí, carga su
 * Certificado de Antecedentes Penales (FR-ID-021..026). Sin CAP vigente nunca da clases a menores.
 */
export default function ClasesConMenoresPage() {
  const toast = useToast();
  const [acepta, setAcepta] = useState<boolean | undefined>(undefined);
  const [confirmarApagar, setConfirmarApagar] = useState(false);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    getEstadoPerfilTutor()
      .then((e) => setAcepta(e.aceptaMenores ?? true))
      .catch(() => setAcepta(true));
  }, []);

  async function guardar(valor: boolean) {
    setGuardando(true);
    try {
      const r = await actualizarAceptaMenores(valor);
      setAcepta(r.aceptaMenores);
      toast.mostrar(
        valor
          ? "Listo: podés dar clases a menores con el certificado vigente."
          : r.clasesCanceladas > 0
            ? `Listo. Cancelamos ${r.clasesCanceladas} ${r.clasesCanceladas === 1 ? "clase" : "clases"} con menores y avisamos a sus familias.`
            : "Listo: no vas a aparecerle a menores."
      );
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar el cambio."), { tono: "error" });
    } finally {
      setGuardando(false);
      setConfirmarApagar(false);
    }
  }

  return (
    <SubpaginaTutor
      titulo="Clases con menores"
      descripcion="Elegí si querés dar clases a chicos y chicas menores de 18. Para enseñar a adultos no tenés que hacer nada acá."
    >
      {acepta === undefined ? (
        <Cargando>Cargando…</Cargando>
      ) : (
        <div className="flex flex-col gap-6">
          <Tarjeta>
            <Interruptor
              id="aceptaMenores"
              etiqueta="Doy clases a menores"
              descripcion="Con esto prendido y tu certificado de antecedentes aprobado y vigente, las familias pueden autorizarte para sus hijos."
              activo={acepta}
              disabled={guardando}
              onCambio={(v) => (v ? void guardar(true) : setConfirmarApagar(true))}
            />
          </Tarjeta>
          {acepta && (
            <Tarjeta>
              <h3 className="mb-4 text-lg font-bold">Certificado de Antecedentes Penales</h3>
              <SeccionCap />
            </Tarjeta>
          )}
        </div>
      )}

      <ModalConfirmacion
        abierto={confirmarApagar}
        titulo="¿Dejar de dar clases a menores?"
        textoConfirmar="Sí, dejar de darlas"
        tono="peligro"
        cargando={guardando}
        onConfirmar={() => guardar(false)}
        onCerrar={() => setConfirmarApagar(false)}
      >
        No vas a aparecerle a ningún menor. Si tenés clases agendadas con menores, se cancelan, las familias reciben el
        reembolso total y les avisamos.
      </ModalConfirmacion>
    </SubpaginaTutor>
  );
}
