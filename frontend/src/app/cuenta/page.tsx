"use client";

import { useEffect, useState, type FormEvent } from "react";
import { useSesion } from "@/lib/useSesion";
import {
  actualizarCapacidades,
  getPerfilPropio,
  mensajeDeError,
  type PerfilPropio,
} from "@/lib/api";
import BannerCredencial from "@/components/BannerCredencial";
import { Alerta, Boton, CampoCheckbox, Cargando, Tarjeta } from "@/components/ui";

const NOMBRE_TIPO: Record<string, string> = {
  ADULTO: "Adulto",
  MENOR: "Menor",
  TUTOR: "Tutor",
};

export default function CuentaPerfilPage() {
  const session = useSesion();
  const payload = session?.payload;

  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  const [cargandoPerfil, setCargandoPerfil] = useState(true);

  const [capEstudiante, setCapEstudiante] = useState(false);
  const [capAr, setCapAr] = useState(false);
  const [guardandoCapacidades, setGuardandoCapacidades] = useState(false);
  const [errorCapacidades, setErrorCapacidades] = useState<string | null>(null);
  const [exitoCapacidades, setExitoCapacidades] = useState(false);

  useEffect(() => {
    getPerfilPropio()
      .then((p) => {
        setPerfil(p);
        setCapEstudiante(p.capacidadEstudiante);
        setCapAr(p.capacidadAdultoResponsable);
      })
      .catch(() => setPerfil(null))
      .finally(() => setCargandoPerfil(false));
  }, []);

  async function onSubmitCapacidades(e: FormEvent) {
    e.preventDefault();
    setErrorCapacidades(null);
    setExitoCapacidades(false);
    setGuardandoCapacidades(true);
    try {
      const p = await actualizarCapacidades(capEstudiante, capAr);
      setPerfil(p);
      setExitoCapacidades(true);
    } catch (err) {
      setErrorCapacidades(mensajeDeError(err, "No se pudieron actualizar las capacidades."));
    } finally {
      setGuardandoCapacidades(false);
    }
  }

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Perfil</h2>

      {payload?.tipo === "TUTOR" && (
        <div className="mt-4">
          <BannerCredencial />
        </div>
      )}

      <dl className="mt-6 rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">DNI</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">{payload?.sub ?? "—"}</dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Tipo de cuenta</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.tipo ? NOMBRE_TIPO[payload.tipo] ?? payload.tipo : "—"}
          </dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Estudiante</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.cap_est ? "Activa" : "Inactiva"}
          </dd>
        </div>
        <div className="flex justify-between gap-4 border-b border-slate-100 py-3 first:pt-0 last:border-b-0 last:pb-0">
          <dt className="text-sm font-semibold text-slate-800">Adulto Responsable</dt>
          <dd className="m-0 text-right text-sm text-slate-600 capitalize">
            {payload?.cap_ar ? "Activa" : "Inactiva"}
          </dd>
        </div>
      </dl>

      {perfil?.tipo === "ADULTO" && (
        <Tarjeta className="mt-6 w-full max-w-sm p-6">
          <h3 className="mb-3 text-base font-semibold text-slate-800">Capacidades</h3>
          {cargandoPerfil ? (
            <Cargando>Cargando…</Cargando>
          ) : (
            <form className="flex flex-col gap-3" onSubmit={onSubmitCapacidades}>
              <CampoCheckbox
                id="capEstudiante"
                etiqueta="Estudiante"
                checked={capEstudiante}
                onChange={(e) => setCapEstudiante(e.target.checked)}
              />
              <CampoCheckbox
                id="capAr"
                etiqueta="Adulto Responsable"
                checked={capAr}
                onChange={(e) => setCapAr(e.target.checked)}
              />
              {errorCapacidades && <Alerta tono="error">{errorCapacidades}</Alerta>}
              {exitoCapacidades && <Alerta tono="exito">Capacidades actualizadas.</Alerta>}
              <Boton
                type="submit"
                tamano="sm"
                className="w-fit"
                cargando={guardandoCapacidades}
                textoCargando="Guardando…"
                disabled={
                  capEstudiante === perfil?.capacidadEstudiante &&
                  capAr === perfil?.capacidadAdultoResponsable
                }
              >
                Guardar capacidades
              </Boton>
            </form>
          )}
        </Tarjeta>
      )}
    </section>
  );
}
