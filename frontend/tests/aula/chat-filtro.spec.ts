import { test, expect } from "@playwright/test";
import { filtrarMensaje, REEMPLAZO_CONTACTO, REEMPLAZO_INSULTO } from "../../src/lib/chat/filtro";

/**
 * Filtro del canal de texto del aula (src/lib/chat/filtro.ts): datos de contacto e insultos.
 * Lógica pura, corre en Node.
 */

const texto = (t: string) => filtrarMensaje(t).texto;

test.describe("Chat del aula — datos de contacto", () => {
  for (const [caso, entrada] of [
    ["teléfono con separadores", "escribime al 11 5555-1234"],
    ["teléfono internacional", "mi cel es +54 9 11 5555 1234"],
    ["teléfono en palabras", "once cinco cinco cinco cinco uno dos tres cuatro"],
    ["teléfono mezclado", "once 5555 doce 34"],
    ["email", "juan.perez@gmail.com"],
    ["email en palabras", "juan arroba gmail punto com"],
    ["email con (at)", "juan (at) gmail.com"],
    ["usuario de red con conector", "mi insta es juan_perez"],
    ["usuario de red con forma de usuario", "agregame al face juan.perez"],
    ["mención", "seguime @juanperez"],
    ["enlace de WhatsApp", "https://wa.me/5491155551234"],
    ["enlace web", "www.miweb.com.ar"],
  ] as const) {
    test(`oculta ${caso}`, { tag: ["@aula", "@CHAT-001"] }, () => {
      const r = filtrarMensaje(entrada);
      expect(r.texto).toContain(REEMPLAZO_CONTACTO);
      expect(r.contactoOculto).toBe(true);
    });
  }

  for (const entrada of [
    "te escribo por whatsapp mañana",
    "el dato punto final",
    "x favor mandame el ejercicio 3",
    "el 27/10/2026 a las 15",
    "son $ 15.000 la hora",
    "tengo una duda con el ejercicio dos",
    "la integral de 1 a 1000 da 999",
  ]) {
    test(`no toca "${entrada}"`, { tag: ["@aula", "@CHAT-002"] }, () => {
      expect(filtrarMensaje(entrada)).toEqual({ texto: entrada, contactoOculto: false, insultoOculto: false });
    });
  }
});

test.describe("Chat del aula — insultos", () => {
  test("reemplaza la palabra, no el mensaje", { tag: ["@aula", "@CHAT-003"] }, () => {
    const r = filtrarMensaje("sos un boludo, no entendés");
    expect(r.texto).toBe(`sos un ${REEMPLAZO_INSULTO}, no entendés`);
    expect(r.insultoOculto).toBe(true);
    expect(r.contactoOculto).toBe(false);
  });

  test("normaliza tildes, números y letras estiradas", { tag: ["@aula", "@CHAT-004"] }, () => {
    expect(texto("p3l0tud0")).toBe(REEMPLAZO_INSULTO);
    expect(texto("qué forrooo")).toBe(`qué ${REEMPLAZO_INSULTO}`);
    expect(texto("BOLUUUDO")).toBe(REEMPLAZO_INSULTO);
    expect(texto("imbécil")).toBe(REEMPLAZO_INSULTO);
  });

  test("no toca palabras que contienen un insulto", { tag: ["@aula", "@CHAT-005"] }, () => {
    for (const entrada of ["la computadora anda mal", "el foro del curso", "disputa territorial", "Gilberto"]) {
      expect(texto(entrada)).toBe(entrada);
    }
  });

  test("contacto e insulto en el mismo mensaje", { tag: ["@aula", "@CHAT-006"] }, () => {
    const r = filtrarMensaje("boludo llamame al 1155551234");
    expect(r.texto).toBe(`${REEMPLAZO_INSULTO} llamame al ${REEMPLAZO_CONTACTO}`);
    expect(r).toMatchObject({ contactoOculto: true, insultoOculto: true });
  });
});
