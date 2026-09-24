"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { FileText, ImageUp, RefreshCw, X } from "lucide-react";
import { cn } from "@/lib/cn";

export interface SubidaArchivoProps {
  id?: string;
  etiqueta: ReactNode;
  /** Qué formatos acepta, en humano: "JPG, PNG o PDF". Se muestra ANTES de elegir. */
  formatosTexto: string;
  /** `accept` del input ("image/jpeg,image/png,application/pdf"). */
  accept: string;
  /** Tamaño máximo en MB. */
  maxMb: number;
  archivo: File | null;
  onCambio: (archivo: File | null) => void;
  /** Guía corta debajo (ej. cómo sacar la foto del DNI). */
  ayuda?: ReactNode;
  error?: string | null;
  /** `camara`: en mobile ofrece abrir la cámara trasera. */
  capturar?: boolean;
  disabled?: boolean;
}

function peso(bytes: number): string {
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1).replace(".", ",")} MB`;
}

/**
 * Reemplaza el `<input type="file">` nativo (que en inglés dice "Choose File").
 * Zona para arrastrar o tocar, vista previa (imagen) o nombre y peso (PDF), y error
 * humano si no cumple. La validación real (magic bytes) la hace siempre el backend.
 */
export default function SubidaArchivo({
  id: idProp,
  etiqueta,
  formatosTexto,
  accept,
  maxMb,
  archivo,
  onCambio,
  ayuda,
  error: errorExterno,
  capturar,
  disabled,
}: SubidaArchivoProps) {
  const idAuto = useId();
  const id = idProp ?? idAuto;
  const input = useRef<HTMLInputElement>(null);
  const [arrastrando, setArrastrando] = useState(false);
  const [errorLocal, setErrorLocal] = useState<string | null>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const error = errorLocal ?? errorExterno ?? null;

  useEffect(() => {
    if (!archivo || !archivo.type.startsWith("image/")) {
      setPreview(null);
      return;
    }
    const url = URL.createObjectURL(archivo);
    setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [archivo]);

  function elegir(f: File | undefined | null) {
    setErrorLocal(null);
    if (!f) return;
    const tipos = accept.split(",").map((t) => t.trim());
    if (!tipos.some((t) => (t.endsWith("/*") ? f.type.startsWith(t.slice(0, -1)) : f.type === t))) {
      setErrorLocal(`Ese archivo no es de un formato que aceptemos. Subí ${formatosTexto}.`);
      return;
    }
    if (f.size > maxMb * 1024 * 1024) {
      setErrorLocal(`El archivo pesa ${peso(f.size)} y el máximo es ${maxMb} MB. Probá con uno más liviano.`);
      return;
    }
    onCambio(f);
  }

  const descripcion = [`${id}-formatos`, ayuda ? `${id}-ayuda` : null, error ? `${id}-error` : null]
    .filter(Boolean)
    .join(" ");

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-semibold text-tinta">
        {etiqueta}
      </label>
      <input
        ref={input}
        id={id}
        type="file"
        accept={accept}
        capture={capturar ? "environment" : undefined}
        className="sr-only"
        disabled={disabled}
        aria-describedby={descripcion}
        aria-invalid={error ? true : undefined}
        onChange={(e) => {
          elegir(e.target.files?.[0]);
          e.target.value = "";
        }}
      />
      {archivo ? (
        <div className="flex items-center gap-4 rounded-control border border-borde-fuerte bg-superficie p-3">
          {preview ? (
            // eslint-disable-next-line @next/next/no-img-element -- blob local de vista previa
            <img src={preview} alt="" className="size-16 shrink-0 rounded-lg object-cover" />
          ) : (
            <span aria-hidden className="flex size-16 shrink-0 items-center justify-center rounded-lg bg-marca-50 text-marca-700">
              <FileText className="size-7" />
            </span>
          )}
          <div className="min-w-0 flex-1">
            <p className="truncate text-[15px] font-semibold text-tinta">{archivo.name}</p>
            <p className="text-[13px] text-tinta-tenue">{peso(archivo.size)}</p>
          </div>
          <div className="flex shrink-0 gap-1">
            <button
              type="button"
              disabled={disabled}
              onClick={() => input.current?.click()}
              className="inline-flex size-11 cursor-pointer items-center justify-center rounded-full text-tinta-suave hover:bg-superficie-hundida hover:text-tinta"
              aria-label="Cambiar archivo"
            >
              <RefreshCw className="size-[18px]" aria-hidden />
            </button>
            <button
              type="button"
              disabled={disabled}
              onClick={() => onCambio(null)}
              className="inline-flex size-11 cursor-pointer items-center justify-center rounded-full text-tinta-suave hover:bg-superficie-hundida hover:text-tinta"
              aria-label="Quitar archivo"
            >
              <X className="size-[18px]" aria-hidden />
            </button>
          </div>
        </div>
      ) : (
        <div
          role="presentation"
          onClick={() => !disabled && input.current?.click()}
          onDragOver={(e) => {
            e.preventDefault();
            setArrastrando(true);
          }}
          onDragLeave={() => setArrastrando(false)}
          onDrop={(e) => {
            e.preventDefault();
            setArrastrando(false);
            if (!disabled) elegir(e.dataTransfer.files?.[0]);
          }}
          className={cn(
            "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-control border-2 border-dashed px-4 py-7 text-center transition-colors",
            arrastrando ? "border-marca-600 bg-marca-50" : "border-borde-control bg-superficie hover:border-marca-600 hover:bg-marca-50/50",
            error && "border-peligro",
            disabled && "cursor-not-allowed opacity-60"
          )}
        >
          <span aria-hidden className="flex size-12 items-center justify-center rounded-full bg-marca-50 text-marca-700">
            <ImageUp className="size-6" />
          </span>
          <span className="text-[15px] font-semibold text-tinta">
            <span className="text-marca-700 underline underline-offset-2">Elegí un archivo</span>
            <span className="hidden sm:inline"> o arrastralo acá</span>
          </span>
        </div>
      )}
      <span id={`${id}-formatos`} className="text-[13px] text-tinta-tenue">
        {formatosTexto} · hasta {maxMb} MB
      </span>
      {ayuda && (
        <div id={`${id}-ayuda`} className="text-[13px] text-tinta-tenue">
          {ayuda}
        </div>
      )}
      {error && (
        <span id={`${id}-error`} role="alert" className="text-[13px] font-medium text-peligro">
          {error}
        </span>
      )}
    </div>
  );
}
SubidaArchivo.displayName = "SubidaArchivo";
