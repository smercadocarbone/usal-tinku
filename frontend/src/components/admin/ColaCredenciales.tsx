"use client";

import { useEffect, useRef, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  getArchivoCredencial,
  getColaCredenciales,
  mensajeDeError,
  resolverCredencial,
  type CredencialCola,
  type DecisionCredencial,
} from "@/lib/api";
import { Alerta, AreaTexto, Boton, CampoCheckbox, CampoSelect, Cargando, ModalConfirmacion, Tarjeta } from "@/components/ui";
import {
  MOTIVOS_RECHAZO,
  TIPOS_CREDENCIAL,
  nivelHasta,
  nombreEspecialidad,
  type DetalleDecisionCredencial,
  type MetodoVerificacion,
  type MotivoRechazoCredencial,
} from "@/lib/especialidades";

const ETIQUETA_TIPO: Record<string, string> = {
  TITULO: "Título",
  CERTIFICADO_ANALITICO: "Certificado analítico",
  MATRICULA: "Matrícula",
  ...Object.fromEntries(TIPOS_CREDENCIAL.map((t) => [t.value, t.label])),
};

/** ADR-M1-06: la fila trae los datos para verificar contra el registro (flag encendido). */
function conVerificacion(c: CredencialCola): boolean {
  return c.tutorDni !== undefined;
}

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
    timeZone: "America/Argentina/Buenos_Aires",
  });
}

/** Documento abierto en el visor: URL de blob local (se revoca al cerrar) + su tipo. */
type Documento = { id: string; url: string; tipo: string };

export default function ColaCredenciales() {
  const [credenciales, setCredenciales] = useState<CredencialCola[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [procesandoId, setProcesandoId] = useState<string | null>(null);
  const [documento, setDocumento] = useState<Documento | null>(null);
  const [abriendoId, setAbriendoId] = useState<string | null>(null);
  const urlAbierta = useRef<string | null>(null);

  function cerrarDocumento() {
    if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
    urlAbierta.current = null;
    setDocumento(null);
  }

  // Al desmontar, liberar el blob del documento que haya quedado abierto.
  useEffect(
    () => () => {
      if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
    },
    []
  );

  // AUD-007: hasta acá la credencial se aprobaba sin verla. El archivo se pide
  // con el token (fetch → blob) porque un <a href> no manda el header de auth.
  async function verDocumento(id: string) {
    if (documento?.id === id) {
      cerrarDocumento();
      return;
    }
    setAbriendoId(id);
    setError(null);
    try {
      const blob = await getArchivoCredencial(id);
      cerrarDocumento();
      const url = URL.createObjectURL(blob);
      urlAbierta.current = url;
      setDocumento({ id, url, tipo: blob.type });
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo abrir el documento de la credencial."));
    } finally {
      setAbriendoId(null);
    }
  }

  function cargar() {
    setCargando(true);
    setError(null);
    setProhibido(false);
    getColaCredenciales()
      .then(setCredenciales)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudo cargar la cola de credenciales."));
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, []);

  const [aRechazar, setARechazar] = useState<CredencialCola | null>(null);
  // ADR-M1-06: aprobación con método y cobertura; rechazo con motivo.
  const [aAprobar, setAAprobar] = useState<CredencialCola | null>(null);
  const [metodo, setMetodo] = useState<MetodoVerificacion | null>(null);
  const [cubiertas, setCubiertas] = useState<string[]>([]);
  const [motivo, setMotivo] = useState<MotivoRechazoCredencial | "">("");
  const [detalle, setDetalle] = useState("");
  const [errorModal, setErrorModal] = useState<string | null>(null);

  function abrirAprobar(c: CredencialCola) {
    setMetodo(null);
    setCubiertas([]);
    setErrorModal(null);
    setAAprobar(c);
  }

  function abrirRechazar(c: CredencialCola) {
    setMotivo("");
    setDetalle("");
    setErrorModal(null);
    setARechazar(c);
  }

  async function resolver(id: string, decision: DecisionCredencial, extra?: DetalleDecisionCredencial): Promise<boolean> {
    setProcesandoId(id);
    try {
      await resolverCredencial(id, decision, extra);
      if (documento?.id === id) cerrarDocumento();
      setCredenciales((prev) => (prev ? prev.filter((c) => c.id !== id) : prev));
      return true;
    } catch (err) {
      const mensaje = mensajeDeError(err, "No se pudo resolver la credencial.");
      if (extra) setErrorModal(mensaje);
      else setError(mensaje);
      return false;
    } finally {
      setProcesandoId(null);
    }
  }

  if (cargando) {
    return <Cargando>Cargando credenciales…</Cargando>;
  }

  const modalRechazo = (
    <ModalConfirmacion
      abierto={aRechazar !== null}
      onCerrar={() => setARechazar(null)}
      cargando={aRechazar !== null && procesandoId === aRechazar.id}
      onConfirmar={async () => {
        if (!aRechazar) return;
        if (conVerificacion(aRechazar)) {
          if (!motivo) {
            setErrorModal("Elegí el motivo del rechazo: el Tutor lo va a ver.");
            return;
          }
          if (motivo === "OTRO" && !detalle.trim()) {
            setErrorModal("Contá el motivo del rechazo: el Tutor lo va a ver.");
            return;
          }
          const ok = await resolver(aRechazar.id, "RECHAZAR", {
            motivoRechazo: motivo,
            ...(detalle.trim() ? { detalleRechazo: detalle.trim() } : {}),
          });
          if (ok) setARechazar(null);
          return;
        }
        await resolver(aRechazar.id, "RECHAZAR");
        setARechazar(null);
      }}
      titulo={aRechazar ? `¿Rechazar la credencial de ${aRechazar.tutorNombre} ${aRechazar.tutorApellido}?` : ""}
      textoConfirmar="Rechazar credencial"
    >
      {aRechazar && conVerificacion(aRechazar) && (
        <div className="mb-3 flex flex-col gap-3">
          <CampoSelect
            id="motivoRechazo"
            etiqueta="Motivo"
            value={motivo}
            onChange={(e) => setMotivo(e.target.value as MotivoRechazoCredencial)}
          >
            <option value="">Elegí un motivo</option>
            {MOTIVOS_RECHAZO.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </CampoSelect>
          <AreaTexto
            id="detalleRechazo"
            etiqueta="Detalle para el Tutor"
            value={detalle}
            maxLength={500}
            onChange={(e) => setDetalle(e.target.value)}
          />
          {errorModal && <Alerta tono="peligro">{errorModal}</Alerta>}
        </div>
      )}
      {aRechazar && (
        <p>
          Es su intento {aRechazar.numeroIntento} de 3.{" "}
          {aRechazar.numeroIntento >= 3
            ? "Si la rechazás, agota el ciclo y tiene que esperar antes de volver a intentar (24 hs la primera vez, y se duplica en cada ciclo)."
            : "Si la rechazás, puede volver a cargarla."}{" "}
          Sin una credencial aprobada no aparece en las búsquedas.
        </p>
      )}
    </ModalConfirmacion>
  );

  const tope = aAprobar?.nivelMaximoQueRespalda;
  const modalAprobacion = (
    <ModalConfirmacion
      abierto={aAprobar !== null}
      onCerrar={() => setAAprobar(null)}
      cargando={aAprobar !== null && procesandoId === aAprobar.id}
      tono="primario"
      onConfirmar={async () => {
        if (!aAprobar) return;
        if (!metodo) {
          setErrorModal("Indicá cómo verificaste la credencial.");
          return;
        }
        const ok = await resolver(aAprobar.id, "APROBAR", {
          metodoVerificacion: metodo,
          especialidadesCubiertas: cubiertas,
        });
        if (ok) setAAprobar(null);
      }}
      titulo={aAprobar ? `Aprobar la credencial de ${aAprobar.tutorNombre} ${aAprobar.tutorApellido}` : ""}
      textoConfirmar="Aprobar credencial"
    >
      {aAprobar && (
        <div className="flex flex-col gap-4">
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 font-semibold text-tinta">¿Cómo la verificaste?</legend>
            <label className="flex items-center gap-2">
              <input type="radio" name="metodo" checked={metodo === "REGISTRO_OFICIAL"}
                onChange={() => setMetodo("REGISTRO_OFICIAL")} />
              Registro oficial
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name="metodo" checked={metodo === "SOLO_DOCUMENTO"}
                onChange={() => setMetodo("SOLO_DOCUMENTO")} />
              Solo documento
            </label>
            <p className="text-sm">
              Con &quot;solo documento&quot;, las especialidades quedan con respaldo documental, sin la insignia de
              verificada.
            </p>
          </fieldset>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 font-semibold text-tinta">¿Qué especialidades cubre?</legend>
            {!tope && <p className="text-sm">Este tipo de credencial no respalda especialidades.</p>}
            {(aAprobar.especialidades ?? []).map((e) => {
              const fuera = !tope || !nivelHasta(e.nivelMaximo, tope);
              return (
                <CampoCheckbox
                  key={e.id}
                  id={`cubre-${e.id}`}
                  etiqueta={nombreEspecialidad(e)}
                  disabled={fuera}
                  checked={cubiertas.includes(e.id)}
                  onChange={(ev) =>
                    setCubiertas((prev) => (ev.target.checked ? [...prev, e.id] : prev.filter((x) => x !== e.id)))
                  }
                  ayuda={fuera && tope ? `Esta credencial respalda hasta ${tope.toLowerCase()}.` : undefined}
                />
              );
            })}
          </fieldset>
          {errorModal && <Alerta tono="peligro">{errorModal}</Alerta>}
        </div>
      )}
    </ModalConfirmacion>
  );

  if (prohibido) {
    return (
      <p className="text-sm text-slate-500">
        No tenés permiso de Moderación y Seguridad para ver esta cola.
      </p>
    );
  }

  if (!credenciales || credenciales.length === 0) {
    return <p className="text-sm text-slate-500">No hay credenciales pendientes de revisión.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {modalRechazo}
      {modalAprobacion}
      {error && (
        <Alerta tono="error" className="flex items-start gap-2">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
          <span>{error}</span>
        </Alerta>
      )}
      <ul className="flex flex-col gap-3">
        {credenciales.map((c) => (
          <Tarjeta
            as="li"
            key={c.id}
            className="flex flex-wrap items-center justify-between gap-3 p-5"
          >
            <div>
              <p className="text-sm font-semibold text-slate-800">
                {c.tutorNombre} {c.tutorApellido}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {ETIQUETA_TIPO[c.tipoDocumento] ?? c.tipoDocumento} · Intento {c.numeroIntento} ·
                Enviado {formatFecha(c.createdAt)}
                {c.cicloEsperaHasta && ` · Backoff hasta ${formatFecha(c.cicloEsperaHasta)}`}
              </p>
              {conVerificacion(c) && <DatosVerificacion c={c} />}
            </div>
            <div className="flex gap-2">
              <Boton
                variante="secundario"
                tamano="sm"
                onClick={() => verDocumento(c.id)}
                disabled={abriendoId === c.id}
              >
                {abriendoId === c.id
                  ? "Abriendo…"
                  : documento?.id === c.id
                    ? "Ocultar documento"
                    : "Ver documento"}
              </Boton>
              <Boton
                variante="peligro"
                tamano="sm"
                onClick={() => abrirRechazar(c)}
                disabled={procesandoId === c.id}
              >
                Rechazar
              </Boton>
              <Boton
                tamano="sm"
                onClick={() => (conVerificacion(c) ? abrirAprobar(c) : resolver(c.id, "APROBAR"))}
                disabled={procesandoId === c.id}
              >
                {procesandoId === c.id ? "Procesando…" : "Aprobar"}
              </Boton>
            </div>
            {documento?.id === c.id && (
              <div className="w-full overflow-hidden rounded-lg border border-slate-200">
                {documento.tipo.startsWith("image/") ? (
                  // eslint-disable-next-line @next/next/no-img-element -- blob local, sin optimización posible
                  <img
                    src={documento.url}
                    alt={`Credencial de ${c.tutorNombre} ${c.tutorApellido}`}
                    className="max-h-[70vh] w-full object-contain"
                  />
                ) : documento.tipo === "application/pdf" ? (
                  // Sin sandbox a propósito: Chrome bloquea su visor de PDF en un iframe con
                  // sandbox. El backend ya verificó la firma %PDF y sirve application/pdf:
                  // el navegador lo abre con su visor aislado, nunca como HTML.
                  // oxlint-disable-next-line react/iframe-missing-sandbox
                  <iframe
                    src={documento.url}
                    title={`Credencial de ${c.tutorNombre} ${c.tutorApellido}`}
                    className="h-[70vh] w-full"
                  />
                ) : (
                  <p className="p-4 text-sm text-slate-500">
                    El archivo no es PDF ni imagen (fue subido antes de la validación de
                    formato).{" "}
                    <a
                      href={documento.url}
                      download={`credencial-${c.id}`}
                      className="font-medium text-slate-700 underline"
                    >
                      Descargarlo
                    </a>{" "}
                    y revisarlo con cuidado antes de decidir.
                  </p>
                )}
              </div>
            )}
          </Tarjeta>
        ))}
      </ul>
    </div>
  );
}

/** ADR-M1-06: lo que el Admin necesita para consultar el registro oficial. */
function DatosVerificacion({ c }: { c: CredencialCola }) {
  const datos = [c.institucion, c.denominacion, c.anioEgreso].filter((x) => x !== undefined && x !== null && x !== "");
  const enlace = c.enlaceVerificacion;
  return (
    <div className="mt-2 flex flex-col gap-1 text-sm text-slate-700">
      <p className="font-semibold">DNI {c.tutorDni}</p>
      {datos.length > 0 && <p>{datos.join(" · ")}</p>}
      {c.esDigital && <p>Título digital</p>}
      {enlace &&
        (enlace.startsWith("https://") ? (
          <p>
            Enlace de verificación:{" "}
            <a href={enlace} target="_blank" rel="noopener noreferrer" className="break-all underline">
              {enlace}
            </a>
          </p>
        ) : (
          <p>Código de verificación: {enlace}</p>
        ))}
      {c.nombreCoincide === false && (
        <Alerta tono="aviso">El nombre del documento no coincide con el del DNI.</Alerta>
      )}
      {c.nombreCoincide === undefined && (
        <p className="text-slate-500">No se pudo comparar el nombre del documento automáticamente.</p>
      )}
      {c.pasoASeguir && (
        <p>
          <strong>Paso a seguir:</strong> {c.pasoASeguir}
        </p>
      )}
      {c.especialidades && c.especialidades.length > 0 && (
        <p>Especialidades declaradas: {c.especialidades.map(nombreEspecialidad).join(", ")}</p>
      )}
    </div>
  );
}
