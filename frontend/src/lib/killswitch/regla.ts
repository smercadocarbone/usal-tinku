import { MUESTRAS_PARA_CONFIRMAR, UMBRAL_DETECCION } from "./config";

/** Lógica pura del kill-switch (ADR-M3-07 §4): sin TF.js ni DOM, se testea en Node. */
export type ClaseNsfw = "Drawing" | "Hentai" | "Neutral" | "Porn" | "Sexy";
export interface Prediccion {
  className: ClaseNsfw;
  probability: number;
}

/** ADR-M3-07 §4: con un menor cuentan Porn+Hentai+Sexy; entre adultos, Porn+Hentai. */
export function puntaje(predicciones: Prediccion[], hayMenor: boolean): number {
  let total = 0;
  for (const p of predicciones) {
    if (p.className === "Porn" || p.className === "Hentai" || (hayMenor && p.className === "Sexy")) {
      total += p.probability;
    }
  }
  return total;
}

/** Cuenta muestras consecutivas sobre el umbral, por track. */
export class ContadorDetecciones {
  private readonly cuentas = new Map<string, number>();

  /** Devuelve true SOLO la vez que el track llega a MUESTRAS_PARA_CONFIRMAR. */
  registrar(trackId: string, p: number): boolean {
    if (p < UMBRAL_DETECCION) {
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
