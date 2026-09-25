"use client";

import { useState, useSyncExternalStore, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  BadgeCheck,
  BookOpen,
  CalendarClock,
  Camera,
  Check,
  ChevronLeft,
  CircleDollarSign,
  GraduationCap,
  HeartHandshake,
  Lightbulb,
  Sun,
  UsersRound,
  type LucideIcon,
} from "lucide-react";
import { api, ApiError, subirCredencial, type TipoCredencial } from "@/lib/api";
import { iniciarSesion, siguienteSeguro } from "@/lib/sesion";
import { cn } from "@/lib/cn";
import { Alerta, Boton, Campo, CampoCheckbox, Pasos, RequisitosPassword, Selector, SubidaArchivo, clasesBoton } from "@/components/ui";
import { LARGO_MINIMO_PASSWORD, passwordValida } from "@/lib/password";
import TerminosClave from "@/components/auth/TerminosClave";

type Uso = "clases" | "hijos" | "ambos";
type Tipo = "adulto" | "tutor";

const PASOS: Record<Tipo, string[]> = {
  adulto: ["Para quién", "Tus datos", "Identidad", "Condiciones", "Acceso"],
  tutor: ["Tus datos", "Identidad", "Condiciones", "Acceso", "Qué sigue"],
};

const USOS: { id: Uso; titulo: string; texto: string; icono: LucideIcon }[] = [
  { id: "clases", titulo: "Voy a tomar clases", texto: "Para mí: facultad, idiomas, ingreso…", icono: BookOpen },
  { id: "hijos", titulo: "Tengo un hijo o hija a cargo", texto: "Reservo y pago las clases de los chicos.", icono: UsersRound },
  { id: "ambos", titulo: "Las dos cosas", texto: "Tomo clases y también organizo las de mis hijos.", icono: HeartHandshake },
];

const TIPOS_CREDENCIAL: { value: TipoCredencial; label: string }[] = [
  { value: "TITULO", label: "Título" },
  { value: "CERTIFICADO_ANALITICO", label: "Certificado analítico" },
  { value: "MATRICULA", label: "Matrícula" },
];

function edad(fechaIso: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fechaIso)) return null;
  const [a, m, d] = fechaIso.split("-").map(Number) as [number, number, number];
  const hoy = new Date();
  let e = hoy.getFullYear() - a;
  if (hoy.getMonth() + 1 < m || (hoy.getMonth() + 1 === m && hoy.getDate() < d)) e--;
  return e;
}

function Encabezado({ titulo, texto }: { titulo: string; texto: ReactNode }) {
  return (
    <header className="mt-8">
      <h1 className="text-[28px] font-extrabold sm:text-[32px]">{titulo}</h1>
      <p className="mt-2 text-[16px] leading-relaxed text-tinta-suave">{texto}</p>
    </header>
  );
}

function Navegacion({
  onVolver,
  children,
}: {
  onVolver?: () => void;
  children: ReactNode;
}) {
  return (
    <footer className="mt-8 flex items-center gap-3">
      {onVolver && (
        <Boton variante="secundario" tamano="lg" onClick={onVolver} className="px-4" aria-label="Volver al paso anterior">
          <ChevronLeft className="size-5" aria-hidden />
        </Boton>
      )}
      <div className="flex-1">{children}</div>
    </footer>
  );
}

/**
 * Registro en pasos, compartido por adulto y tutor (UX-03 §3–4): mismas piezas,
 * mismo ritmo. Al terminar, inicia la sesión sola (el usuario ya puso DNI y
 * contraseña) y lo deja en su siguiente paso real.
 */
export default function WizardRegistro({ tipo }: { tipo: Tipo }) {
  const router = useRouter();
  const pasos = PASOS[tipo];
  const [paso, setPaso] = useState(0);

  const queryParams = useSyncExternalStore(
    () => () => {},
    () => window.location.search,
    () => ""
  );
  const siguiente = siguienteSeguro(new URLSearchParams(queryParams).get("siguiente"));

  const [uso, setUso] = useState<Uso | null>(null);
  const [nombre, setNombre] = useState("");
  const [apellido, setApellido] = useState("");
  const [dni, setDni] = useState("");
  const [fechaNacimiento, setFechaNacimiento] = useState("");
  const [email, setEmail] = useState("");
  const [fotoDni, setFotoDni] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [terminos, setTerminos] = useState(false);

  const [trabajando, setTrabajando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [erroresCampos, setErroresCampos] = useState<Record<string, string>>({});
  const [menorDeEdad, setMenorDeEdad] = useState(false);
  const [terminado, setTerminado] = useState(false);
  const [conSesion, setConSesion] = useState(false);

  // Paso de identidad y acceso: sus índices dependen del tipo.
  const iDatos = tipo === "adulto" ? 1 : 0;
  const iIdentidad = iDatos + 1;
  const iCondiciones = iDatos + 2;
  const iAcceso = iDatos + 3;

  const base = tipo === "adulto" ? "/api/usuarios" : "/api/tutores";
  const edadDeclarada = edad(fechaNacimiento);
  const errorEdad =
    edadDeclarada !== null && edadDeclarada < 18
      ? tipo === "tutor"
        ? "Para dar clases en Tinku tenés que ser mayor de 18."
        : "Tenés que ser mayor de 18. Si sos menor, tu adulto responsable te crea la cuenta desde la suya."
      : null;

  function ir(n: number) {
    setError(null);
    setMenorDeEdad(false);
    setPaso(n);
    window.scrollTo({ top: 0 });
  }

  function mapaError(err: unknown) {
    if (err instanceof ApiError) {
      if (err.status === 403) return setMenorDeEdad(true);
      if (err.status === 429) {
        const hs = err.detalles?.espera_restante_hs;
        return setError(
          `Llegaste al máximo de intentos con la foto del DNI. Vas a poder probar de nuevo${hs ? ` en ${hs} hs` : " más tarde"}.`
        );
      }
      const campos = err.detalles?.campos;
      if (campos && typeof campos === "object") setErroresCampos(campos as Record<string, string>);
      return setError(err.message || "No pudimos completar el registro. Probá de nuevo.");
    }
    setError("No pudimos conectarnos. Revisá tu conexión y probá de nuevo.");
  }

  function datosDeclarados() {
    return {
      dniDeclarado: dni,
      nombreDeclarado: nombre.trim(),
      apellidoDeclarado: apellido.trim(),
      fechaNacimientoDeclarada: fechaNacimiento,
    };
  }

  async function verificar() {
    if (!fotoDni) return setError("Subí la foto del frente de tu DNI.");
    setTrabajando(true);
    setError(null);
    setMenorDeEdad(false);
    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datosDeclarados())], { type: "application/json" }));
    form.append("fotoDni", fotoDni);
    try {
      await api.post(`${base}/verificar-dni`, form);
      ir(iCondiciones);
    } catch (err) {
      mapaError(err);
    } finally {
      setTrabajando(false);
    }
  }

  async function crearCuenta(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirmar) return setError("Las contraseñas no coinciden.");
    setTrabajando(true);
    setError(null);
    const datos = {
      ...datosDeclarados(),
      email: email.trim(),
      password,
      ...(tipo === "adulto"
        ? { capacidadEstudiante: uso !== "hijos", capacidadAdultoResponsable: uso !== "clases" }
        : {}),
    };
    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni as File);
    try {
      await api.post(`${base}/registro`, form);
    } catch (err) {
      mapaError(err);
      setTrabajando(false);
      return;
    }
    // La cuenta ya existe: intentamos dejar la sesión abierta. Si el login falla,
    // el usuario ingresa a mano (nunca se pierde la cuenta creada).
    try {
      await iniciarSesion(dni, password);
      setConSesion(true);
      if (tipo === "adulto") {
        if (siguiente) router.replace(siguiente);
        else setTerminado(true);
      } else {
        ir(iAcceso + 1);
      }
    } catch {
      if (tipo === "adulto") router.replace("/login?registrado=1");
      else ir(iAcceso + 1);
    } finally {
      setTrabajando(false);
    }
  }

  if (terminado) return <Bienvenida uso={uso} nombre={nombre} />;

  return (
    <div>
      <Pasos pasos={pasos} actual={paso} />

      {/* ---------------- Adulto · Para quién ---------------- */}
      {tipo === "adulto" && paso === 0 && (
        <section>
          <Encabezado titulo="¿Quién va a usar Tinku?" texto="Así te armamos la cuenta justa. Lo podés cambiar después." />
          <div role="radiogroup" aria-label="Uso de la cuenta" className="mt-6 flex flex-col gap-3">
            {USOS.map((u) => {
              const activo = uso === u.id;
              return (
                <button
                  key={u.id}
                  type="button"
                  role="radio"
                  aria-checked={activo}
                  onClick={() => setUso(u.id)}
                  className={cn(
                    "flex min-h-20 cursor-pointer items-center gap-4 rounded-tarjeta border-2 bg-superficie p-4 text-left transition-[border-color,box-shadow] duration-150",
                    activo ? "border-tinta shadow-elevado" : "border-borde hover:border-borde-fuerte"
                  )}
                >
                  <span
                    aria-hidden
                    className={cn(
                      "flex size-12 shrink-0 items-center justify-center rounded-2xl transition-colors",
                      activo ? "bg-tinta text-white" : "bg-marca-50 text-marca-700"
                    )}
                  >
                    <u.icono className="size-6" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block text-[16px] font-bold text-tinta">{u.titulo}</span>
                    <span className="block text-sm text-tinta-suave">{u.texto}</span>
                  </span>
                  <span
                    aria-hidden
                    className={cn(
                      "flex size-6 shrink-0 items-center justify-center rounded-full border-2",
                      activo ? "border-tinta bg-tinta text-white" : "border-borde-control"
                    )}
                  >
                    {activo && <Check className="size-4" />}
                  </span>
                </button>
              );
            })}
          </div>
          <Navegacion>
            <Boton tamano="lg" anchoCompleto disabled={!uso} onClick={() => ir(1)}>
              Continuar
            </Boton>
          </Navegacion>
          {!uso && <p className="mt-3 text-center text-sm text-tinta-tenue">Elegí una opción para seguir.</p>}
          <p className="mt-8 text-center text-[15px] text-tinta-suave">
            ¿Querés dar clases? <Link href="/registro/tutor" className="font-semibold">Creá tu perfil de tutor</Link>
          </p>
        </section>
      )}

      {/* ---------------- Tus datos ---------------- */}
      {paso === iDatos && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!errorEdad) ir(iIdentidad);
          }}
        >
          <Encabezado titulo="Tus datos" texto="Tal cual figuran en tu DNI: los comparamos con la foto en el paso siguiente." />
          <div className="mt-6 flex flex-col gap-5">
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
              <Campo id="nombre" etiqueta="Nombre" autoComplete="given-name" required value={nombre}
                error={erroresCampos.nombreDeclarado} onChange={(e) => setNombre(e.target.value)} />
              <Campo id="apellido" etiqueta="Apellido" autoComplete="family-name" required value={apellido}
                error={erroresCampos.apellidoDeclarado} onChange={(e) => setApellido(e.target.value)} />
            </div>
            <Campo id="dni" etiqueta="DNI" variante="dni" required value={dni} onValor={setDni}
              placeholder="12.345.678" error={erroresCampos.dniDeclarado} />
            <Campo id="fechaNacimiento" etiqueta="Fecha de nacimiento" type="date" autoComplete="bday" required
              value={fechaNacimiento} onChange={(e) => setFechaNacimiento(e.target.value)}
              error={errorEdad ?? erroresCampos.fechaNacimientoDeclarada} />
            <Campo id="email" etiqueta="Email" type="email" autoComplete="email" required value={email}
              onChange={(e) => setEmail(e.target.value)} error={erroresCampos.email}
              ayuda="Lo usamos solo para cosas de tu cuenta." />
          </div>
          <Navegacion onVolver={tipo === "adulto" ? () => ir(0) : undefined}>
            <Boton type="submit" tamano="lg" anchoCompleto disabled={!!errorEdad}>
              Continuar
            </Boton>
          </Navegacion>
        </form>
      )}

      {/* ---------------- Identidad ---------------- */}
      {paso === iIdentidad && (
        <section>
          <Encabezado
            titulo="Verificá tu identidad"
            texto="Con una foto del frente de tu DNI confirmamos que sos vos y que sos mayor de edad."
          />
          <div className="mt-6 flex flex-col gap-5">
            <ul className="grid list-none grid-cols-3 gap-2 p-0" aria-label="Cómo sacar una buena foto">
              {[
                { i: Sun, t: "Buena luz" },
                { i: Lightbulb, t: "Sin reflejos" },
                { i: BadgeCheck, t: "Frente completo" },
              ].map(({ i: I, t }) => (
                <li key={t} className="flex flex-col items-center gap-2 rounded-2xl bg-superficie-hundida px-2 py-3 text-center text-[13px] font-semibold text-tinta">
                  <I className="size-5 text-marca-700" aria-hidden />
                  {t}
                </li>
              ))}
            </ul>
            <SubidaArchivo
              id="fotoDni"
              etiqueta="Foto de tu DNI (frente o dorso)"
              formatosTexto="JPG o PNG"
              accept="image/jpeg,image/png"
              maxMb={5}
              capturar
              archivo={fotoDni}
              onCambio={setFotoDni}
              ayuda="Si el frente no se lee, probá con el dorso: las 3 líneas con <<< se leen mejor. La usamos solo para verificarte: no se guarda ni se le muestra a nadie."
            />
          </div>
          <Navegacion onVolver={() => ir(iDatos)}>
            <Boton tamano="lg" anchoCompleto disabled={!fotoDni} cargando={trabajando} textoCargando="Verificando tu DNI…" onClick={verificar}>
              Verificar
            </Boton>
          </Navegacion>
        </section>
      )}

      {/* ---------------- Condiciones (puntos clave de los Términos) ---------------- */}
      {paso === iCondiciones && (
        <section>
          <Encabezado
            titulo="Cómo funciona Tinku"
            texto="Antes de crear tu cuenta, lo más importante de los Términos y Condiciones en pocas palabras."
          />
          <TerminosClave tipo={tipo} />
          <div className="mt-6 flex flex-col gap-2">
            <CampoCheckbox id="terminos" etiqueta="Acepto los Términos y Condiciones" checked={terminos} required
              onChange={(e) => setTerminos(e.target.checked)} />
            <p className="text-[13px] leading-relaxed text-tinta-tenue">
              Versión provisoria: al aceptar confirmás que sos mayor de 18 y que tus datos son verdaderos. El texto legal completo se publica antes del lanzamiento.
            </p>
          </div>
          <Navegacion onVolver={() => ir(iIdentidad)}>
            <Boton tamano="lg" anchoCompleto disabled={!terminos} onClick={() => ir(iAcceso)}>
              Continuar
            </Boton>
          </Navegacion>
        </section>
      )}

      {/* ---------------- Acceso ---------------- */}
      {paso === iAcceso && (
        <form onSubmit={crearCuenta}>
          <Encabezado titulo="Creá tu acceso" texto={<>Listo, verificamos tu identidad. Vas a ingresar con tu DNI y esta contraseña.</>} />
          <div className="mt-6 flex flex-col gap-5">
            <Campo id="password" etiqueta="Contraseña" variante="password" autoComplete="new-password" required minLength={LARGO_MINIMO_PASSWORD}
              value={password} onChange={(e) => setPassword(e.target.value)} error={erroresCampos.password} />
            <RequisitosPassword password={password} dni={dni} className="-mt-2" />
            <Campo id="confirmarPassword" etiqueta="Repetí la contraseña" variante="password" autoComplete="new-password"
              required minLength={LARGO_MINIMO_PASSWORD} value={confirmar} onChange={(e) => setConfirmar(e.target.value)}
              error={confirmar && password !== confirmar ? "No coincide con la contraseña de arriba." : undefined} />
          </div>
          <Navegacion onVolver={() => ir(iCondiciones)}>
            <Boton type="submit" tamano="lg" anchoCompleto disabled={!passwordValida(password, dni) || password !== confirmar || !terminos}
              cargando={trabajando} textoCargando="Creando tu cuenta…">
              Crear cuenta
            </Boton>
          </Navegacion>
        </form>
      )}

      {/* ---------------- Tutor · Qué sigue ---------------- */}
      {tipo === "tutor" && paso === iAcceso + 1 && <QueSigueTutor conSesion={conSesion} />}

      <div className="mt-5 flex flex-col gap-3">
        {menorDeEdad && (
          <Alerta tono="aviso" titulo="Sos menor de edad.">
            {tipo === "tutor"
              ? "Para dar clases hay que ser mayor de 18. No se creó ninguna cuenta."
              : "Tu adulto responsable te crea el perfil desde su cuenta. No se creó ninguna cuenta."}
          </Alerta>
        )}
        {error && <Alerta tono="peligro">{error}</Alerta>}
      </div>
    </div>
  );
}

function Bienvenida({ uso, nombre }: { uso: Uso | null; nombre: string }) {
  const conHijos = uso !== "clases";
  return (
    <section className="text-center motion-safe:animate-subir">
      <span aria-hidden className="mx-auto flex size-20 items-center justify-center rounded-full bg-marca-50 text-marca-700 ring-8 ring-marca-50/50">
        <Check className="size-10" />
      </span>
      <h1 className="mt-8 text-[32px] font-extrabold">¡Bienvenido/a, {nombre.trim().split(" ")[0]}!</h1>
      <p className="mt-3 text-[16px] text-tinta-suave">
        {conHijos
          ? "Tu cuenta está lista. El siguiente paso es sumar a tu hijo o hija: le creás su propio acceso y vos decidís con qué tutores puede tomar clases."
          : "Tu cuenta está lista. Buscá un tutor y reservá tu primera clase."}
      </p>
      <div className="mt-8 flex flex-col gap-3">
        {conHijos && (
          <Link href="/cuenta/menores" className={clasesBoton("primario", "lg", "w-full")}>
            Sumar a mi hijo o hija
          </Link>
        )}
        <Link href="/buscar" className={clasesBoton(conHijos ? "secundario" : "primario", "lg", "w-full")}>
          Buscar un tutor
        </Link>
      </div>
    </section>
  );
}

function QueSigueTutor({ conSesion }: { conSesion: boolean }) {
  const router = useRouter();
  const [tipo, setTipo] = useState<TipoCredencial>("TITULO");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [subiendo, setSubiendo] = useState(false);
  const [ok, setOk] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function subir() {
    if (!archivo) return;
    setSubiendo(true);
    setError(null);
    try {
      await subirCredencial(tipo, archivo);
      setOk(true);
    } catch (err) {
      setError(err instanceof ApiError && err.message ? err.message : "No pudimos subir el archivo. Probá de nuevo.");
    } finally {
      setSubiendo(false);
    }
  }

  const pasosTutor = [
    { i: GraduationCap, t: "Subí tu título o certificado", d: "Lo revisa una persona del equipo de Tinku." },
    { i: Camera, t: "Subí tu foto de perfil", d: "Es obligatoria: los alumnos y las familias ven con quién toman clase." },
    { i: BookOpen, t: "Elegí qué materias enseñás" },
    { i: CalendarClock, t: "Publicá tus horarios" },
    { i: CircleDollarSign, t: "Poné tu precio" },
  ];

  return (
    <section>
      <Encabezado
        titulo="Cuenta de tutor creada"
        texto="Te faltan cinco cosas para aparecer en las búsquedas. La primera la podés hacer ya."
      />
      <ol className="mt-6 flex list-none flex-col gap-2 p-0">
        {pasosTutor.map(({ i: I, t, d }, n) => (
          <li key={t} className="flex items-center gap-4 rounded-2xl border border-borde bg-superficie p-4">
            <span aria-hidden className="flex size-10 shrink-0 items-center justify-center rounded-full bg-marca-50 text-marca-700">
              <I className="size-5" />
            </span>
            <span>
              <span className="block text-[15px] font-bold">
                <span className="sr-only">Paso {n + 1}: </span>
                {t}
              </span>
              {d && <span className="block text-sm text-tinta-suave">{d}</span>}
            </span>
          </li>
        ))}
      </ol>

      {conSesion ? (
        <div className="mt-6 flex flex-col gap-4 rounded-tarjeta border border-borde bg-superficie p-5">
          <Selector id="tipoCredencial" etiqueta="Tipo de documento" value={tipo} onChange={(e) => setTipo(e.target.value as TipoCredencial)}>
            {TIPOS_CREDENCIAL.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </Selector>
          <SubidaArchivo
            id="archivoCredencial"
            etiqueta="Archivo del documento"
            formatosTexto="PDF, JPG o PNG"
            accept="application/pdf,image/png,image/jpeg"
            maxMb={5}
            archivo={archivo}
            onCambio={setArchivo}
            disabled={ok}
          />
          {ok ? (
            <Alerta tono="exito">Recibimos tu documento. Queda en revisión y te avisamos en tu cuenta.</Alerta>
          ) : (
            <Boton variante="secundario" disabled={!archivo} cargando={subiendo} textoCargando="Subiendo…" onClick={subir}>
              Subir documento
            </Boton>
          )}
          {error && <Alerta tono="peligro">{error}</Alerta>}
        </div>
      ) : (
        <Alerta tono="info" className="mt-6">Ingresá con tu DNI y contraseña para subir tu documento y completar tu perfil.</Alerta>
      )}

      <Boton
        tamano="lg"
        anchoCompleto
        className="mt-6"
        onClick={() => router.replace(conSesion ? "/cuenta/horarios" : "/login?tutorRegistrado=1")}
      >
        {conSesion ? "Ir a completar mi perfil" : "Ir a ingresar"}
      </Boton>
    </section>
  );
}
