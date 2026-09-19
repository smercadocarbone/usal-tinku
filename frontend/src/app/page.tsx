import Link from "next/link";
import {
  Atom,
  BookOpen,
  Building2,
  Calculator,
  FlaskConical,
  GraduationCap,
  HeartHandshake,
  Languages,
  School,
  Users,
  type LucideIcon,
} from "lucide-react";
import TarjetasSeguridad from "@/components/TarjetasSeguridad";

const MATERIAS: { nombre: string; descripcion: string; icono: LucideIcon }[] = [
  {
    nombre: "Matemática",
    descripcion: "Operaciones, álgebra, funciones y todo el programa escolar.",
    icono: Calculator,
  },
  {
    nombre: "Física",
    descripcion: "Cinemática, fuerzas, energía y problemas guiados paso a paso.",
    icono: Atom,
  },
  {
    nombre: "Química",
    descripcion: "Estequiometría, reacciones y preparación de parciales.",
    icono: FlaskConical,
  },
  {
    nombre: "Inglés",
    descripcion: "Clases de conversación, gramática y apoyo con tareas.",
    icono: Languages,
  },
  {
    nombre: "Lengua",
    descripcion: "Lectura, escritura, análisis de texto y redacción.",
    icono: BookOpen,
  },
];

export default function HomePage() {
  return (
    <main>
      {/* ============ HERO ============ */}
      <section className="relative overflow-hidden">
        <div
          aria-hidden
          className="pointer-events-none absolute -top-32 left-1/2 -z-10 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full bg-teal-700 opacity-20 blur-3xl"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-24 -right-24 -z-10 h-80 w-80 rounded-full bg-teal-700 opacity-10 blur-3xl"
        />

        <div className="mx-auto grid min-h-[80vh] max-w-6xl grid-cols-1 items-center gap-14 px-5 py-16 lg:grid-cols-2 lg:gap-12">
          <div>
            <p
              className="mb-5 inline-flex items-center gap-2 animate-fade-in-up motion-reduce:animate-none rounded-full border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-slate-500"
              style={{ animationDelay: "0ms" }}
            >
              <span className="h-2 w-2 rounded-full bg-teal-600" />
              Tutorías en línea para toda Argentina
            </p>

            <h1
              className="animate-fade-in-up motion-reduce:animate-none text-5xl font-extrabold tracking-tight text-slate-800 md:text-7xl"
              style={{ animationDelay: "100ms" }}
            >
              El apoyo escolar que necesitás,{" "}
              <span className="text-teal-700">sin barreras geográficas</span>
            </h1>

            <p
              className="animate-fade-in-up motion-reduce:animate-none mt-6 max-w-xl text-lg text-slate-500"
              style={{ animationDelay: "200ms" }}
            >
              Conectamos alumnos con Tutores verificados para clases en un aula
              virtual segura: se paga al terminar, la clase queda resumida por
              IA y las identidades están validadas desde el primer día.
            </p>

            <div
              className="animate-fade-in-up motion-reduce:animate-none mt-9 flex flex-col gap-4 sm:flex-row"
              style={{ animationDelay: "300ms" }}
            >
              <Link
                href="/buscar"
                className="rounded-xl bg-teal-700 px-6 py-3 text-center text-base font-semibold text-white no-underline transition-all duration-300 hover:scale-105 hover:bg-teal-800 hover:shadow-[0_0_20px_rgba(13,148,136,0.4)] active:scale-95"
              >
                Encontrar un Tutor
              </Link>
              <Link
                href="/registro/tutor"
                className="rounded-xl border border-slate-200 bg-white px-6 py-3 text-center text-base font-semibold text-teal-700 transition-all duration-300 hover:scale-105 hover:border-teal-700 hover:bg-teal-50 active:scale-95"
              >
                Quiero Enseñar
              </Link>
            </div>
          </div>

          {/* Mockup del aula virtual */}
          <div className="relative mx-auto w-full max-w-md">
            <div className="animate-float rounded-2xl border border-slate-200 bg-white p-4 shadow-sm motion-reduce:animate-none">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-teal-600/10 font-bold text-teal-700">
                    M
                  </div>
                  <div>
                    <p className="text-sm font-bold text-slate-800">
                      Martín · Tutor
                    </p>
                    <p className="text-xs text-slate-500">
                      Matemática · 3er año
                    </p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1.5 rounded-full bg-teal-700/10 px-2.5 py-1 text-xs font-semibold text-teal-700">
                  <span className="h-1.5 w-1.5 rounded-full bg-teal-700" />
                  En vivo
                </span>
              </div>

              <div className="relative flex aspect-video items-center justify-center rounded-lg border border-slate-200 bg-slate-100">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-teal-600/10 text-2xl font-bold text-teal-700">
                  S
                </div>
                <span className="absolute bottom-2 left-2 rounded-md bg-black/60 px-2 py-1 text-xs text-white">
                  Sofía · Estudiante
                </span>
              </div>

              <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                <span className="font-semibold text-slate-800">Resumen IA:</span>{" "}
                “División de polinomios” explicado con 3 ejercicios.
              </div>
            </div>
            <div
              aria-hidden
              className="absolute -bottom-4 -left-4 -z-10 h-28 w-28 rounded-2xl bg-teal-700 opacity-10 blur-2xl"
            />
          </div>
        </div>
      </section>

      {/* ============ ESCAPARATE: TOP MATERIAS ============ */}
      <section className="bg-slate-50 py-20">
        <div className="mx-auto max-w-6xl px-5">
          <h2 className="text-3xl font-extrabold tracking-tight text-slate-800 md:text-4xl">
            Las materias más pedidas
          </h2>
          <p className="mt-3 max-w-2xl text-lg text-slate-500">
            Explorá la oferta real de tutores y reservá la próxima clase sin
            tener que registrarte primero.
          </p>

          <div className="mt-10 flex snap-x snap-mandatory gap-6 overflow-x-auto pb-4">
            {MATERIAS.map((m) => (
              <Link
                key={m.nombre}
                href="/buscar"
                className="group w-72 shrink-0 snap-center rounded-2xl border border-slate-200 bg-white p-6 shadow-sm transition-all duration-300 hover:-translate-y-2 hover:shadow-xl"
              >
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-teal-600/10 text-teal-700">
                  <m.icono className="h-6 w-6" />
                </div>
                <h3 className="text-lg font-bold text-slate-800">{m.nombre}</h3>
                <p className="mt-1 text-sm text-slate-500">
                  {m.descripcion}
                </p>
                <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-teal-700">
                  Buscar tutores <span aria-hidden>→</span>
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ============ POR QUÉ CONFIAR ============ */}
      <section className="mx-auto max-w-6xl px-5 py-20">
        <h2 className="text-3xl font-extrabold tracking-tight text-slate-800 md:text-4xl">
          Pensado para que las familias confíen
        </h2>
        <p className="mt-3 max-w-2xl text-lg text-slate-500">
          Antes de poner tu tarjeta o dejar a tu hijo en una videollamada,
          estas son las garantías que ya vienen incluidas.
        </p>

        <TarjetasSeguridad />
      </section>

      {/* ============ PARTNERS: COMUNIDAD EDUCATIVA ============ */}
      <section className="border-y border-slate-200 bg-white py-16">
        <div className="mx-auto max-w-6xl px-5">
          <p className="text-center text-sm font-semibold uppercase tracking-widest text-slate-500">
            En alianza con la comunidad educativa de toda Argentina
          </p>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
            {[
              { icono: School, nombre: "Colegios y escuelas" },
              { icono: GraduationCap, nombre: "Universidades y CBC" },
              { icono: Building2, nombre: "Instituciones públicas" },
              { icono: Users, nombre: "Centros de estudiantes" },
              { icono: HeartHandshake, nombre: "ONGs educativas" },
              { icono: Languages, nombre: "Comunidades de idiomas" },
            ].map((p) => (
              <span
                key={p.nombre}
                className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-4 py-2 text-sm font-semibold text-slate-500"
              >
                <p.icono className="h-4 w-4 text-teal-700" />
                {p.nombre}
              </span>
            ))}
          </div>
        </div>
      </section>

      {/* ============ FOOTER ============ */}
      <footer className="bg-slate-50">
        <div className="mx-auto flex max-w-6xl flex-col gap-6 px-5 py-12 md:flex-row md:items-start md:justify-between">
          <div>
            <p className="text-xl font-extrabold text-slate-800">
              Tinku<span className="text-teal-700">.</span>
            </p>
            <p className="mt-2 max-w-sm text-sm text-slate-500">
              Tutorías en línea con Tutores verificados, pagos protegidos y
              aulas seguras para menores.
            </p>
          </div>
          <nav className="flex flex-col gap-2 text-sm">
            <Link href="/buscar" className="font-semibold text-slate-800 transition-colors hover:text-teal-700">
              Encontrar un Tutor
            </Link>
            <Link href="/registro" className="font-semibold text-slate-800 transition-colors hover:text-teal-700">
              Crear mi cuenta
            </Link>
            <Link href="/registro/tutor" className="font-semibold text-slate-800 transition-colors hover:text-teal-700">
              Quiero enseñar
            </Link>
          </nav>
        </div>
        <p className="border-t border-slate-200 py-5 text-center text-xs text-slate-500">
          © 2026 Tinku · Tutorías en línea para toda Argentina
        </p>
      </footer>
    </main>
  );
}