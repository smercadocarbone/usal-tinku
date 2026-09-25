"use client";

import {
  BadgeCheck,
  Camera,
  CircleDollarSign,
  Clock3,
  GraduationCap,
  Handshake,
  IdCard,
  Lock,
  MicOff,
  ShieldCheck,
  Sparkles,
  Star,
  UsersRound,
  VideoOff,
  Wallet,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/cn";
import { TIEMPOS } from "@/lib/tiempos";

/**
 * Onboarding de Términos y Condiciones (tesis, cap. 4 "Diseño y experiencia de usuario"):
 * en vez de un bloque de texto, los puntos clave —incluido cómo funciona la plataforma—
 * como tarjetas con un título corto y grande, un párrafo breve y una ilustración al costado.
 * Los plazos salen de `TIEMPOS` (copia de la Tabla de Tiempos), nunca escritos a mano.
 */

interface Punto {
  titulo: string;
  texto: string;
  icono: LucideIcon;
  acentos: [LucideIcon, LucideIcon];
  tono: "marca" | "ambar" | "cielo" | "rosa";
}

const COMUNES_INICIO: Punto[] = [
  {
    titulo: "Tinku conecta, no da las clases",
    texto:
      "Somos la plataforma que te une con tutores independientes. Cada tutor define qué enseña, sus horarios y su precio; Tinku verifica identidades, cuida los pagos y la seguridad.",
    icono: Handshake,
    acentos: [UsersRound, GraduationCap],
    tono: "marca",
  },
  {
    titulo: "Todos verificamos quiénes somos",
    texto:
      "Para usar Tinku validamos tu DNI y tenés que ser mayor de 18. Tus datos tienen que ser verdaderos: una cuenta con datos falsos se da de baja.",
    icono: IdCard,
    acentos: [BadgeCheck, ShieldCheck],
    tono: "cielo",
  },
];

const ADULTO: Punto[] = [
  {
    titulo: "Recomendaciones, no garantías",
    texto:
      "Te sugerimos tutores según lo que buscás y según las calificaciones de otras familias y alumnos. Es una ayuda para elegir: la decisión de con quién tomar clase es siempre tuya.",
    icono: Sparkles,
    acentos: [Star, UsersRound],
    tono: "ambar",
  },
  {
    titulo: "Pagás al reservar, el tutor cobra después",
    texto: `El precio que ves es el total. Tenés ${TIEMPOS.pagoMinutos} minutos para pagar; el dinero queda retenido y se le libera al tutor ${TIEMPOS.liberacionHoras} hs después de la clase. Si cancelás con más de ${TIEMPOS.cancelacionSinPenalidadHoras} hs de anticipación, te lo devolvemos entero.`,
    icono: Wallet,
    acentos: [Lock, Clock3],
    tono: "marca",
  },
  {
    titulo: "Los menores, siempre con un adulto",
    texto:
      "Un chico o chica nunca se registra solo: su adulto responsable le crea la cuenta, elige con qué tutores puede tomar clase y paga. Si algo inapropiado aparece en cámara, la clase se corta al instante.",
    icono: ShieldCheck,
    acentos: [UsersRound, VideoOff],
    tono: "rosa",
  },
];

const TUTOR: Punto[] = [
  {
    titulo: "Verificamos tu formación y tu cara",
    texto:
      "Tu título o certificado lo revisa una persona del equipo contra los registros oficiales. La foto de perfil es obligatoria: los alumnos y las familias tienen que ver con quién toman clase.",
    icono: GraduationCap,
    acentos: [BadgeCheck, Camera],
    tono: "cielo",
  },
  {
    titulo: "Cobrás después de cada clase",
    texto: `El alumno paga al reservar y el dinero queda retenido hasta ${TIEMPOS.liberacionHoras} hs después de la clase. Tinku descuenta su comisión y el resto es tuyo. Vos decidís tu precio por hora.`,
    icono: CircleDollarSign,
    acentos: [Wallet, Clock3],
    tono: "marca",
  },
  {
    titulo: "Con menores, reglas más estrictas",
    texto:
      "Para dar clases a menores necesitás el Certificado de Antecedentes Penales aprobado y vigente. En esas clases, si algo inapropiado aparece en cámara, la clase se corta al instante.",
    icono: ShieldCheck,
    acentos: [UsersRound, VideoOff],
    tono: "rosa",
  },
];

const COMUNES_FIN: Punto[] = [
  {
    titulo: "Las clases no se graban",
    texto:
      "Nunca guardamos video. La única excepción es el resumen automático, que se contrata aparte: graba solo el audio, nunca en clases con menores, y lo borramos apenas se transcribe (24 hs como máximo).",
    icono: MicOff,
    acentos: [VideoOff, Lock],
    tono: "ambar",
  },
  {
    titulo: "Tus datos, lo mínimo necesario",
    texto:
      "Guardamos solo lo que hace falta para que la plataforma funcione, según la Ley 25.326 de Protección de Datos Personales. Podés pedir ver, corregir o borrar tus datos cuando quieras.",
    icono: Lock,
    acentos: [ShieldCheck, IdCard],
    tono: "cielo",
  },
];

const TONOS: Record<Punto["tono"], { fondo: string; icono: string; anillo: string }> = {
  marca: { fondo: "bg-marca-50", icono: "text-marca-700", anillo: "ring-marca-100" },
  ambar: { fondo: "bg-amber-50", icono: "text-amber-700", anillo: "ring-amber-100" },
  cielo: { fondo: "bg-sky-50", icono: "text-sky-700", anillo: "ring-sky-100" },
  rosa: { fondo: "bg-rose-50", icono: "text-rose-700", anillo: "ring-rose-100" },
};

/** Ilustración del concepto: el ícono principal grande y dos acentos que lo orbitan. */
function Ilustracion({ punto }: { punto: Punto }) {
  const t = TONOS[punto.tono];
  const [A1, A2] = punto.acentos;
  const I = punto.icono;
  return (
    <div aria-hidden className={cn("relative flex size-32 shrink-0 items-center justify-center rounded-full sm:size-36", t.fondo)}>
      <I className={cn("size-14 sm:size-16", t.icono)} strokeWidth={1.6} />
      <span className={cn("absolute -right-1 top-3 flex size-11 items-center justify-center rounded-full bg-superficie shadow-sm ring-4", t.anillo)}>
        <A1 className={cn("size-5", t.icono)} />
      </span>
      <span className={cn("absolute -left-1 bottom-3 flex size-10 items-center justify-center rounded-full bg-superficie shadow-sm ring-4", t.anillo)}>
        <A2 className={cn("size-5", t.icono)} />
      </span>
    </div>
  );
}

export function puntosClave(tipo: "adulto" | "tutor"): Punto[] {
  return [...COMUNES_INICIO, ...(tipo === "tutor" ? TUTOR : ADULTO), ...COMUNES_FIN];
}

export default function TerminosClave({ tipo }: { tipo: "adulto" | "tutor" }) {
  return (
    <ol className="mt-6 flex list-none flex-col gap-4 p-0" aria-label="Puntos clave de los Términos y Condiciones">
      {puntosClave(tipo).map((p, n) => (
        <li
          key={p.titulo}
          className={cn(
            "flex flex-col items-center gap-5 rounded-tarjeta border border-borde bg-superficie p-5 text-center sm:flex-row sm:p-6 sm:text-left",
            n % 2 === 1 && "sm:flex-row-reverse"
          )}
        >
          <Ilustracion punto={p} />
          <div>
            <h2 className="text-[22px] font-extrabold leading-tight sm:text-[24px]">{p.titulo}</h2>
            <p className="mt-2 text-[15px] leading-relaxed text-tinta-suave">{p.texto}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
