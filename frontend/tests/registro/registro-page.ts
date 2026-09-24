import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export interface DatosAdulto {
  nombre: string;
  apellido: string;
  dni: string;
  fechaNacimiento: string;
  email: string;
  password: string;
}

const FOTO_DNI_FAKE = {
  name: "dni-frente.png",
  mimeType: "image/png",
  buffer: Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
    "base64"
  ),
};

export class RegistroPage extends BasePage {
  readonly botonSoyAdulto: Locator;
  readonly botonContinuar: Locator;
  readonly botonVerificar: Locator;
  readonly botonCrearCuenta: Locator;

  constructor(page: Page) {
    super(page);
    this.botonSoyAdulto = page.getByRole("radio", { name: /Voy a tomar clases/ });
    this.botonContinuar = page.getByRole("button", { name: "Continuar", exact: true });
    this.botonVerificar = page.getByRole("button", { name: "Verificar", exact: true });
    this.botonCrearCuenta = page.getByRole("button", { name: "Crear cuenta", exact: true });
  }

  async goto(): Promise<void> {
    await super.goto("/registro");
  }

  async elegirRolAdulto(): Promise<void> {
    await this.botonSoyAdulto.click();
    await this.botonContinuar.click();
  }

  async completarDatosPersonales(datos: DatosAdulto): Promise<void> {
    await this.page.getByLabel("Nombre").fill(datos.nombre);
    await this.page.getByLabel("Apellido").fill(datos.apellido);
    await this.page.getByLabel("DNI").fill(datos.dni);
    await this.page.getByLabel("Fecha de nacimiento").fill(datos.fechaNacimiento);
    await this.page.getByLabel("Email").fill(datos.email || "sin-email@example.com");
    await this.botonContinuar.click();
  }

  async verificarIdentidad(): Promise<void> {
    await this.page.getByLabel("Foto de tu DNI (frente)").setInputFiles(FOTO_DNI_FAKE);
    await this.botonVerificar.click();
  }

  async crearAcceso(password: string): Promise<void> {
    // "Contraseña" es substring de "Repetí la contraseña" — exact evita el
    // choque entre los dos campos.
    await this.page.getByLabel("Contraseña", { exact: true }).fill(password);
    await this.page.getByLabel("Repetí la contraseña").fill(password);
    await this.page.getByLabel("Acepto los Términos y Condiciones").check();
    await this.botonCrearCuenta.click();
  }

  async registrarAdultoCompleto(datos: DatosAdulto): Promise<void> {
    await this.elegirRolAdulto();
    await this.completarDatosPersonales(datos);
    await this.verificarIdentidad();
    await this.crearAcceso(datos.password);
  }
}
