"use client";

import Link from "next/link";
import { BadgeCheck, ChevronRight, Lock } from "lucide-react";
import { Avatar, Boton, Estrellas, Insignia, Precio, Tarjeta, enlaceTarjeta } from "@/components/ui";
import { TIEMPOS } from "@/lib/tiempos";
import { nombreCorto, useFotoTutor, type TutorPerfil } from "@/lib/tutores";

export interface TarjetaTutorProps {
  tutor: TutorPerfil;
  /** Búsqueda de un menor: el tutor todavía no está autorizado por su adulto. */
  noAutorizado?: boolean;
  avisoAutorizacion?: boolean;
  onSolicitarAutorizacion?: () => void;
}

/** Tarjeta de resultado (UX-04 §1): toda la tarjeta lleva al perfil. */
export default function TarjetaTutor({ tutor, noAutorizado, avisoAutorizacion, onSolicitarAutorizacion }: TarjetaTutorProps) {
  const foto = useFotoTutor(tutor.id, tutor.tieneFoto);
  const nombre = `${tutor.nombre} ${tutor.apellido}`.trim();
  const materias = tutor.materias.slice(0, 3);
  const resto = tutor.materias.length - materias.length;
  const nuevo = tutor.calificacionPromedio === null || tutor.cantidadCalificaciones < TIEMPOS.minimoCalificaciones;

  return (
    <Tarjeta as="article" variante="interactiva" sinPadding className="flex flex-col">
      <div className="flex flex-1 flex-col gap-4 p-5">
        <div className="flex items-start gap-4">
          <Avatar nombre={tutor.nombre} apellido={tutor.apellido} semilla={tutor.id} foto={foto} tamano="lg" verificado={tutor.verificado} />
          <div className="min-w-0 flex-1">
            <h2 className="truncate text-lg font-bold">
              <Link href={`/tutores/${tutor.id}`} className={enlaceTarjeta}>
                {nombre}
              </Link>
            </h2>
            <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
              {nuevo ? (
                <Insignia tono="acento" tamano="sm">Tutor nuevo</Insignia>
              ) : (
                <Estrellas valor={tutor.calificacionPromedio!} cantidad={tutor.cantidadCalificaciones} />
              )}
              {tutor.verificado && (
                <span className="inline-flex items-center gap-1 text-[13px] font-semibold text-exito">
                  <BadgeCheck className="size-4" aria-hidden /> Título verificado
                </span>
              )}
            </div>
          </div>
        </div>

        {materias.length > 0 && (
          <p className="text-[15px] leading-relaxed text-tinta-suave">
            {materias.join(" · ")}
            {resto > 0 && <span className="text-tinta-tenue"> y {resto} más</span>}
          </p>
        )}

        <div className="mt-auto flex items-end justify-between gap-3 border-t border-borde pt-4">
          <div>
            <Precio valor={tutor.precioHora} tamano="md" />
            {tutor.precioHora !== null && <span className="ml-1 text-sm text-tinta-tenue">por hora</span>}
          </div>
          <span className="inline-flex items-center gap-0.5 text-sm font-bold text-tinta" aria-hidden>
            Ver perfil <ChevronRight className="size-4" />
          </span>
        </div>
      </div>

      {noAutorizado && (
        // `relative z-10`: por encima del enlace que cubre la tarjeta.
        <div className="relative z-10 flex flex-col gap-2 rounded-b-tarjeta border-t border-borde bg-aviso-suave px-5 py-3">
          <p className="flex items-center gap-2 text-sm font-semibold text-[#7c2d12]">
            <Lock className="size-4" aria-hidden /> Todavía no está autorizado por tu adulto responsable
          </p>
          <Boton variante="secundario" tamano="sm" className="w-fit" onClick={onSolicitarAutorizacion}>
            Solicitar autorización
          </Boton>
          {avisoAutorizacion && (
            <p className="text-[13px] text-tinta-suave" role="status">
              Pedile a tu adulto responsable que entre a su cuenta y autorice a {nombreCorto(tutor.nombre, tutor.apellido)}. Después vas a poder pedirle clases.
            </p>
          )}
        </div>
      )}
    </Tarjeta>
  );
}
