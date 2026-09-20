import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class SeguridadPage extends BasePage {
  readonly tituloDenuncias: Locator;
  readonly tituloAlertas: Locator;
  readonly campoDescargo: Locator;
  readonly botonEnviarDescargo: Locator;

  constructor(page: Page) {
    super(page);
    this.tituloDenuncias = page.getByRole("heading", { name: "Denuncias recibidas" });
    this.tituloAlertas = page.getByRole("heading", { name: "Alertas de seguridad automáticas" });
    this.campoDescargo = page.getByLabel("Dar mi versión de los hechos (opcional, máx. 300 caracteres)");
    this.botonEnviarDescargo = page.getByRole("button", { name: "Enviar mi descargo" });
  }

  async goto(): Promise<void> {
    await super.goto("/cuenta/seguridad");
  }
}
