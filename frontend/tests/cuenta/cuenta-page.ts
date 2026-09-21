import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class CuentaPage extends BasePage {
  readonly selectMenorBaja: Locator;
  readonly botonDarDeBaja: Locator;

  readonly campoEmail: Locator;
  readonly botonGuardarEmail: Locator;
  readonly campoPasswordActual: Locator;
  readonly campoPasswordNueva: Locator;
  readonly botonCambiarPassword: Locator;

  readonly selectTipoCredencial: Locator;
  readonly campoArchivoCredencial: Locator;
  readonly botonCargarCredencial: Locator;

  constructor(page: Page) {
    super(page);
    this.selectMenorBaja = page.getByLabel("Menor", { exact: true });
    this.botonDarDeBaja = page.getByRole("button", { name: "Dar de baja", exact: true });

    this.campoEmail = page.getByLabel("Email", { exact: true });
    this.botonGuardarEmail = page.getByRole("button", { name: "Guardar email" });
    this.campoPasswordActual = page.getByLabel("Contraseña actual", { exact: true });
    this.campoPasswordNueva = page.getByLabel("Contraseña nueva", { exact: true });
    this.botonCambiarPassword = page.getByRole("button", { name: "Cambiar contraseña" });

    this.selectTipoCredencial = page.getByLabel("Tipo de documento", { exact: true });
    this.campoArchivoCredencial = page.getByLabel("Archivo", { exact: true });
    this.botonCargarCredencial = page.getByRole("button", { name: "Cargar credencial" });
  }

  /** Perfil (raíz del shell): info de cuenta, capacidades y credencial del Tutor. */
  async goto(): Promise<void> {
    await super.goto("/cuenta");
  }

  /** Acceso: email y contraseña. */
  async gotoAcceso(): Promise<void> {
    await super.goto("/cuenta/acceso");
  }

  /** Menores a cargo del Adulto Responsable: alta, solicitudes, baja. */
  async gotoMenores(): Promise<void> {
    await super.goto("/cuenta/menores");
  }
}
