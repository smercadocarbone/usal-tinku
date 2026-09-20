"use client";

import { useCallback, useEffect, useId, useState } from "react";
import {
  getAlertasMias,
  getDenunciasRecibidas,
  mensajeDeError,
  presentarDescargoAlerta,
  presentarDescargoDenuncia,
  type AlertaPropia,
  type DenunciaRecibida,
  type EstadoDenunciaRecibida,
  type MotivoDenuncia,
} from "@/lib/api";
import Cabecera from "@/components/Cabecera";
import { Alerta, Boton, Cargando, Tarjeta } from "@/components/ui";

const ETIQUETA_MOTIVO: Record<MotivoDenuncia, string> = {
  comportamiento_inapropiado: "Comportamiento inapropiado",
  incumplimiento: "No cumplió lo acordado",
  fraude: "Fraude",
  contenido_ilegal: "Contenido ilegal",
  acoso: "Acoso",
};

const ETIQUETA_ESTADO_DENUNCIA: Record<EstadoDenunciaRecibida, string> = {
  registrada: "Registrada",
  en_revision: "En revisión",
  resuelta_infundada: "Resuelta: infundada",
  resuelta_fundada: "Resuelta: fundada",
  escalada: "Escalada",
};

const ETIQUETA_RAMA: Record<string, string> = {
  menor: "Contenido con un menor",
  adultos: "Contenido entre adultos",
};

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function FormularioDescargo({
  yaEnviado,
  onEnviar,
}: {
  yaEnviado: string | null;
  onEnviar: (texto: string) => Promise<void>;
}) {
  const [texto, setTexto] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const id = useId();

  if (yaEnviado) {
    return (
      <div className="mt-3 rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
        <p className="font-semibold text-slate-800">Tu descargo:</p>
        <p className="mt-1">{yaEnviado}</p>
      </div>
    );
  }

  async function enviar() {
    if (!texto.trim()) {
      setError("Escribí tu versión antes de enviar.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await onEnviar(texto.trim());
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo enviar tu descargo."));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mt-3 flex flex-col gap-2">
      <label htmlFor={id} className="text-sm font-semibold text-slate-800">
        Dar mi versión de los hechos (opcional, máx. 300 caracteres)
      </label>
      <textarea
        id={id}
        rows={3}
        maxLength={300}
        value={texto}
        onChange={(e) => setTexto(e.target.value)}
        className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
      />
      {error && <Alerta tono="error">{error}</Alerta>}
      <Boton tamano="sm" className="w-fit" cargando={enviando} textoCargando="Enviando…" onClick={enviar}>
        Enviar mi descargo
      </Boton>
    </div>
  );
}

export default function SeguridadPage() {
  const [denuncias, setDenuncias] = useState<DenunciaRecibida[] | null>(null);
  const [alertas, setAlertas] = useState<AlertaPropia[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(() => {
    setError(null);
    Promise.all([getDenunciasRecibidas(), getAlertasMias()])
      .then(([d, a]) => {
        setDenuncias(d);
        setAlertas(a);
      })
      .catch((err) => setError(mensajeDeError(err, "No se pudo cargar la información.")));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function descargarDenuncia(id: string, texto: string) {
    const actualizada = await presentarDescargoDenuncia(id, texto);
    setDenuncias((prev) => (prev ? prev.map((d) => (d.id === id ? actualizada : d)) : prev));
  }

  async function descargarAlerta(id: string, texto: string) {
    const actualizada = await presentarDescargoAlerta(id, texto);
    setAlertas((prev) => (prev ? prev.map((a) => (a.id === id ? actualizada : a)) : prev));
  }

  const cargando = denuncias === null || alertas === null;

  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />

      <main className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Denuncias y alertas de seguridad</h1>
        <p className="mt-1 text-sm text-slate-500">
          Acá ves los casos abiertos sobre tu cuenta y podés dar tu versión de los hechos.
        </p>

        {error && (
          <Alerta tono="error" className="mt-4">
            {error}
            <Boton variante="secundario" tamano="sm" className="mt-3 flex" onClick={cargar}>
              Reintentar
            </Boton>
          </Alerta>
        )}

        {cargando && !error && <Cargando>Cargando…</Cargando>}

        {!cargando && (
          <>
            <h2 className="mt-8 text-base font-semibold text-slate-800">Denuncias recibidas</h2>
            {denuncias!.length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">No tenés denuncias recibidas.</p>
            ) : (
              <ul className="mt-3 flex list-none flex-col gap-3 p-0">
                {denuncias!.map((d) => (
                  <Tarjeta as="li" key={d.id} className="w-full p-4">
                    <p className="text-sm font-semibold text-slate-800">
                      {ETIQUETA_MOTIVO[d.motivo] ?? d.motivo}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Estado: {ETIQUETA_ESTADO_DENUNCIA[d.estado] ?? d.estado} · Recibida{" "}
                      {formatFecha(d.createdAt)}
                    </p>
                    {d.descargoVenceAt && !d.descargoTexto && (
                      <p className="mt-1 text-sm text-amber-800">
                        Podés dar tu versión hasta {formatFecha(d.descargoVenceAt)}.
                      </p>
                    )}
                    <FormularioDescargo
                      yaEnviado={d.descargoTexto}
                      onEnviar={(texto) => descargarDenuncia(d.id, texto)}
                    />
                  </Tarjeta>
                ))}
              </ul>
            )}

            <h2 className="mt-8 text-base font-semibold text-slate-800">
              Alertas de seguridad automáticas
            </h2>
            {alertas!.length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">No tenés alertas de seguridad.</p>
            ) : (
              <ul className="mt-3 flex list-none flex-col gap-3 p-0">
                {alertas!.map((a) => (
                  <Tarjeta as="li" key={a.id} className="w-full p-4">
                    <p className="text-sm font-semibold text-slate-800">
                      {ETIQUETA_RAMA[a.rama] ?? a.rama}
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Estado: {a.estado} · Detectada {formatFecha(a.createdAt)}
                    </p>
                    <FormularioDescargo
                      yaEnviado={a.descargoTexto}
                      onEnviar={(texto) => descargarAlerta(a.id, texto)}
                    />
                  </Tarjeta>
                ))}
              </ul>
            )}
          </>
        )}
      </main>
    </>
  );
}
