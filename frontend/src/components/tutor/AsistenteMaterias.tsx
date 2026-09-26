"use client";

import { useRef, useState, type FormEvent } from "react";
import { Check, Plus, Sparkles } from "lucide-react";
import { mensajeDeError, sugerirTemas, type TemaSugerido } from "@/lib/api";
import { cn } from "@/lib/cn";
import { AreaTexto, Boton, Chip } from "@/components/ui";

const NIVELES = [
  { id: null, texto: "Todos" },
  { id: "primario", texto: "Primario" },
  { id: "secundario", texto: "Secundario" },
  { id: "universitario", texto: "Universitario" },
] as const;

const NOMBRE_NIVEL: Record<string, string> = { primario: "Primario", secundario: "Secundario", universitario: "Universitario" };

type Contenido =
  | { de: "tinku"; texto: string }
  | { de: "tutor"; texto: string }
  | { de: "sugerencias"; temas: TemaSugerido[] };
type Mensaje = Contenido & { id: number };

/**
 * Asistente de "Mis materias": en vez de recorrer todo el catálogo, el Tutor cuenta con sus
 * palabras qué enseña y a quién, y Tinku le sugiere temas del catálogo (mismo modelo que la
 * búsqueda de alumnos, sin costo de LLM). Él decide cuáles marcar; se guardan como siempre.
 */
export default function AsistenteMaterias({ seleccion, onAlternar }: { seleccion: Set<string>; onAlternar: (temaId: string) => void }) {
  const [mensajes, setMensajes] = useState<Mensaje[]>([
    {
      de: "tinku",
      id: 0,
      texto:
        "Contame con tus palabras qué enseñás y a quién. Por ejemplo: «Doy apoyo de matemática a chicos de secundaria, sobre todo funciones y ecuaciones».",
    },
  ]);
  const [texto, setTexto] = useState("");
  const [nivel, setNivel] = useState<string | null>(null);
  const [buscando, setBuscando] = useState(false);
  const siguienteId = useRef(1);

  function agregar(m: Contenido) {
    const id = siguienteId.current++;
    setMensajes((prev) => [...prev, { ...m, id }]);
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    const limpio = texto.trim();
    if (limpio.length < 10) return;
    agregar({ de: "tutor", texto: limpio });
    setTexto("");
    setBuscando(true);
    try {
      const temas = await sugerirTemas(limpio, nivel);
      if (temas.length === 0) {
        agregar({ de: "tinku", texto: "No encontré temas parecidos. Probá contándolo de otra forma, o elegilos en la lista de abajo." });
      } else {
        agregar({ de: "tinku", texto: "Estos temas del catálogo se parecen a lo que contaste. Marcá los que das:" });
        agregar({ de: "sugerencias", temas });
      }
    } catch (err) {
      agregar({
        de: "tinku",
        texto: mensajeDeError(err, "Ahora no puedo sugerirte temas.") + " Mientras tanto, podés elegirlos en la lista de abajo.",
      });
    } finally {
      setBuscando(false);
    }
  }

  return (
    <section aria-label="Asistente de materias" className="rounded-tarjeta border border-borde bg-fondo p-4 sm:p-5">
      <p className="flex items-center gap-2 text-sm font-bold text-marca-700">
        <Sparkles className="size-4" aria-hidden /> Te ayudo a elegir tus temas
      </p>
      <ol className="mt-3 flex list-none flex-col gap-3 p-0" aria-live="polite">
        {mensajes.map((m) =>
          m.de === "sugerencias" ? (
            <li key={m.id} className="flex flex-wrap gap-2">
              {m.temas.map((t) => {
                const elegido = seleccion.has(t.id);
                return (
                  <button
                    key={t.id}
                    type="button"
                    aria-pressed={elegido}
                    onClick={() => onAlternar(t.id)}
                    className={cn(
                      "flex min-h-11 items-center gap-2 rounded-2xl border px-3 py-2 text-left text-sm transition-colors",
                      elegido ? "border-marca-700 bg-marca-50" : "border-borde bg-superficie hover:border-borde-fuerte"
                    )}
                  >
                    {elegido ? <Check className="size-4 shrink-0 text-marca-700" aria-hidden /> : <Plus className="size-4 shrink-0 text-tinta-tenue" aria-hidden />}
                    <span>
                      <span className="block font-semibold">{t.nombre}</span>
                      <span className="block text-xs text-tinta-suave">
                        {t.materia} · {t.curso} · {NOMBRE_NIVEL[t.nivel] ?? t.nivel}
                      </span>
                    </span>
                  </button>
                );
              })}
            </li>
          ) : (
            <li key={m.id} className={cn("flex", m.de === "tutor" ? "justify-end" : "justify-start")}>
              <p
                className={cn(
                  "max-w-[85%] rounded-2xl px-4 py-2.5 text-[15px] leading-relaxed",
                  m.de === "tutor" ? "rounded-br-md bg-marca-700 text-white" : "rounded-bl-md bg-superficie text-tinta"
                )}
              >
                {m.texto}
              </p>
            </li>
          )
        )}
        {buscando && (
          <li className="text-sm text-tinta-tenue" role="status">
            Buscando temas…
          </li>
        )}
      </ol>

      <form className="mt-4 flex flex-col gap-3" onSubmit={enviar}>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Nivel">
          {NIVELES.map((n) => (
            <Chip key={n.texto} activo={nivel === n.id} onClick={() => setNivel(n.id)}>
              {n.texto}
            </Chip>
          ))}
        </div>
        <AreaTexto
          id="asistenteTexto"
          etiqueta="Qué enseñás"
          etiquetaOculta
          rows={3}
          maxLength={1000}
          value={texto}
          placeholder="Escribí acá…"
          onChange={(e) => setTexto(e.target.value)}
        />
        <Boton type="submit" className="self-end" cargando={buscando} textoCargando="Buscando…" disabled={texto.trim().length < 10}>
          Sugerirme temas
        </Boton>
      </form>
    </section>
  );
}
