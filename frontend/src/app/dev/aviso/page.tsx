import { notFound } from "next/navigation";
import AvisoProteccion, { type MotivoAvisoProteccion } from "@/components/aula/AvisoProteccion";

export const metadata = { title: "Aviso de protección (dev)", robots: { index: false } };

const MOTIVOS: MotivoAvisoProteccion[] = ["no-disponible", "seguridad", "sin-proteccion"];

/** R-C C-3: muestra la pantalla de corte del aula para revisar su contraste. Solo fuera de producción. */
export default async function AvisoDevPage({ searchParams }: { searchParams: Promise<{ motivo?: string }> }) {
  if (process.env.NODE_ENV === "production") notFound();
  const { motivo } = await searchParams;
  const elegido = MOTIVOS.find((m) => m === motivo) ?? "seguridad";
  return <AvisoProteccion motivo={elegido} />;
}
