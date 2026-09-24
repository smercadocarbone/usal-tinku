import Link from "next/link";
import {
  Atom,
  BadgeCheck,
  BookOpen,
  Calculator,
  CalendarCheck,
  ChevronRight,
  Code2,
  FlaskConical,
  Globe2,
  Languages,
  Search,
  Sigma,
  Video,
  WalletCards,
  type LucideIcon,
} from "lucide-react";
import Cabecera from "@/components/Cabecera";
import Logo from "@/components/Logo";
import TarjetasSeguridad from "@/components/TarjetasSeguridad";
import BuscadorHero from "@/components/landing/BuscadorHero";
import { Acordeon, Avatar, Insignia, clasesBoton } from "@/components/ui";
import { TIEMPOS } from "@/lib/tiempos";

const MATERIAS: { nombre: string; icono: LucideIcon }[] = [
  { nombre: "Matemática", icono: Calculator },
  { nombre: "Física", icono: Atom },
  { nombre: "Química", icono: FlaskConical },
  { nombre: "Inglés", icono: Languages },
  { nombre: "Lengua", icono: BookOpen },
  { nombre: "Historia", icono: Globe2 },
  { nombre: "Análisis Matemático", icono: Sigma },
  { nombre: "Programación", icono: Code2 },
];

const PASOS: { icono: LucideIcon; titulo: string; texto: string }[] = [
  {
    icono: Search,
    titulo: "Buscá",
    texto: "Contanos qué necesitás aprender y te mostramos tutores verificados que lo enseñan.",
  },
  {
    icono: CalendarCheck,
    titulo: "Reservá y pagá",
    texto: `Elegí el horario y pagá con MercadoPago. El dinero queda retenido hasta ${TIEMPOS.liberacionHoras} hs después de la clase.`,
  },
  {
    icono: Video,
    titulo: "Tomá la clase",
    texto: "Entrás al aula de Tinku desde el navegador, sin instalar nada. Al terminar, calificás al tutor.",
  },
];

const PREGUNTAS = [
  {
    pregunta: "¿Cómo se paga?",
    respuesta: `Con MercadoPago, al reservar. Tenés ${TIEMPOS.pagoMinutos} minutos para completar el pago; si no, el horario se libera. El dinero queda retenido y se le libera al tutor ${TIEMPOS.liberacionHoras} hs después de la clase.`,
  },
  {
    pregunta: "¿Qué pasa si la clase no se da?",
    respuesta: `Si cancelás con más de ${TIEMPOS.cancelacionSinPenalidadHoras} hs de anticipación, te devolvemos el total. Si el tutor no se conecta, también te devolvemos todo.`,
  },
  {
    pregunta: "¿Desde qué edad se puede usar?",
    respuesta: `Desde los ${TIEMPOS.edadMinimaMenor} años, siempre con un adulto responsable que crea la cuenta del chico, autoriza a cada tutor y paga. Durante el piloto, las clases son solo para mayores de 18.`,
  },
  {
    pregunta: "¿Cómo se verifica a un tutor?",
    respuesta:
      "Valida su identidad con el DNI al registrarse, y nuestro equipo revisa a mano su título o certificado antes de que pueda aparecer en las búsquedas.",
  },
];

export default function HomePage() {
  return (
    <>
      <Cabecera variante="publica" />
      <main>
        {/* ============ HERO ============ */}
        <section className="relative overflow-hidden border-b border-borde bg-[linear-gradient(180deg,#ffffff_0%,#f6f8f7_100%)]">
          <div className="mx-auto grid max-w-[1120px] grid-cols-1 items-center gap-12 px-4 pb-16 pt-10 sm:px-6 lg:grid-cols-[1.15fr_1fr] lg:gap-16 lg:pb-24 lg:pt-20">
            <div className="motion-safe:animate-aparecer">
              <Insignia tono="acento" icono={<BadgeCheck />} className="mb-6">
                Tutores con identidad y título verificados
              </Insignia>
              <h1 className="text-[40px] font-extrabold leading-[1.05] sm:text-[56px]">
                Clases particulares online, con tutores en los que podés confiar.
              </h1>
              <p className="mt-5 max-w-lg text-lg leading-relaxed text-tinta-suave">
                Escolar, secundario y universitario. Pagás al reservar y el tutor cobra recién después de la clase.
              </p>
              <div className="mt-8">
                <BuscadorHero />
              </div>
              <Link
                href="/registro/tutor"
                className="mt-6 inline-flex min-h-11 items-center gap-1 text-[15px] font-semibold text-tinta underline decoration-borde-fuerte underline-offset-4 hover:decoration-tinta"
              >
                Quiero dar clases <ChevronRight className="size-4" aria-hidden />
              </Link>
            </div>

            {/* Así se ve un tutor en Tinku: explica qué datos vas a encontrar (sello,
                precio, próximo horario, pago protegido). Es ilustrativo. */}
            <figure className="relative mx-auto w-full max-w-md" aria-labelledby="hero-ejemplo">
              <div aria-hidden className="absolute -inset-6 -z-10 rounded-[40px] bg-marca-100/60 blur-2xl" />
              <div className="rounded-[28px] border border-borde bg-superficie p-5 shadow-flotante">
                <div className="flex items-center gap-4">
                  <Avatar nombre="Valeria" apellido="Gómez" semilla="valeria" tamano="lg" verificado />
                  <div className="min-w-0">
                    <p className="text-lg font-bold">Valeria G.</p>
                    <p className="text-sm text-tinta-suave">Matemática · Física · Secundario</p>
                  </div>
                </div>
                <div className="mt-5 flex flex-wrap gap-2">
                  <Insignia tono="exito" icono={<BadgeCheck />}>Título verificado</Insignia>
                  <Insignia>4,9 ★ · 38 clases</Insignia>
                </div>
                <div className="mt-5 grid grid-cols-3 gap-2" aria-hidden>
                  {["jue 18:00", "jue 19:00", "vie 17:30"].map((h, i) => (
                    <span
                      key={h}
                      className={`rounded-control border px-2 py-2.5 text-center text-sm font-semibold ${i === 0 ? "border-tinta bg-tinta text-white" : "border-borde-fuerte"}`}
                    >
                      {h}
                    </span>
                  ))}
                </div>
                <div className="mt-5 flex items-center justify-between rounded-2xl bg-fondo p-4">
                  <div className="flex items-center gap-3">
                    <WalletCards className="size-5 text-marca-700" aria-hidden />
                    <span className="text-sm font-semibold">Pago protegido hasta después de la clase</span>
                  </div>
                </div>
              </div>
              <figcaption id="hero-ejemplo" className="mt-4 text-center text-sm text-tinta-tenue">
                Así se ve un tutor en Tinku (ejemplo ilustrativo).
              </figcaption>
            </figure>
          </div>
        </section>

        {/* ============ CÓMO FUNCIONA ============ */}
        <section id="como-funciona" className="mx-auto max-w-[1120px] scroll-mt-20 px-4 py-16 sm:px-6 lg:py-24">
          <h2 className="text-[28px] font-extrabold sm:text-[40px]">Cómo funciona</h2>
          <ol className="mt-10 grid list-none grid-cols-1 gap-4 p-0 md:grid-cols-3">
            {PASOS.map((p, i) => (
              <li key={p.titulo} className="rounded-tarjeta border border-borde bg-superficie p-6">
                <div className="flex items-center justify-between">
                  <span aria-hidden className="flex size-12 items-center justify-center rounded-2xl bg-marca-50 text-marca-700">
                    <p.icono className="size-6" />
                  </span>
                  <span className="text-5xl font-extrabold text-superficie-hundida" aria-hidden>
                    {i + 1}
                  </span>
                </div>
                <h3 className="mt-6 text-xl font-bold">
                  <span className="sr-only">Paso {i + 1}: </span>
                  {p.titulo}
                </h3>
                <p className="mt-2 text-[15px] leading-relaxed text-tinta-suave">{p.texto}</p>
              </li>
            ))}
          </ol>
        </section>

        {/* ============ SEGURIDAD PARA FAMILIAS ============ */}
        <section className="bg-marca-950 text-white">
          <div className="mx-auto max-w-[1120px] px-4 py-16 sm:px-6 lg:py-24">
            <h2 className="max-w-2xl text-[28px] font-extrabold text-white sm:text-[40px]">
              Pensado para que las familias confíen
            </h2>
            <p className="mt-4 max-w-2xl text-lg text-white/75">
              Antes de poner tu tarjeta o dejar a tu hijo en una videollamada, estas son las garantías que ya vienen incluidas.
            </p>
            <TarjetasSeguridad />
          </div>
        </section>

        {/* ============ MATERIAS ============ */}
        <section className="mx-auto max-w-[1120px] px-4 py-16 sm:px-6 lg:py-24">
          <h2 className="text-[28px] font-extrabold sm:text-[40px]">Las materias más pedidas</h2>
          <p className="mt-3 max-w-2xl text-lg text-tinta-suave">Elegí una para ver quién la enseña.</p>
          <ul className="mt-10 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-4">
            {MATERIAS.map((m) => (
              <li key={m.nombre}>
                <Link
                  href={`/buscar?materia=${encodeURIComponent(m.nombre)}`}
                  className="group flex min-h-24 flex-col justify-between gap-4 rounded-tarjeta border border-borde bg-superficie p-4 text-tinta no-underline transition-[border-color,box-shadow] hover:border-borde-fuerte hover:shadow-elevado sm:p-5"
                >
                  <m.icono className="size-6 text-marca-700" aria-hidden />
                  <span className="flex items-center justify-between gap-2 text-[15px] font-bold sm:text-base">
                    {m.nombre}
                    <ChevronRight className="size-4 text-tinta-tenue transition-transform group-hover:translate-x-0.5" aria-hidden />
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </section>

        {/* ============ PARA TUTORES ============ */}
        <section className="mx-auto max-w-[1120px] px-4 pb-16 sm:px-6 lg:pb-24">
          <div className="grid gap-8 overflow-hidden rounded-[28px] bg-acento-100 p-8 sm:p-12 lg:grid-cols-[1.3fr_1fr] lg:items-center">
            <div>
              <h2 className="text-[28px] font-extrabold sm:text-[40px]">¿Enseñás? Dá clases en Tinku.</h2>
              <ul className="mt-6 flex list-none flex-col gap-3 p-0 text-[16px] text-tinta">
                {[
                  "Ponés tu precio y tus horarios.",
                  `Cobrás por MercadoPago ${TIEMPOS.liberacionHoras} hs después de cada clase.`,
                  "Las familias llegan sabiendo que estás verificado.",
                ].map((t) => (
                  <li key={t} className="flex gap-3">
                    <BadgeCheck className="mt-0.5 size-5 shrink-0 text-acento-700" aria-hidden />
                    {t}
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex flex-col gap-3 lg:items-end">
              <Link href="/registro/tutor" className={clasesBoton("oscuro", "lg", "w-full lg:w-auto")}>
                Crear mi perfil de tutor
              </Link>
              <p className="text-sm text-tinta-suave">Te pedimos DNI y tu título o certificado.</p>
            </div>
          </div>
        </section>

        {/* ============ PREGUNTAS FRECUENTES ============ */}
        <section className="mx-auto max-w-[720px] px-4 pb-20 sm:px-6 lg:pb-28">
          <h2 className="text-center text-[28px] font-extrabold sm:text-[40px]">Preguntas frecuentes</h2>
          <Acordeon items={PREGUNTAS} className="mt-10" />
        </section>
      </main>

      <footer className="border-t border-borde bg-superficie">
        <div className="mx-auto flex max-w-[1120px] flex-col gap-8 px-4 py-12 sm:px-6 md:flex-row md:items-start md:justify-between">
          <div>
            <Logo />
            <p className="mt-3 max-w-sm text-sm text-tinta-suave">
              Clases particulares online con tutores verificados y pagos protegidos.
            </p>
          </div>
          <nav aria-label="Pie de página" className="flex flex-col gap-1 text-[15px]">
            <Link href="/registro" className="min-h-10 font-semibold text-tinta no-underline hover:underline">
              Crear cuenta
            </Link>
            <Link href="/login" className="min-h-10 font-semibold text-tinta no-underline hover:underline">
              Ingresar
            </Link>
            <Link href="/registro/tutor" className="min-h-10 font-semibold text-tinta no-underline hover:underline">
              Dar clases en Tinku
            </Link>
          </nav>
        </div>
        <p className="border-t border-borde py-5 text-center text-xs text-tinta-tenue">
          © 2026 Tinku · Tutorías online para toda Argentina
        </p>
      </footer>
    </>
  );
}
