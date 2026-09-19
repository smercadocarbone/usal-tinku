import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class ReservaDetallePage extends BasePage {
  readonly botonEntrarClase: Locator;

  constructor(page: Page) {
    super(page);
    this.botonEntrarClase = page.getByRole("link", { name: "Entrar a la clase" });
  }

  async goto(reservaId: string): Promise<void> {
    await super.goto(`/cuenta/reservas/${reservaId}`);
  }

  estrella(n: number): Locator {
    return this.page.getByRole("button", { name: `${n} estrella${n > 1 ? "s" : ""}` });
  }

  get botonEnviarCalificacion(): Locator {
    return this.page.getByRole("button", { name: "Enviar calificación" });
  }
}
