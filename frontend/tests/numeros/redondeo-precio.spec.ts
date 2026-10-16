import { test, expect } from "@playwright/test";
import { precioClase } from "../../src/lib/agenda";

/**
 * El precio que se le muestra a quien reserva tiene que ser el mismo que congela el backend
 * (`ReservaService.precioDe`: precioHora × minutos × (100 − descuento) / 6000, HALF_UP a centavos,
 * en BigDecimal). Con flotantes, una tarifa con centavos daba un centavo menos: 6000,07 $/h en
 * 30 min se mostraba $3000,03 y se cobraban $3000,04.
 */
function esperadoBackend(precioHora: string, minutos: number, descuento: number): number {
  const [entero, dec = ""] = precioHora.split(".");
  const centavos = BigInt(entero) * BigInt(100) + BigInt((dec + "00").slice(0, 2));
  const n = centavos * BigInt(minutos) * BigInt(100 - descuento);
  return Number((n * BigInt(2) + BigInt(6000)) / BigInt(12000)) / 100;
}

test.describe("precio de una clase — mismo redondeo que el backend", () => {
  test("casos con centavos que el flotante redondeaba mal", { tag: ["@numeros", "@NUM-001"] }, () => {
    expect(precioClase(6000.07, 30)).toBe(3000.04);
    expect(precioClase(6000.33, 90)).toBe(9000.5);
    expect(precioClase(1.14, 150, 30)).toBe(2);
  });

  test("barrido: tarifas con centavos, 30–180 min, descuento 0–30 %", { tag: ["@numeros", "@NUM-002"] }, () => {
    const malos: string[] = [];
    for (let c = 614000; c <= 640000; c += 7) {
      const texto = `${Math.floor(c / 100)}.${String(c % 100).padStart(2, "0")}`;
      for (const m of [30, 60, 90, 120, 150, 180]) {
        for (const d of [0, 5, 10, 15, 20, 25, 30]) {
          const esperado = esperadoBackend(texto, m, d);
          const real = precioClase(Number(texto), m, d);
          if (real !== esperado && malos.length < 5) malos.push(`${texto} ${m}min ${d}%: ${real} ≠ ${esperado}`);
        }
      }
    }
    expect(malos).toEqual([]);
  });
});
