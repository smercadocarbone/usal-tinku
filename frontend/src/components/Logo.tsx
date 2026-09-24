import Link from "next/link";
import { cn } from "@/lib/cn";

/** Marca de Tinku: isotipo + nombre. Siempre lleva a `/` (o a donde se indique). */
export default function Logo({ href = "/", className, claro }: { href?: string; className?: string; claro?: boolean }) {
  return (
    <Link
      href={href}
      className={cn("inline-flex min-h-11 items-center gap-2 no-underline", claro ? "text-white" : "text-tinta", className)}
      aria-label="Tinku, ir al inicio"
    >
      <svg viewBox="0 0 64 64" className="size-8" aria-hidden>
        <rect width="64" height="64" rx="16" fill={claro ? "#ffffff" : "#146251"} />
        <path d="M16 20h32v7.5H36.2V48h-8.4V27.5H16z" fill={claro ? "#146251" : "#ffffff"} />
        <circle cx="50" cy="45" r="4.5" fill="#f59e2b" />
      </svg>
      <span className="text-xl font-extrabold tracking-tight">tinku</span>
    </Link>
  );
}
