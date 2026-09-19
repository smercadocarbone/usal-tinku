import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class PagarPage extends BasePage {
  readonly botonConfirmarSimulado: Locator;
  readonly botonMercadoPago: Locator;
  readonly botonReintentar: Locator;
  readonly botonContactarSoporte: Locator;
  readonly campoDetalleSoporte: Locator;
  readonly botonEnviarSoporte: Locator;

  constructor(page: Page) {
    super(page);
    this.botonConfirmarSimulado = page.getByRole("button", {
      name: "Confirmar reserva (simulado)",
    });
    this.botonMercadoPago = page.getByRole("button", { name: "Pagar con MercadoPago" });
    this.botonReintentar = page.getByRole("button", { name: "Reintentar" });
    this.botonContactarSoporte = page.getByRole("button", { name: "Contactar a soporte" });
    this.campoDetalleSoporte = page.getByLabel("Contanos qué pasó");
    this.botonEnviarSoporte = page.getByRole("button", { name: "Enviar a soporte" });
  }

  async goto(reservaId: string): Promise<void> {
    await super.goto(`/pagar?reserva=${reservaId}`);
  }

  async contactarSoporte(): Promise<void> {
    await this.botonContactarSoporte.click();
    await this.botonEnviarSoporte.click();
  }
}
