/**
 * Política de contraseña (FASE2-02 / AUD-012): la misma que valida el backend
 * (`@PasswordSegura` + `PoliticaPassword`). La UI la muestra en vivo; el backend
 * igual la vuelve a validar.
 */
export const LARGO_MINIMO_PASSWORD = 10;

export interface RequisitoPassword {
  texto: string;
  cumple: boolean;
}

export function requisitosPassword(password: string, dni?: string | null): RequisitoPassword[] {
  const lista: RequisitoPassword[] = [
    { texto: `Al menos ${LARGO_MINIMO_PASSWORD} caracteres`, cumple: password.length >= LARGO_MINIMO_PASSWORD },
    { texto: "Letras y números", cumple: /\p{L}/u.test(password) && /\d/.test(password) },
  ];
  const digitos = (dni ?? "").replace(/\D/g, "");
  if (digitos) {
    lista.push({ texto: "Que no contenga tu DNI", cumple: password.length > 0 && !password.includes(digitos) });
  }
  return lista;
}

export function passwordValida(password: string, dni?: string | null): boolean {
  return requisitosPassword(password, dni).every((r) => r.cumple);
}
