"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen, Camera, ChevronRight, CircleDollarSign, Eye, Trash2 } from "lucide-react";
import { actualizarBioTutor, borrarFotoTutor, mensajeDeError, subirFotoTutor } from "@/lib/api";
import { usePerfilPropio } from "@/lib/usePerfil";
import { getTutor, invalidarFoto, useFotoTutor, type TutorPerfil } from "@/lib/tutores";
import BannerCredencial from "@/components/BannerCredencial";
import ChecklistTutor from "@/components/tutor/ChecklistTutor";
import SeccionCap from "@/components/tutor/SeccionCap";
import { AreaTexto, Avatar, Boton, Skeleton, Tarjeta, clasesBoton, useToast } from "@/components/ui";

const MAX_BIO = 500;
const MAX_FOTO_MB = 5;

/** "Mi perfil" del tutor (UX-06 + U1): lo que ve una familia y lo que falta completar. */
export default function PerfilTutorPage() {
  const toast = useToast();
  const yo = usePerfilPropio();
  const [perfil, setPerfil] = useState<TutorPerfil | null>(null);
  const [bio, setBio] = useState("");
  const [guardandoBio, setGuardandoBio] = useState(false);
  const [subiendo, setSubiendo] = useState(false);
  const idPropio = yo?.id;

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
    <div className="mx-auto flex max-w-3xl flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mi perfil</h1>
        {yo?.id && (
          <Link href={`/tutores/${yo.id}`} className={clasesBoton("secundario", "sm", "rounded-pastilla")}>
            <Eye className="size-4" aria-hidden /> Ver como lo ve una familia
          </Link>
        )}
      </div>

      <ChecklistTutor tutorId={yo?.id ?? null} />

      {/* Foto y presentación (U1) */}
      <Tarjeta>
        <h2 className="text-lg font-bold">Foto y presentación</h2>
        <p className="mt-1 text-sm text-tinta-suave">
          Son opcionales, pero son lo primero que mira una familia. No pongas teléfonos, emails ni redes: el equipo de moderación puede quitarlos.
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

      <section id="credencial" className="scroll-mt-24">
        <Tarjeta>
          <h2 className="mb-4 text-lg font-bold">Credencial académica</h2>
          <BannerCredencial />
        </Tarjeta>
      </section>

      <section id="menores" className="scroll-mt-24">
        <Tarjeta>
          <h2 className="mb-4 text-lg font-bold">Clases con menores</h2>
          <SeccionCap />
        </Tarjeta>
      </section>

      <ul className="grid list-none grid-cols-1 gap-3 p-0 sm:grid-cols-2">
        {[
          { href: "/cuenta/materias", icono: BookOpen, titulo: "Mis materias", texto: "Qué temas enseñás" },
          { href: "/cuenta/precio", icono: CircleDollarSign, titulo: "Mi precio", texto: "Cuánto cobrás por hora" },
        ].map(({ href, icono: I, titulo, texto }) => (
          <li key={href}>
            <Link href={href} className="flex min-h-16 items-center gap-4 rounded-tarjeta border border-borde bg-superficie p-5 text-tinta no-underline hover:border-borde-fuerte">
              <span aria-hidden className="flex size-11 items-center justify-center rounded-2xl bg-marca-50 text-marca-700">
                <I className="size-6" />
              </span>
              <span className="flex-1">
                <span className="block font-bold">{titulo}</span>
                <span className="block text-sm text-tinta-suave">{texto}</span>
              </span>
              <ChevronRight className="size-5 text-tinta-tenue" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
