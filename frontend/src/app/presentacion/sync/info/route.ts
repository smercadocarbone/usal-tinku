import { URL_SITIO } from "@/lib/sitio";

export const dynamic = "force-dynamic";

/** Dirección pública de la presentación, para el QR del mando. */
export function GET() {
  return Response.json({ url: `${URL_SITIO}/presentacion` });
}
