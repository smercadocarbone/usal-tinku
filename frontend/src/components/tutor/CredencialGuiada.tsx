"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Alerta, Boton, Campo, CampoCheckbox, CampoSelect, Cargando, SubidaArchivo } from "@/components/ui";
import { getMiCredencial, mensajeDeError, type CredencialPropia } from "@/lib/api";
import {
  TIPOS_CREDENCIAL,
  prellenarCredencial,
  subirCredencialEstructurada,
  type TipoCredencialEspecialidades,
} from "@/lib/especialidades";

/** Ayuda según el tipo: qué registro oficial se va a consultar (SPIKE §2). */
const AYUDA_TIPO: Record<TipoCredencialEspecialidades, string> = {
  SECUNDARIO:
    "Si es digital (desde noviembre de 2023), está en Mi Argentina con QR. Si es en papel, queda como respaldo documental.",
  TERCIARIO_NO_DOCENTE: "Si es digital, pegá el enlace del QR de Mi Argentina.",
  PROFESORADO: "Si es digital, pegá el enlace del QR de Mi Argentina; si no, lo revisamos con la validez nacional del plan.",
  UNIVERSITARIO: "Lo consultamos en el Registro Público de Graduados Universitarios con tu DNI.",
  ALUMNO_UNIVERSITARIO: "Pegá el código o el enlace de validación del certificado del SIU-Guaraní de tu universidad.",
  MATRICULA_PROFESIONAL: "La buscamos en el buscador de tu colegio o consejo profesional.",
  OTRO: "Lo revisa una persona del equipo.",
};

/**
 * ADR-M1-06 (E3): carga de la credencial guiada por tipo, con datos estructurados, prellenado por
 * OCR y el consentimiento expreso para consultar registros oficiales con el DNI. Reemplaza a
 * `BannerCredencial` solo cuando el backend informa el flag encendido.
 */
export default function CredencialGuiada() {
  const [credencial, setCredencial] = useState<CredencialPropia | null | undefined>(undefined);
  const [errorCarga, setErrorCarga] = useState(false);

  const [tipo, setTipo] = useState<TipoCredencialEspecialidades>("SECUNDARIO");
  const [institucion, setInstitucion] = useState("");
  const [denominacion, setDenominacion] = useState("");
  const [anio, setAnio] = useState("");
  const [digital, setDigital] = useState(false);
  const [enlace, setEnlace] = useState("");
  const [acepta, setAcepta] = useState(false);
  const [archivo, setArchivo] = useState<File | null>(null);
  const [leyendo, setLeyendo] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(() => {
    setErrorCarga(false);
    getMiCredencial()
      .then(setCredencial)
      .catch(() => setErrorCarga(true));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const exigeEnlace = digital || tipo === "ALUMNO_UNIVERSITARIO";

  async function leerArchivo() {
    if (!archivo) return;
    setLeyendo(true);
    setError(null);
    try {
      const p = await prellenarCredencial(archivo);
      if (p.institucion) setInstitucion(p.institucion);
      if (p.denominacion) setDenominacion(p.denominacion);
      if (p.anioEgreso) setAnio(String(p.anioEgreso));
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos leer los datos del archivo. Completalos a mano."));
    } finally {
      setLeyendo(false);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const anioNumero = Number.parseInt(anio, 10);
    const faltante = !archivo
      ? "Adjuntá el archivo de tu credencial."
      : !institucion.trim()
        ? "Indicá la institución que emitió el documento."
        : !denominacion.trim()
          ? "Indicá el nombre del título o certificado."
          : !Number.isFinite(anioNumero)
            ? "Indicá el año de egreso (o de cursada)."
            : exigeEnlace && !enlace.trim()
              ? "Indicá el enlace o el código de verificación."
              : !acepta
                ? "Tenés que aceptar la consulta a los registros oficiales."
                : null;
    if (faltante || !archivo) {
      setError(faltante);
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      const c = await subirCredencialEstructurada(
        {
          tipoDocumento: tipo,
          institucion: institucion.trim(),
          denominacion: denominacion.trim(),
          anioEgreso: anioNumero,
          esDigital: digital,
          enlaceVerificacion: enlace.trim() || undefined,
          aceptaConsultaRegistros: acepta,
        },
        archivo
      );
      setCredencial(c);
      setArchivo(null);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo cargar la credencial."));
    } finally {
      setEnviando(false);
    }
  }

  if (credencial === undefined && !errorCarga) {
    return <Cargando>Cargando estado de tu credencial…</Cargando>;
  }
  if (errorCarga) {
    return (
      <Alerta tono="peligro" accion={<Boton variante="secundario" tamano="sm" onClick={cargar}>Reintentar</Boton>}>
        No se pudo cargar el estado de tu credencial.
      </Alerta>
    );
  }
  if (credencial?.estado === "APROBADO") {
    return <Alerta tono="exito">Tu credencial académica fue aprobada.</Alerta>;
  }
  if (credencial?.estado === "PENDIENTE") {
    return (
      <Alerta tono={credencial.tieneAprobada ? "exito" : "aviso"}>
        {credencial.tieneAprobada
          ? "Tu perfil está verificado. Tu nueva credencial está en revisión."
          : "Tu credencial está en revisión por el equipo de Tinku."}
      </Alerta>
    );
  }

  return (
    <section className="flex flex-col gap-4">
      <h2 className="text-lg font-bold">Tu credencial</h2>
      {credencial?.estado === "RECHAZADO" ? (
        <Alerta tono="peligro" titulo="Tu credencial fue rechazada. Podés volver a cargarla.">
          {credencial.motivoRechazoTexto && <p>{credencial.motivoRechazoTexto}</p>}
          {credencial.detalleRechazo && <p>{credencial.detalleRechazo}</p>}
        </Alerta>
      ) : (
        <Alerta tono="aviso">
          Cargá el título o certificado que respalda tus especialidades. Lo verificamos contra el registro oficial
          cuando existe; mientras tanto, tus especialidades se muestran como declaradas.
        </Alerta>
      )}

      <form className="flex max-w-md flex-col gap-4" onSubmit={onSubmit} noValidate>
        <CampoSelect
          id="tipoCredencial"
          etiqueta="Tipo de documento"
          value={tipo}
          onChange={(e) => setTipo(e.target.value as TipoCredencialEspecialidades)}
          ayuda={AYUDA_TIPO[tipo]}
        >
          {TIPOS_CREDENCIAL.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </CampoSelect>

        <SubidaArchivo
          id="archivoCredencial"
          etiqueta="Archivo"
          formatosTexto="PDF, JPG o PNG"
          accept="application/pdf,image/png,image/jpeg"
          maxMb={5}
          archivo={archivo}
          onCambio={setArchivo}
          ayuda="Una foto nítida y derecha, o el PDF original."
        />
        <Boton
          variante="secundario"
          tamano="sm"
          className="w-fit"
          onClick={leerArchivo}
          disabled={!archivo}
          cargando={leyendo}
          textoCargando="Leyendo…"
        >
          Leer datos del archivo
        </Boton>

        <Campo id="institucion" etiqueta="Institución" value={institucion} maxLength={200}
          onChange={(e) => setInstitucion(e.target.value)} />
        <Campo id="denominacion" etiqueta="Título o certificado" value={denominacion} maxLength={200}
          onChange={(e) => setDenominacion(e.target.value)} />
        <Campo id="anioEgreso" etiqueta="Año de egreso" inputMode="numeric" maxLength={4} value={anio}
          onChange={(e) => setAnio(e.target.value.replace(/\D/g, ""))}
          ayuda={tipo === "ALUMNO_UNIVERSITARIO" ? "El año que estás cursando." : undefined} />

        <CampoCheckbox id="esDigital" etiqueta="Es un título digital (con QR o enlace)" checked={digital}
          onChange={(e) => setDigital(e.target.checked)} />
        <Campo id="enlaceVerificacion" etiqueta="Enlace o código de verificación" value={enlace} maxLength={500}
          onChange={(e) => setEnlace(e.target.value)}
          ayuda={exigeEnlace
            ? "Obligatorio. El enlace tiene que empezar con https:// y ser de argentina.gob.ar, registrograduados.siu.edu.ar o de tu universidad (.edu.ar)."
            : "Opcional."} />

        <div className="flex flex-col gap-2 rounded-lg border border-borde p-3">
          {/* PENDIENTE DEL DUEÑO: el texto legal de la cláusula (Ley 25.326). No se inventa acá. */}
          <p className="text-sm font-semibold text-aviso">Texto legal pendiente del dueño</p>
          <CampoCheckbox
            id="aceptaConsultaRegistros"
            etiqueta="Acepto que el equipo de Tinku consulte mis datos en los registros oficiales con mi DNI para verificar este título."
            checked={acepta}
            onChange={(e) => setAcepta(e.target.checked)}
          />
        </div>

        {error && <Alerta tono="peligro">{error}</Alerta>}

        <Boton type="submit" className="w-fit" cargando={enviando} textoCargando="Cargando…">
          Cargar credencial
        </Boton>
      </form>
    </section>
  );
}
