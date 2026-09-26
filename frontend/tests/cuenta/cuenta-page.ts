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
    // UX-05 §5: cada hijo es una tarjeta; la baja vive en su menú ⋯ y se confirma en un modal.
    this.selectMenorBaja = page.getByRole("button", { name: /^Opciones de / });
    this.botonDarDeBaja = page.getByRole("menuitem", { name: "Dar de baja" });

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
  /** La credencial del tutor vive en Mi cuenta → Credenciales. */
  async gotoPerfilTutor(): Promise<void> {
    await super.goto("/cuenta/credenciales");
  }

  async gotoMenores(): Promise<void> {
    await super.goto("/cuenta/menores");
  }
}
