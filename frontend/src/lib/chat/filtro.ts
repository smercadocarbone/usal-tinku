/**
 * Filtro del canal de texto del aula (FR-AULA-002). Los mensajes viajan por el canal de datos de
 * LiveKit, de navegador a navegador, sin pasar por el backend: por eso se filtra en los dos lados.
 * Quien envía manda el texto ya filtrado; quien recibe lo vuelve a filtrar, así un cliente
 * modificado que se saltee el filtro al enviar igual llega tapado a la otra persona.
 *
 * Contacto: mismos patrones que `OcultadorDeContacto` del backend (bio del Tutor, decisión D-7) —
 * las clases se coordinan dentro de Tinku; un contacto directo saltea la protección del menor y el
 * pago — más los disfraces habituales en un chat: números en palabras, "arroba"/"punto",
 * enlaces y menciones de redes. No es infalible: un número dictado por voz o mostrado a la cámara
 * no pasa por acá.
 *
 * Insultos: lista corta del habla rioplatense, comparada palabra por palabra después de normalizar
 * tildes, sustituciones con números (p3l0tud0) y letras repetidas. Se reemplaza la palabra, no se
 * bloquea el mensaje.
 */

export const REEMPLAZO_CONTACTO = "[contacto oculto]";
export const REEMPLAZO_INSULTO = "***";

export interface ResultadoFiltro {
  texto: string;
  contactoOculto: boolean;
  insultoOculto: boolean;
}

const EMAIL = /[\w.+-]+@[\w-]+(\.[\w-]+)+/g;
/** 8 o más dígitos, admitiendo espacios, guiones, puntos, paréntesis y un + inicial entre ellos. */
const TELEFONO = /\+?\(?\d(?:[\s.()-]*\d){7,}/g;
const USUARIO = /(?<![\w@])@[A-Za-z0-9_.]{3,}/g;
/** "juan arroba gmail punto com", "juan (at) gmail.com". "arroba"/"at" van sueltos: no dentro de "dato". */
const EMAIL_EN_PALABRAS =
  /[\p{L}\d._-]+(?:\s*\(\s*(?:arroba|at)\s*\)\s*|\s+(?:arroba|at)\s+)[\p{L}\d-]+\s*(?:(?:\.|\bpunto\b|\bdot\b)\s*\p{L}{2,}\s*)+/giu;
const ENLACE = /\b(?:https?:\/\/|www\.)\S+|\b(?:wa\.me|t\.me|instagram\.com|facebook\.com|fb\.me|tiktok\.com|linkedin\.com)\/?\S*/gi;
/**
 * Un usuario de red social: "mi insta es juan_perez", "wsp: 11…", "agregame al face juan.perez".
 * Nombrar la red sin un usuario ("te escribo por whatsapp") no se tapa: tiene que venir un
 * conector ("es", ":", "=") o algo con forma de usuario (con punto, guion bajo o números).
 */
const REDES = "insta(?:gram)?|face(?:book)?|tiktok|telegram|whats?app|wsp|wpp|wasap|guasap|discord|snap(?:chat)?|linkedin|twitter";
const RED_SOCIAL = new RegExp(
  `\\b(?:${REDES})\\b(?:\\s*(?:es|:|=)\\s*@?[\\p{L}\\d_.]{3,}|\\s+(?:(?:al|a|de|como)\\s+)?@?[\\p{L}\\d]*[_.\\d][\\p{L}\\d_.]*)`,
  "giu",
);

const DIGITO_EN_PALABRAS: Record<string, number> = {
  cero: 1, uno: 1, una: 1, dos: 1, tres: 1, cuatro: 1, cinco: 1, seis: 1, siete: 1, ocho: 1, nueve: 1,
  diez: 2, once: 2, doce: 2, trece: 2, catorce: 2, quince: 2, veinte: 2, treinta: 2, cuarenta: 2,
  cincuenta: 2, sesenta: 2, setenta: 2, ochenta: 2, noventa: 2,
};
const MIN_DIGITOS_TELEFONO = 8;

const INSULTOS = new Set([
  "boludo", "boluda", "boludos", "boludas", "pelotudo", "pelotuda", "pelotudos", "pelotudas",
  "forro", "forra", "forros", "forras", "puto", "puta", "putos", "putas", "puton", "mierda",
  "mierdas", "carajo", "pija", "pijas", "verga", "vergas", "garcha", "chupala", "chupame",
  "conchudo", "conchuda", "conchudos", "conchudas", "culiado", "culiada", "culeado", "culeada",
  "pajero", "pajera", "pajeros", "trolo", "trola", "trolos", "hdp", "hijoputa", "ctm", "lpm",
  "mogolico", "mogolica", "tarado", "tarada", "imbecil", "imbeciles", "idiota", "idiotas",
  "estupido", "estupida", "gil", "giles", "gila", "orto", "ojete", "cagon", "cagona",
]);

const SUSTITUCIONES: Record<string, string> = { "0": "o", "1": "i", "3": "e", "4": "a", "5": "s", "7": "t", "@": "a", $: "s" };

/**
 * Formas de una palabra para compararla con la lista: sin tildes y con las sustituciones
 * deshechas; además, con las letras estiradas recortadas ("boluuudo") y con toda letra repetida
 * reducida ("boluudo"). Se prueban las tres porque la lista tiene dobles legítimas ("forro").
 */
export function formasDePalabra(palabra: string): string[] {
  const sinTildes = palabra.toLowerCase().normalize("NFD").replace(/\p{M}/gu, "");
  const limpia = [...sinTildes].map((c) => SUSTITUCIONES[c] ?? c).join("").replace(/[^a-zñ]/g, "");
  return [limpia, limpia.replace(/(.)\1{2,}/g, "$1"), limpia.replace(/(.)\1+/g, "$1")];
}

export function esInsulto(palabra: string): boolean {
  return formasDePalabra(palabra).some((f) => INSULTOS.has(f));
}

function ocultarTelefonoEnPalabras(texto: string): string {
  // Tramos de palabras numéricas o grupos de dígitos seguidos ("once 5555 doce 34").
  const token = /[\p{L}\d]+/gu;
  const tokens: { inicio: number; fin: number; digitos: number }[] = [];
  for (const m of texto.matchAll(token)) {
    const t = m[0].toLowerCase().normalize("NFD").replace(/\p{M}/gu, "");
    const digitos = /^\d+$/.test(t) ? t.length : (DIGITO_EN_PALABRAS[t] ?? 0);
    tokens.push({ inicio: m.index!, fin: m.index! + m[0].length, digitos });
  }
  const tramos: [number, number][] = [];
  let i = 0;
  while (i < tokens.length) {
    if (!tokens[i].digitos) {
      i++;
      continue;
    }
    let j = i;
    let total = 0;
    let conPalabra = false;
    while (j < tokens.length && tokens[j].digitos) {
      total += tokens[j].digitos;
      conPalabra ||= !/^\d+$/.test(texto.slice(tokens[j].inicio, tokens[j].fin));
      j++;
    }
    // Solo dígitos ya los tapa TELEFONO; acá van los tramos con al menos una palabra.
    if (conPalabra && total >= MIN_DIGITOS_TELEFONO) tramos.push([tokens[i].inicio, tokens[j - 1].fin]);
    i = j;
  }
  for (const [inicio, fin] of tramos.reverse()) {
    texto = texto.slice(0, inicio) + REEMPLAZO_CONTACTO + texto.slice(fin);
  }
  return texto;
}

function ocultarContacto(texto: string): string {
  let r = texto.replace(ENLACE, REEMPLAZO_CONTACTO);
  r = r.replace(EMAIL, REEMPLAZO_CONTACTO);
  r = r.replace(EMAIL_EN_PALABRAS, REEMPLAZO_CONTACTO);
  r = r.replace(RED_SOCIAL, REEMPLAZO_CONTACTO);
  r = r.replace(TELEFONO, REEMPLAZO_CONTACTO);
  r = ocultarTelefonoEnPalabras(r);
  return r.replace(USUARIO, REEMPLAZO_CONTACTO);
}

function ocultarInsultos(texto: string): string {
  // Palabras con las sustituciones habituales (p3l0tud0, put@) incluidas en el token.
  return texto.replace(/[\p{L}\d@$]+/gu, (palabra) => (esInsulto(palabra) ? REEMPLAZO_INSULTO : palabra));
}

export function filtrarMensaje(texto: string): ResultadoFiltro {
  const sinContacto = ocultarContacto(texto);
  const filtrado = ocultarInsultos(sinContacto);
  return {
    texto: filtrado,
    contactoOculto: sinContacto !== texto,
    insultoOculto: filtrado !== sinContacto,
  };
}
