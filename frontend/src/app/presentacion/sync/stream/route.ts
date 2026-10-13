import { NextRequest } from "next/server";
import {
  clientes,
  difundir,
  MAX_CLIENTES,
  RE_ID,
  RE_SALA,
} from "@/lib/presentacion-sync";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

export function GET(req: NextRequest) {
  const id = req.nextUrl.searchParams.get("id") ?? "";
  const sala = req.nextUrl.searchParams.get("r") ?? "";
  if (!RE_ID.test(id) || !RE_SALA.test(sala)) return new Response(null, { status: 400 });
  if (clientes.size >= MAX_CLIENTES && !clientes.has(id)) return new Response(null, { status: 503 });

  const enc = new TextEncoder();
  let latido: ReturnType<typeof setInterval> | undefined;
  const limpiar = () => {
    if (latido) clearInterval(latido);
    if (clientes.get(id)?.sala === sala) {
      clientes.delete(id);
      difundir(sala);
    }
  };

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      const enviar = (t: string) => controller.enqueue(enc.encode(t));
      clientes.set(id, { id, sala, presencia: {}, enviar });
      enviar("retry: 1000\n\n");
      difundir(sala);
      latido = setInterval(() => {
        try {
          enviar(": ping\n\n");
        } catch {
          limpiar();
        }
      }, 15000);
      req.signal.addEventListener("abort", () => {
        limpiar();
        try {
          controller.close();
        } catch {
          /* ya cerrado */
        }
      });
    },
    cancel: limpiar,
  });

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  });
}
