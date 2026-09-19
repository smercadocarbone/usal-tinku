import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class ReservarPage extends BasePage {
  readonly selectHora: Locator;
  readonly botonReservar: Locator;

  constructor(page: Page) {
    super(page);
    this.selectHora = page.getByLabel("Horario");
    this.botonReservar = page.getByRole("button", { name: "Reservar y pagar" });
  }

  async goto(tutorId: string): Promise<void> {
    await super.goto(`/reservar?tutor=${tutorId}`);
  }

  franja(etiqueta: string): Locator {
    return this.page.getByRole("button", { name: etiqueta });
  }

  async elegirFranjaYHora(etiquetaFranja: string, hora: string): Promise<void> {
    await this.franja(etiquetaFranja).click();
    await this.selectHora.selectOption(hora);
  }

  async confirmar(): Promise<void> {
    await this.botonReservar.click();
  }
}
