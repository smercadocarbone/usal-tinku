import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class CuentaPage extends BasePage {
  readonly selectMenorBaja: Locator;
  readonly botonDarDeBaja: Locator;

  constructor(page: Page) {
    super(page);
    this.selectMenorBaja = page.getByLabel("Menor", { exact: true });
    this.botonDarDeBaja = page.getByRole("button", { name: "Dar de baja", exact: true });
  }

  async goto(): Promise<void> {
    await super.goto("/cuenta");
  }
}
