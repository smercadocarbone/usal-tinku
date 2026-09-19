import type { Page } from "@playwright/test";

/** Clase padre de todos los Page Objects del proyecto. */
export class BasePage {
  constructor(protected page: Page) {}

  async goto(path: string): Promise<void> {
    await this.page.goto(path);
    await this.page.waitForLoadState("networkidle");
  }

  async waitForStatus(): Promise<void> {
    await this.page.waitForSelector('[role="status"]');
  }

  async waitForAlert(): Promise<void> {
    await this.page.waitForSelector('[role="alert"]');
  }
}
