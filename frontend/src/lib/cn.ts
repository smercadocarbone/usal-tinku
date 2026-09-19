import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Une clases condicionales y resuelve conflictos de Tailwind (la última gana).
 *
 * Sin `twMerge`, un consumidor que pasa `className="bg-red-700"` a un
 * `<Boton>` que ya trae `bg-teal-600` termina con las dos clases y un
 * resultado que depende del orden en el CSS final, no del código.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
