import { notFound } from "next/navigation";
import Galeria from "./Galeria";

export const metadata = { title: "Componentes", robots: { index: false } };

/** Catálogo del sistema visual (UX-01 §3). Solo existe fuera de producción. */
export default function ComponentesPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <Galeria />;
}
