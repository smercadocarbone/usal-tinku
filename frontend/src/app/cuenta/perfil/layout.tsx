import type { ReactNode } from "react";

/** R-G G-3 (T7-5): título de la pestaña ("Mi perfil · Tinku"); la página es de cliente y no puede exportarlo. */
export const metadata = { title: "Mi perfil" };

export default function Layout({ children }: { children: ReactNode }) {
  return children;
}
