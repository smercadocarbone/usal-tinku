/**
 * Mismo formato que acepta el backend (FormatoEmail): algo@dominio.ext. El `type="email"` del
 * navegador acepta "ana@gmail" (sin extensión), que nunca recibe un mail.
 */
const FORMATO_EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)*\.[A-Za-z]{2,}$/;

export function esEmailValido(email: string): boolean {
  return FORMATO_EMAIL.test(email.trim());
}

export const MENSAJE_EMAIL_INVALIDO = "Revisá el email: tiene que ser como nombre@gmail.com.";
