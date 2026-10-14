import { notFound } from "next/navigation";
import Cliente from "./Cliente";

export const metadata = { title: "Kill-switch (dev)", robots: { index: false } };

/** Banco de pruebas del clasificador (M3C-03/07). Solo existe fuera de producción. */
export default function KillswitchDevPage() {
  if (process.env.NODE_ENV === "production") notFound();
  return <Cliente />;
}
