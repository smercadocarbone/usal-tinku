import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class ReservarPage extends BasePage {
  readonly selectHora: Locator;
  readonly botonReservar: Locator;

  constructor(page: Page) {
    super(page);
    this.selectHora = page.getByLabel("Horario");
    this.botonReservar = page.getByRole("button", { name: "Confirmar y pagar" });
  }

  async goto(tutorId: string): Promise<void> {
    await super.goto(`/reservar?tutor=${tutorId}`);
  }

  franja(etiqueta: string): Locator {
    return this.page.getByRole("button", { name: etiqueta });
  }

  /** Paso "Cuándo": el primer día con horarios ya viene elegido; se toca el horario. */
  async elegirHorario(etiquetaHorario: string): Promise<void> {
    await this.page.getByRole("button", { name: new RegExp(etiquetaHorario) }).click();
    await this.page.getByRole("button", { name: "Continuar", exact: true }).click();
  }

  async confirmar(): Promise<void> {
    await this.botonReservar.click();
  }
}
