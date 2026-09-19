import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class PagarPage extends BasePage {
  readonly botonConfirmarSimulado: Locator;
  readonly botonMercadoPago: Locator;

  constructor(page: Page) {
    super(page);
    this.botonConfirmarSimulado = page.getByRole("button", {
      name: "Confirmar reserva (simulado)",
    });
    this.botonMercadoPago = page.getByRole("button", { name: "Pagar con MercadoPago" });
  }

  async goto(reservaId: string): Promise<void> {
    await super.goto(`/pagar?reserva=${reservaId}`);
  }
}
