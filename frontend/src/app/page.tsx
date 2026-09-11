import Link from "next/link";

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-8">
      <div className="w-full max-w-[32rem] rounded-tarjeta border border-borde bg-superficie p-8 shadow-tarjeta">
        <div className="mb-6 text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>
        <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Tutorías en línea</h1>
        <p className="mb-6 text-texto-suave">
          Clases particulares con tutores verificados para estudiantes y
          menores, en un entorno seguro y supervisado.
        </p>
        <div className="flex flex-wrap gap-3">
          <Link className="rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover" href="/registro">
            Crear mi cuenta
          </Link>
          <Link className="rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50" href="/login">
            Iniciar sesión
          </Link>
        </div>
      </div>
    </main>
  );
}