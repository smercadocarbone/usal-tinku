import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class RecuperarPasswordPage extends BasePage {
  readonly campoDni: Locator;
  readonly botonEnviar: Locator;

  constructor(page: Page) {
    super(page);
    this.campoDni = page.getByLabel("DNI", { exact: true });
    this.botonEnviar = page.getByRole("button", { name: "Enviar enlace de recuperación" });
  }

  async goto(): Promise<void> {
    await super.goto("/recuperar-password");
  }
}

export class ResetearPasswordPage extends BasePage {
  readonly campoPasswordNueva: Locator;
  readonly campoConfirmacion: Locator;
  readonly botonGuardar: Locator;

  constructor(page: Page) {
    super(page);
    this.campoPasswordNueva = page.getByLabel("Contraseña nueva", { exact: true });
    this.campoConfirmacion = page.getByLabel("Repetí la contraseña", { exact: true });
    this.botonGuardar = page.getByRole("button", { name: "Guardar contraseña nueva" });
  }

  async goto(token?: string): Promise<void> {
    await super.goto(token ? `/resetear-password?token=${token}` : "/resetear-password");
  }
}
