import type { ReactNode } from "react";

/** R-G G-3 (T7-5): título de la pestaña ("Ingresar · Tinku"); la página es de cliente y no puede exportarlo. */
export const metadata = { title: "Ingresar" };

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
