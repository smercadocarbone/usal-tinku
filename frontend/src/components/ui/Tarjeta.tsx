import type { CSSProperties, ElementType, ReactNode } from "react";
import { cn } from "@/lib/cn";

export type VarianteTarjeta = "plana" | "elevada" | "interactiva";

export interface TarjetaProps {
  children: ReactNode;
  className?: string;
  /** `article`/`li` cuando la tarjeta es un ítem de una lista. */
  as?: ElementType;
  variante?: VarianteTarjeta;
  /** Atajo histórico de `variante="interactiva"`. */
  interactiva?: boolean;
  /** Sin padding interno (para listas con separadores o imágenes a sangre). */
  sinPadding?: boolean;
  style?: CSSProperties;
  id?: string;
}

const VARIANTES: Record<VarianteTarjeta, string> = {
  plana: "border border-borde bg-superficie",
  elevada: "border border-borde/70 bg-superficie shadow-elevado",
  // Toda la tarjeta es clickeable: el enlace de adentro lleva `after:absolute after:inset-0`
  // (ver `enlaceTarjeta`), así el foco va a un solo elemento real y se ve en el borde.
  interactiva:
    "relative border border-borde/70 bg-superficie shadow-elevado transition-[box-shadow,transform,border-color] duration-200 ease-out " +
    "hover:border-borde-fuerte hover:shadow-flotante motion-safe:hover:-translate-y-0.5 has-[a:focus-visible]:outline-2 has-[a:focus-visible]:outline-offset-2 has-[a:focus-visible]:outline-marca-600",
};

export default function Tarjeta({
  children,
  className,
  as: Componente = "div",
  variante,
  interactiva = false,
  sinPadding,
  style,
  id,
}: TarjetaProps) {
  const v = variante ?? (interactiva ? "interactiva" : "elevada");
  return (
    <Componente
      id={id}
      style={style}
      className={cn("rounded-tarjeta", !sinPadding && "p-5 sm:p-6", VARIANTES[v], className)}
    >
      {children}
    </Componente>
  );
}
Tarjeta.displayName = "Tarjeta";

/** Clases para el enlace principal de una `Tarjeta interactiva`: su área cubre la tarjeta entera. */
export const enlaceTarjeta =
  "no-underline text-inherit outline-none after:absolute after:inset-0 after:rounded-tarjeta after:content-['']";
