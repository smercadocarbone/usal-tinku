import { NextRequest } from "next/server";
import { clientes, difundir, MAX_BYTES_PRESENCIA, RE_ID, RE_SALA } from "@/lib/presentacion-sync";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export async function POST(req: NextRequest) {
  const texto = await req.text();
  if (texto.length > MAX_BYTES_PRESENCIA * 2) return new Response(null, { status: 413 });
  let j: { id?: unknown; r?: unknown; presence?: unknown };
  try {
    j = JSON.parse(texto);
  } catch {
    return new Response(null, { status: 400 });
  }
  const id = String(j.id ?? "");
  const sala = String(j.r ?? "");
  if (!RE_ID.test(id) || !RE_SALA.test(sala)) return new Response(null, { status: 400 });
  const c = clientes.get(id);
  if (!c || c.sala !== sala) return new Response(null, { status: 404 });
  if (!j.presence || typeof j.presence !== "object" || Array.isArray(j.presence)) {
    return new Response(null, { status: 400 });
  }
  if (JSON.stringify(j.presence).length > MAX_BYTES_PRESENCIA) return new Response(null, { status: 413 });
  c.presencia = j.presence as Record<string, unknown>;
  difundir(sala);
  return new Response(null, { status: 204 });
}
