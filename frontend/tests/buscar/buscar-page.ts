import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class BuscarPage extends BasePage {
  readonly inputBusqueda: Locator;
  readonly botonBuscar: Locator;

  constructor(page: Page) {
    super(page);
    this.inputBusqueda = page.getByLabel("Buscar tutores");
    this.botonBuscar = page.getByRole("button", { name: "Buscar", exact: true });
  }

  async goto(): Promise<void> {
    await super.goto("/buscar");
  }

  async buscar(texto: string): Promise<void> {
    await this.inputBusqueda.fill(texto);
    await this.botonBuscar.click();
  }

  tarjetaTutor(nombre: string): Locator {
    return this.page.getByRole("heading", { name: nombre });
  }
}
