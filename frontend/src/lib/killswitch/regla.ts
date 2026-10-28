import { MUESTRAS_PARA_CONFIRMAR, UMBRAL_DETECCION, UMBRAL_DETECCION_SALUD } from "./config";

/** Lógica pura del kill-switch (ADR-M3-07 §4): sin TF.js ni DOM, se testea en Node. */
export type ClaseNsfw = "Drawing" | "Hentai" | "Neutral" | "Porn" | "Sexy";
export interface Prediccion {
  className: ClaseNsfw;
  probability: number;
}

/**
 * ADR-M3-07 §4: con un menor cuentan Porn+Hentai+Sexy; entre adultos, Porn+Hentai. §4bis (modo
 * salud, clase de Medicina o Anatomía entre adultos): solo Porn, porque las láminas anatómicas
 * caen en Hentai («dibujos pornográficos» para NSFWJS). Con un menor el modo salud no cuenta.
 */
export function puntaje(predicciones: Prediccion[], hayMenor: boolean, contenidoAnatomico = false): number {
  const salud = contenidoAnatomico && !hayMenor;
  let total = 0;
  for (const p of predicciones) {
    if (
      p.className === "Porn" ||
      (!salud && p.className === "Hentai") ||
      (hayMenor && p.className === "Sexy")
    ) {
      total += p.probability;
    }
  }
  return total;
}

/** ADR-M3-07 §4bis: 0,95 en modo salud (solo entre adultos); 0,85 en cualquier otro caso. */
export function umbralDe(hayMenor: boolean, contenidoAnatomico: boolean): number {
  return contenidoAnatomico && !hayMenor ? UMBRAL_DETECCION_SALUD : UMBRAL_DETECCION;
}

/** Cuenta muestras consecutivas sobre el umbral, por track. */
export class ContadorDetecciones {
  private readonly cuentas = new Map<string, number>();

  /** Devuelve true SOLO la vez que el track llega a MUESTRAS_PARA_CONFIRMAR. */
  registrar(trackId: string, p: number, umbral = UMBRAL_DETECCION): boolean {
    if (p < umbral) {
      this.cuentas.set(trackId, 0);
      return false;
    }
    const n = (this.cuentas.get(trackId) ?? 0) + 1;
    // Después de confirmar queda por encima del tope: no vuelve a devolver true hasta reiniciar.
    this.cuentas.set(trackId, Math.min(n, MUESTRAS_PARA_CONFIRMAR + 1));
    return n === MUESTRAS_PARA_CONFIRMAR;
  }

  /** Reinicia ese track (después de un "No" en la rama adultos, o al reactivar el clasificador). */
  reiniciar(trackId: string): void {
    this.cuentas.delete(trackId);
  }

  quitar(trackId: string): void {
    this.cuentas.delete(trackId);
  }

  reiniciarTodo(): void {
    this.cuentas.clear();
  }
}
