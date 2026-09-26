"use client";

import { useCallback, useEffect, useState } from "react";
import { Camera, ExternalLink, Trash2 } from "lucide-react";
import { actualizarBioTutor, borrarFotoTutor, mensajeDeError, subirFotoTutor } from "@/lib/api";
import { usePerfilPropio } from "@/lib/usePerfil";
import { getTutor, invalidarFoto, useFotoTutor, type TutorPerfil } from "@/lib/tutores";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import TarjetaTutor from "@/components/tutores/TarjetaTutor";
import { AreaTexto, Avatar, Boton, Insignia, Skeleton, Tabs, Tarjeta, clasesBoton, useToast } from "@/components/ui";

const MAX_BIO = 500;
const MAX_FOTO_MB = 5;

/**
 * Mi cuenta → Presentación (U1): foto y "Sobre mí", con una vista previa en vivo de cómo lo ve un
 * alumno o una familia (en la búsqueda y en el perfil) antes de guardar.
 */
export default function PresentacionPage() {
  const toast = useToast();
  const yo = usePerfilPropio();
  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [bio, setBio] = useState("");
  const [guardandoBio, setGuardandoBio] = useState(false);
  const [subiendo, setSubiendo] = useState(false);
  const idPropio = yo?.id;
  const [vista, setVista] = useState<"busqueda" | "perfil">("busqueda");

  const cargar = useCallback(() => {
    if (!idPropio) return;
    getTutor(idPropio)
      .then((p) => {
        setPerfil(p);
        setBio(p.bio ?? "");
      })
      .catch(() => setPerfil(null));
  }, [idPropio]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const foto = useFotoTutor(perfil ? `${perfil.id}` : null, perfil?.tieneFoto);

  async function guardarBio() {
    setGuardandoBio(true);
    try {
      await actualizarBioTutor(bio);
      toast.mostrar("Guardamos tu presentación");
      cargar();
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar tu presentación."), { tono: "error" });
    } finally {
      setGuardandoBio(false);
    }
  }

  async function elegirFoto(archivo: File | undefined) {
    if (!archivo || !perfil) return;
    if (!["image/jpeg", "image/png"].includes(archivo.type)) {
      toast.mostrar("La foto tiene que ser JPG o PNG.", { tono: "error" });
      return;
    }
    if (archivo.size > MAX_FOTO_MB * 1024 * 1024) {
      toast.mostrar(`La foto puede pesar hasta ${MAX_FOTO_MB} MB.`, { tono: "error" });
      return;
    }
    setSubiendo(true);
    try {
      await subirFotoTutor(archivo);
      invalidarFoto(perfil.id);
      toast.mostrar("Actualizamos tu foto");
      cargar();
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos subir la foto."), { tono: "error" });
    } finally {
      setSubiendo(false);
    }
  }

  async function quitarFoto() {
    if (!perfil) return;
    try {
      await borrarFotoTutor();
      invalidarFoto(perfil.id);
      toast.mostrar("Sacamos tu foto");
      cargar();
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos sacar la foto."), { tono: "error" });
    }
  }

  return (
    <SubpaginaTutor titulo="Presentación" descripcion="Tu foto y cómo te presentás. Es lo primero que mira una familia.">
      {/* Foto y presentación (U1) */}
      <Tarjeta>
        <p className="text-sm text-tinta-suave">
          La foto es obligatoria para aparecer en las búsquedas. No pongas teléfonos, emails ni redes: el equipo de moderación puede quitarlos.
        </p>
        <div className="mt-5 flex flex-col gap-6 sm:flex-row sm:items-start">
          <div className="flex flex-col items-center gap-3">
            {perfil ? (
              <Avatar nombre={perfil.nombre} apellido={perfil.apellido} semilla={perfil.id} foto={foto} tamano="xl" />
            ) : (
              <Skeleton className="size-24 rounded-full" />
            )}
            <label
              htmlFor="fotoPerfil"
              className={clasesBoton("secundario", "sm", "cursor-pointer has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-marca-600")}
            >
              <Camera className="size-4" aria-hidden /> {subiendo ? "Subiendo…" : perfil?.tieneFoto ? "Cambiar foto" : "Subir foto"}
              <input
                id="fotoPerfil"
                type="file"
                accept="image/jpeg,image/png"
                className="sr-only"
                disabled={subiendo || !perfil}
                onChange={(e) => {
                  void elegirFoto(e.target.files?.[0]);
                  e.target.value = "";
                }}
              />
            </label>
            {perfil?.tieneFoto && (
              <Boton variante="fantasma" tamano="sm" icono={<Trash2 />} onClick={() => void quitarFoto()}>
                Sacar foto
              </Boton>
            )}
            <p className="text-center text-[12px] text-tinta-tenue">JPG o PNG · hasta {MAX_FOTO_MB} MB</p>
          </div>
          <div className="flex flex-1 flex-col gap-3">
            <AreaTexto
              id="bio"
              etiqueta="Sobre mí"
              rows={6}
              maxLength={MAX_BIO}
              contador
              value={bio}
              placeholder="Contá cómo das clases, a quién ayudás y qué experiencia tenés."
              onChange={(e) => setBio(e.target.value)}
            />
            <Boton
              className="w-fit"
              disabled={!perfil || bio.trim() === (perfil.bio ?? "")}
              cargando={guardandoBio}
              textoCargando="Guardando…"
              onClick={() => void guardarBio()}
            >
              Guardar presentación
            </Boton>
          </div>
        </div>
      </Tarjeta>


      <Tarjeta className="mt-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h3 className="text-lg font-bold">Vista previa</h3>
          {yo?.id && (
            <a href={`/tutores/${yo.id}`} target="_blank" rel="noreferrer" className={clasesBoton("fantasma", "sm")}>
              <ExternalLink className="size-4" aria-hidden /> Abrir mi perfil público
            </a>
          )}
        </div>
        <p className="mt-1 text-sm text-tinta-suave">Así te ve un alumno o una familia, con lo que escribiste aunque todavía no lo hayas guardado.</p>
        <Tabs
          className="mt-4"
          etiqueta="Vista previa"
          variante="segmentado"
          activo={vista}
          onCambio={setVista}
          opciones={[
            { id: "busqueda", label: "En la búsqueda" },
            { id: "perfil", label: "En tu perfil" },
          ]}
        />
        <div className="mt-4" aria-live="polite">
          {!perfil ? (
            <Skeleton className="h-40 w-full" />
          ) : vista === "busqueda" ? (
            <div className="pointer-events-none max-w-sm" inert>
              <TarjetaTutor tutor={{ ...perfil, bio: bio.trim() || null }} />
            </div>
          ) : (
            <div className="rounded-tarjeta border border-borde p-5" inert>
              <div className="flex items-center gap-4">
                <Avatar nombre={perfil.nombre} apellido={perfil.apellido} semilla={perfil.id} foto={foto} tamano="xl" verificado={perfil.verificado} />
                <div>
                  <p className="text-2xl font-extrabold">{perfil.nombre} {perfil.apellido}</p>
                  {perfil.materias.length > 0 && <p className="text-sm text-tinta-suave">{perfil.materias.slice(0, 4).join(" · ")}</p>}
                  {perfil.verificado && <Insignia tono="exito" tamano="sm" className="mt-1">Título verificado</Insignia>}
                </div>
              </div>
              <p className="mt-5 text-sm font-bold uppercase tracking-wider text-tinta-tenue">Sobre mí</p>
              {bio.trim() ? (
                <p className="mt-2 whitespace-pre-line text-[16px] leading-relaxed text-tinta-suave">{bio.trim()}</p>
              ) : (
                <p className="mt-2 text-[15px] italic text-tinta-tenue">Todavía no escribiste tu presentación: esta sección no se muestra.</p>
              )}
            </div>
          )}
        </div>
      </Tarjeta>
    </SubpaginaTutor>
  );
}
