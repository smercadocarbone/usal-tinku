import Link from "next/link";
import { Tarjeta, clasesBoton } from "@/components/ui";

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <Tarjeta className="w-full max-w-lg p-8 text-center">
        <div className="mb-6 text-lg font-bold text-slate-800">
          Tinku<span className="text-teal-700">.</span>
        </div>
        <p className="text-3xl font-extrabold tracking-tight text-teal-700">404</p>
        <h1 className="mt-2 text-xl tracking-tight">Esta página no existe</h1>
        <p className="mt-1 text-sm text-slate-500">
          Puede que el enlace esté viejo o que la clase que buscabas ya no esté
          disponible.
        </p>

        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <Link href="/buscar" className={clasesBoton("primario")}>
            Buscar un tutor
          </Link>
          <Link href="/" className={clasesBoton("secundario")}>
            Ir al inicio
          </Link>
        </div>
      </Tarjeta>
    </main>
  );
}
