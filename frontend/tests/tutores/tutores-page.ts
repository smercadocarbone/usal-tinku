import type { Locator, Page } from "@playwright/test";
import { BasePage } from "../base-page";

export class TutorPerfilPage extends BasePage {
  readonly botonDenunciar: Locator;
  readonly selectMotivo: Locator;
  readonly inputEvidencia: Locator;
  readonly botonEnviarDenuncia: Locator;
  readonly selectMenorAutorizar: Locator;
  readonly botonAutorizar: Locator;

  constructor(page: Page) {
    super(page);
    // "Denunciar" ya no compite con el CTA: vive en el menú "⋯" del perfil (UX-04 §2).
    this.botonDenunciar = page.getByRole("button", { name: "Más opciones de este perfil" });
    this.selectMotivo = page.getByLabel("Motivo");
    this.inputEvidencia = page.getByLabel("Link de evidencia (opcional)");
    this.botonEnviarDenuncia = page.getByRole("button", { name: "Enviar denuncia" });
    this.selectMenorAutorizar = page.getByLabel("Menor", { exact: true });
    this.botonAutorizar = page.getByRole("button", { name: "Autorizar para este menor" });
  }

  async goto(tutorId: string): Promise<void> {
    await super.goto(`/tutores/${tutorId}`);
  }

  async enviarDenuncia(motivo: string): Promise<void> {
    await this.botonDenunciar.click();
    await this.page.getByRole("menuitem", { name: "Reportar este perfil" }).click();
    await this.selectMotivo.selectOption(motivo);
    await this.botonEnviarDenuncia.click();
  }
}
