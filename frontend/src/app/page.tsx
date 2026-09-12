import Link from "next/link";
import {
  Atom,
  BookOpen,
  Calculator,
  FlaskConical,
  Languages,
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
          className="pointer-events-none absolute -top-32 left-1/2 -z-10 h-[28rem] w-[28rem] -translate-x-1/2 rounded-full bg-accent opacity-20 blur-3xl"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-24 -right-24 -z-10 h-80 w-80 rounded-full bg-accent opacity-10 blur-3xl"
        />

        <div className="mx-auto grid min-h-[80vh] max-w-6xl grid-cols-1 items-center gap-14 px-5 py-16 lg:grid-cols-2 lg:gap-12">
          <div>
            <p
              className="mb-5 inline-flex items-center gap-2 animate-fade-in-up rounded-full border border-borde bg-superficie px-3.5 py-1.5 text-[0.8rem] font-semibold text-texto-suave"
              style={{ animationDelay: "0ms" }}
            >
              <span className="h-2 w-2 rounded-full bg-accent" />
              Tutorías en línea para toda Argentina
            </p>

            <h1
              className="animate-fade-in-up text-5xl font-extrabold tracking-tight text-texto md:text-7xl"
              style={{ animationDelay: "100ms" }}
            >
              El apoyo escolar que necesitás,{" "}
              <span className="text-accent">sin barreras geográficas</span>
            </h1>

            <p
              className="animate-fade-in-up mt-6 max-w-xl text-lg text-texto-suave"
              style={{ animationDelay: "200ms" }}
            >
              Conectamos alumnos con Tutores verificados para clases en un aula
              virtual segura: se paga al terminar, la clase queda resumida por
              IA y las identidades están validadas desde el primer día.
            </p>

            <div
              className="animate-fade-in-up mt-9 flex flex-col gap-4 sm:flex-row"
              style={{ animationDelay: "300ms" }}
            >
              <Link
                href="/buscar"
                className="rounded-xl bg-accent px-6 py-3 text-center text-base font-semibold text-white transition-all duration-300 hover:scale-105 hover:bg-accent-hover hover:shadow-[0_0_20px_rgba(13,148,136,0.4)] active:scale-95"
              >
                Encontrar un Tutor
              </Link>
              <Link
                href="/registro/tutor"
                className="rounded-xl border border-borde bg-superficie px-6 py-3 text-center text-base font-semibold text-accent transition-all duration-300 hover:scale-105 hover:border-accent hover:bg-teal-50 active:scale-95"
              >
                Quiero Enseñar
              </Link>
            </div>
          </div>

          {/* Mockup del aula virtual */}
          <div className="relative mx-auto w-full max-w-md">
            <div className="animate-float rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta motion-reduce:animate-none">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-accent/10 font-bold text-accent">
                    M
                  </div>
                  <div>
                    <p className="text-sm font-bold text-texto">
                      Martín · Tutor
                    </p>
                    <p className="text-xs text-texto-suave">
                      Matemática · 3er año
                    </p>
                  </div>
                </div>
                <span className="inline-flex items-center gap-1.5 rounded-full bg-exito/10 px-2.5 py-1 text-xs font-semibold text-exito">
                  <span className="h-1.5 w-1.5 rounded-full bg-exito" />
                  En vivo
                </span>
              </div>

              <div className="relative flex aspect-video items-center justify-center rounded-lg border border-borde bg-slate-100">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent/10 text-2xl font-bold text-accent">
                  S
                </div>
                <span className="absolute bottom-2 left-2 rounded-md bg-black/60 px-2 py-1 text-xs text-white">
                  Sofía · Estudiante
                </span>
              </div>

              <div className="mt-3 rounded-lg border border-borde bg-fondo px-3 py-2 text-xs text-texto-suave">
                <span className="font-semibold text-texto">Resumen IA:</span>{" "}
                “División de polinomios” explicado con 3 ejercicios.
              </div>
            </div>
            <div
              aria-hidden
              className="absolute -bottom-4 -left-4 -z-10 h-28 w-28 rounded-tarjeta bg-accent opacity-10 blur-2xl"
            />
          </div>
        </div>
      </section>

      {/* ============ ESCAPARATE: TOP MATERIAS ============ */}
      <section className="bg-slate-50 py-20">
        <div className="mx-auto max-w-6xl px-5">
          <h2 className="text-3xl font-extrabold tracking-tight text-texto md:text-4xl">
            Las materias más pedidas
          </h2>
          <p className="mt-3 max-w-2xl text-lg text-texto-suave">
            Explorá la oferta real de tutores y reservá la próxima clase sin
            tener que registrarte primero.
          </p>

          <div className="mt-10 flex snap-x snap-mandatory gap-6 overflow-x-auto pb-4">
            {MATERIAS.map((m) => (
              <Link
                key={m.nombre}
                href="/buscar"
                className="group w-72 shrink-0 snap-center rounded-tarjeta border border-borde bg-superficie p-6 shadow-tarjeta transition-all duration-300 hover:-translate-y-2 hover:shadow-xl"
              >
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-accent/10 text-accent">
                  <m.icono className="h-6 w-6" />
                </div>
                <h3 className="text-lg font-bold text-texto">{m.nombre}</h3>
                <p className="mt-1 text-sm text-texto-suave">
                  {m.descripcion}
                </p>
                <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-accent">
                  Buscar tutores <span aria-hidden>→</span>
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* ============ POR QUÉ CONFIAR ============ */}
      <section className="mx-auto max-w-6xl px-5 py-20">
        <h2 className="text-3xl font-extrabold tracking-tight text-texto md:text-4xl">
          Pensado para que las familias confíen
        </h2>
        <p className="mt-3 max-w-2xl text-lg text-texto-suave">
          Antes de poner tu tarjeta o dejar a tu hijo en una videollamada,
          estas son las garantías que ya vienen incluidas.
        </p>

        <TarjetasSeguridad />
      </section>
    </main>
  );
}