import Diagnostico from "./Diagnostico";

/**
 * Diagnóstico de latencia del clasificador del kill-switch (M3C-07, T-M3-06g). Pública, sin login,
 * fuera del sitemap y sin indexar. Usa solo la cámara propia y no sube ni guarda nada.
 */
export const metadata = { title: "Diagnóstico del clasificador", robots: { index: false, follow: false } };

export default function DiagnosticoClasificadorPage() {
  return <Diagnostico />;
}
