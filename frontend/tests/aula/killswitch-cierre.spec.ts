import { test, expect } from "@playwright/test";
import { resolverCierre } from "../../src/lib/killswitch/cierre";

/**
 * R-C C-1 (H-AULA-1): cuando la sala se desconecta, la pantalla sale del estado de la sesión en
 * el backend, no de un aviso entre pares. Antes un corte por seguridad terminaba en
 * "Esperando al otro participante".
 */
test.describe("Cierre del aula — regla", () => {
  test("corte por seguridad manda sobre todo", { tag: ["@aula", "@KS-052"] }, () => {
    expect(resolverCierre({ cortePorSeguridad: true, sesionCerrada: true })).toBe("seguridad");
    expect(resolverCierre({ cortePorSeguridad: true, sesionCerrada: false })).toBe("seguridad");
  });

  test("sesión cerrada sin corte: clase finalizada", { tag: ["@aula", "@KS-053"] }, () => {
    expect(resolverCierre({ cortePorSeguridad: false, sesionCerrada: true })).toBe("finalizada");
  });

  test("sesión abierta: el otro se fue y puede volver", { tag: ["@aula", "@KS-054"] }, () => {
    expect(resolverCierre({ cortePorSeguridad: false, sesionCerrada: false })).toBe("esperando");
    expect(resolverCierre(null)).toBe("esperando");
  });
});
