import { test, expect } from "@playwright/test";
import { aplicar, codificar, decodificar, mensajesDeSync, type Trazo } from "../../src/lib/pizarra";
import { setFakeSession } from "../helpers";

/**
 * FR-AULA-011..013 (ADR-M3-06): protocolo de la pizarra (puro, sin LiveKit) y el componente
 * dibujando de verdad en el navegador (demo de /dev/componentes).
 */
test.describe("Pizarra — protocolo", () => {
  test("un segmento válido se decodifica y se va sumando al mismo trazo", { tag: ["@aula", "@PIZARRA-001"] }, () => {
    const a = decodificar(codificar({ op: "segmento", id: "t1", color: "#111827", grosor: 6, goma: false, puntos: [0.1, 0.1, 0.2, 0.2] }))!;
    const b = decodificar(codificar({ op: "segmento", id: "t1", color: "#111827", grosor: 6, goma: false, puntos: [0.3, 0.3] }))!;
    let trazos: Trazo[] = [];
    trazos = aplicar(trazos, a, false);
    trazos = aplicar(trazos, b, false);
    expect(trazos).toHaveLength(1);
    expect(trazos[0].puntos).toEqual([0.1, 0.1, 0.2, 0.2, 0.3, 0.3]);
    expect(trazos[0].propio).toBe(false);
  });

  test("descarta lo que no es de la pizarra o está mal formado", { tag: ["@aula", "@PIZARRA-002"] }, () => {
    const enc = (o: unknown) => new TextEncoder().encode(JSON.stringify(o));
    expect(decodificar(enc({ tipo: "mensaje", texto: "hola" }))).toBeNull();
    expect(decodificar(new TextEncoder().encode("no es json"))).toBeNull();
    // fuera de 0..1, cantidad impar, color o grosor que no existen, id con caracteres raros
    expect(decodificar(enc({ tipo: "pizarra", op: "segmento", id: "t", color: "#111827", grosor: 6, goma: false, puntos: [2, 0] }))).toBeNull();
    expect(decodificar(enc({ tipo: "pizarra", op: "segmento", id: "t", color: "#111827", grosor: 6, goma: false, puntos: [0.1] }))).toBeNull();
    expect(decodificar(enc({ tipo: "pizarra", op: "segmento", id: "t", color: "#ff00ff", grosor: 6, goma: false, puntos: [] }))).toBeNull();
    expect(decodificar(enc({ tipo: "pizarra", op: "segmento", id: "t", color: "#111827", grosor: 99, goma: false, puntos: [] }))).toBeNull();
    expect(decodificar(enc({ tipo: "pizarra", op: "deshacer", id: "<script>" }))).toBeNull();
    expect(decodificar(enc({ tipo: "pizarra", op: "otra-cosa" }))).toBeNull();
  });

  test("deshacer saca un trazo, borrar deja la pizarra vacía y el sync reconstruye todo", { tag: ["@aula", "@PIZARRA-003"] }, () => {
    const largo = Array.from({ length: 900 }, (_, i) => (i % 100) / 100);
    let trazos: Trazo[] = [];
    trazos = aplicar(trazos, { op: "segmento", id: "a", color: "#dc2626", grosor: 3, goma: false, puntos: largo }, true);
    trazos = aplicar(trazos, { op: "segmento", id: "b", color: "#2563eb", grosor: 12, goma: false, puntos: [0.5, 0.5] }, false);

    // El sync parte los trazos largos en mensajes que entran en el canal y del otro lado queda igual.
    const mensajes = mensajesDeSync(trazos);
    expect(mensajes.length).toBeGreaterThan(2);
    let reconstruido: Trazo[] = [];
    for (const m of mensajes) reconstruido = aplicar(reconstruido, decodificar(codificar(m))!, false);
    expect(reconstruido.map((t) => t.puntos)).toEqual(trazos.map((t) => t.puntos));

    expect(aplicar(trazos, { op: "deshacer", id: "a" }, false).map((t) => t.id)).toEqual(["b"]);
    expect(aplicar(trazos, { op: "borrar" }, false)).toEqual([]);
  });
});

test.describe("Pizarra — dibujar", () => {
  test("dibujar manda segmentos, deshacer borra mi trazo y el tablero queda en blanco", { tag: ["@aula", "@PIZARRA-004"] }, async ({ page, context, baseURL }) => {
    await setFakeSession(context, baseURL!);
    await page.goto("/dev/componentes");
    const tablero = page.getByLabel("Tablero de la pizarra");
    await tablero.scrollIntoViewIfNeeded();
    const caja = (await tablero.boundingBox())!;

    await page.mouse.move(caja.x + 40, caja.y + 40);
    await page.mouse.down();
    await page.mouse.move(caja.x + 160, caja.y + 120, { steps: 10 });
    await page.mouse.up();

    const estado = page.getByTestId("pizarra-estado");
    await expect(estado).toContainText("1 trazos");
    await expect(estado).toContainText("segmento");
    const pintados = () =>
      tablero.evaluate((c: HTMLCanvasElement) => {
        const d = c.getContext("2d")!.getImageData(0, 0, c.width, c.height).data;
        let n = 0;
        for (let i = 3; i < d.length; i += 4) if (d[i] > 0) n++;
        return n;
      });
    await expect.poll(pintados).toBeGreaterThan(50);

    await page.getByRole("button", { name: "Deshacer mi último trazo" }).click();
    await expect(estado).toContainText("0 trazos");
    await expect.poll(pintados).toBe(0);
  });
});
