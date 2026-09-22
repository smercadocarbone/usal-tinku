"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  getMiCredencial,
  mensajeDeError,
  subirCredencial,
  type CredencialPropia,
  type TipoCredencial,
} from "@/lib/api";
import { Alerta, Boton, Campo, CampoSelect, Cargando, Tarjeta } from "@/components/ui";

const TIPOS: { value: TipoCredencial; label: string }[] = [
  { value: "TITULO", label: "Título" },
  { value: "CERTIFICADO_ANALITICO", label: "Certificado analítico" },
  { value: "MATRICULA", label: "Matrícula" },
];

/**
 * Estado real de la credencial del Tutor (auditoría 2026-09-19): antes el
 * panel mostraba siempre el mismo texto fijo de "en revisión", sin importar
 * si el Tutor había cargado alguna vez una credencial — y no existía ninguna
 * pantalla para cargarla. Sin credencial aprobada el Tutor no aparece en el
 * matching (CredencialService#marcarAprobada), así que dejarlo sin forma de
 * cargarla era un callejón sin salida real, no solo un texto engañoso.
 */
export default function BannerCredencial() {
  const [credencial, setCredencial] = useState<CredencialPropia | null | undefined>(undefined);
  const [errorCarga, setErrorCarga] = useState(false);

  const [tipo, setTipo] = useState<TipoCredencial>("TITULO");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [errorSubida, setErrorSubida] = useState<string | null>(null);

  const cargar = useCallback(() => {
    setErrorCarga(false);
    getMiCredencial()
      .then(setCredencial)
      .catch(() => setErrorCarga(true));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!archivo) {
      setErrorSubida("Adjuntá el archivo de tu credencial.");
      return;
    }
    setEnviando(true);
    setErrorSubida(null);
    try {
      const c = await subirCredencial(tipo, archivo);
      setCredencial(c);
      setArchivo(null);
    } catch (err) {
      setErrorSubida(mensajeDeError(err, "No se pudo cargar la credencial."));
    } finally {
      setEnviando(false);
    }
  }

  if (credencial === undefined && !errorCarga) {
    return <Cargando>Cargando estado de tu credencial…</Cargando>;
  }

  if (errorCarga) {
    return (
      <Alerta tono="error" className="w-fit">
        No se pudo cargar el estado de tu credencial.
        <Boton variante="secundario" tamano="sm" className="mt-3 flex" onClick={cargar}>
          Reintentar
        </Boton>
      </Alerta>
    );
  }

  if (credencial?.estado === "APROBADO") {
    return <Alerta tono="exito" className="w-fit">Tu credencial académica fue aprobada.</Alerta>;
  }

  if (credencial?.estado === "PENDIENTE") {
    return (
      <Alerta tono="aviso" className="w-fit">
        Tu credencial está en revisión por el equipo de Tinku.
      </Alerta>
    );
  }

  // credencial === null (nunca cargó ninguna) o estado === "RECHAZADO".
  return (
    <div className="flex flex-col gap-3">
      <Alerta tono={credencial?.estado === "RECHAZADO" ? "error" : "aviso"} className="w-fit">
        {credencial?.estado === "RECHAZADO"
          ? "Tu credencial fue rechazada. Podés volver a cargarla."
          : "Todavía no cargaste tu credencial académica: sin ella, tu perfil no aparece en las búsquedas de los Adultos Responsables."}
      </Alerta>

      <Tarjeta className="w-full max-w-sm p-4">
        <form className="flex flex-col gap-3" onSubmit={onSubmit}>
          <CampoSelect
            id="tipoCredencial"
            etiqueta="Tipo de documento"
            value={tipo}
            onChange={(e) => setTipo(e.target.value as TipoCredencial)}
          >
            {TIPOS.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </CampoSelect>

          <Campo
            id="archivoCredencial"
            etiqueta="Archivo"
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
          />

          {errorSubida && <Alerta tono="error">{errorSubida}</Alerta>}

          <Boton type="submit" tamano="sm" className="w-fit" cargando={enviando} textoCargando="Cargando…">
            Cargar credencial
          </Boton>
        </form>
      </Tarjeta>
    </div>
  );
}
