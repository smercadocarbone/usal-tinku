"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { CalendarDays, Plus, UserMinus, UsersRound } from "lucide-react";
import { api, ApiError, getMenores, mensajeDeError, type Menor } from "@/lib/api";
import type { Reserva, Solicitud } from "@/lib/reservas";
import { ESTADOS_PROXIMOS } from "@/lib/reservas";
import { fechaHoraCorta } from "@/lib/formatos";
import { TIEMPOS } from "@/lib/tiempos";
import { nombreCorto } from "@/lib/tutores";
import { LARGO_MINIMO_PASSWORD, passwordValida } from "@/lib/password";
import Pedidos from "@/components/clases/Pedidos";
import {
  Alerta,
  Avatar,
  Boton,
  Campo,
  CampoCheckbox,
  EstadoVacio,
  Menu,
  Modal,
  ModalConfirmacion,
  Pasos,
  RequisitosPassword,
  SkeletonLista,
  SubidaArchivo,
  Tarjeta,
  useToast,
} from "@/components/ui";

/** Texto del consentimiento que se registra con `versionTextoConsentimiento` (BR-CONSENT-01). */
const VERSION_CONSENTIMIENTO = "v1";
const TEXTO_CONSENTIMIENTO =
  "Confirmo que soy el Adulto Responsable del menor y doy mi consentimiento explícito para crear su cuenta";

export default function MisChicosPage() {
  const toast = useToast();
  const [menores, setMenores] = useState<Menor[] | null>(null);
  const [errorMenores, setErrorMenores] = useState<string | null>(null);
  const [pedidos, setPedidos] = useState<Solicitud[] | null>(null);
  const [reservas, setReservas] = useState<Reserva[]>([]);
  const [alta, setAlta] = useState(false);
  const [baja, setBaja] = useState<Menor | null>(null);

  const cargar = useCallback(() => {
    setErrorMenores(null);
    getMenores()
      .then(setMenores)
      .catch((err) => setErrorMenores(mensajeDeError(err, "No pudimos cargar a tus chicos.")));
    api
      .get<Solicitud[]>("/api/solicitudes/pendientes")
      .then(setPedidos)
      .catch(() => setPedidos([]));
    api
      .get<Reserva[]>("/api/reservas")
      .then(setReservas)
      .catch(() => setReservas([]));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const proximaDe = useMemo(() => {
    const mapa = new Map<string, Reserva[]>();
    for (const r of reservas) {
      if (!r.beneficiarioId || !ESTADOS_PROXIMOS.has(r.estado)) continue;
      mapa.set(r.beneficiarioId, [...(mapa.get(r.beneficiarioId) ?? []), r].sort((a, b) => a.horario.localeCompare(b.horario)));
    }
    return mapa;
  }, [reservas]);

  return (
    <div className="mx-auto max-w-3xl">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mis chicos</h1>
          <p className="mt-1 text-[15px] text-tinta-suave">Vos reservás, pagás y decidís con qué tutores toman clases.</p>
        </div>
        <Boton icono={<Plus />} onClick={() => setAlta(true)}>
          Sumar a un hijo o hija
        </Boton>
      </div>

      {pedidos && pedidos.length > 0 && (
        <section className="mt-8">
          <h2 className="text-lg font-bold">Pedidos para aprobar</h2>
          <div className="mt-3">
            <Pedidos pedidos={pedidos} />
          </div>
        </section>
      )}

      <section className="mt-8">
        {errorMenores && (
          <Alerta tono="peligro" accion={<Boton variante="secundario" tamano="sm" onClick={cargar}>Probar de nuevo</Boton>}>
            {errorMenores}
          </Alerta>
        )}
        {!errorMenores && menores === null && <SkeletonLista filas={2} etiqueta="Cargando a tus chicos…" />}
        {menores !== null && menores.length === 0 && (
          <EstadoVacio
            icono={<UsersRound />}
            titulo="No tenés menores a cargo todavía."
            accion={<Boton onClick={() => setAlta(true)}>Sumar a un hijo o hija</Boton>}
          >
            Le creás su propio acceso con su DNI. Desde los {TIEMPOS.edadMinimaMenor} años.
          </EstadoVacio>
        )}
        {menores !== null && menores.length > 0 && (
          <ul className="grid list-none grid-cols-1 gap-3 p-0 sm:grid-cols-2">
            {menores.map((m) => {
              const proximas = proximaDe.get(m.id) ?? [];
              return (
                <li key={m.id}>
                  <Tarjeta className="flex h-full flex-col gap-4">
                    <div className="flex items-start gap-4">
                      <Avatar nombre={m.nombre} apellido={m.apellido} semilla={m.id} tamano="lg" />
                      <div className="min-w-0 flex-1">
                        <h2 className="truncate text-lg font-bold">
                          {m.nombre} {m.apellido}
                        </h2>
                        <p className="text-sm text-tinta-suave">
                          {proximas.length === 0 ? "Sin clases próximas" : `${proximas.length} ${proximas.length === 1 ? "clase próxima" : "clases próximas"}`}
                        </p>
                      </div>
                      <Menu
                        etiqueta={`Opciones de ${m.nombre}`}
                        items={[{ texto: "Dar de baja", icono: <UserMinus />, peligro: true, onClick: () => setBaja(m) }]}
                      />
                    </div>
                    {proximas[0] && (
                      <Link
                        href={`/cuenta/reservas/${proximas[0].id}`}
                        className="flex items-center gap-3 rounded-2xl bg-fondo p-3 text-sm text-tinta no-underline hover:bg-superficie-hundida"
                      >
                        <CalendarDays className="size-5 shrink-0 text-marca-700" aria-hidden />
                        <span>
                          <span className="font-semibold capitalize">{fechaHoraCorta(proximas[0].horario)}</span>
                          {proximas[0].tutorNombre && <> con {nombreCorto(proximas[0].tutorNombre, proximas[0].tutorApellido)}</>}
                        </span>
                      </Link>
                    )}
                    <div className="mt-auto flex flex-wrap gap-x-5 gap-y-1 text-sm font-semibold">
                      <Link href={`/cuenta/menores/${m.id}`}>Ver clases y tutores</Link>
                      <Link href="/buscar">Buscar un tutor para {m.nombre}</Link>
                    </div>
                  </Tarjeta>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <AltaMenor
        abierto={alta}
        onCerrar={() => setAlta(false)}
        onCreado={(nombre) => {
          setAlta(false);
          toast.mostrar(`Listo, sumaste a ${nombre}`);
          cargar();
        }}
      />
      <BajaMenor
        key={baja?.id ?? "ninguno"}
        menor={baja}
        onCerrar={() => setBaja(null)}
        onHecha={(nombre) => {
          setBaja(null);
          toast.mostrar(`Diste de baja a ${nombre}`);
          cargar();
        }}
      />
    </div>
  );
}

/** Baja en dos tiempos, como el backend (FR-ID-014): si hay clases futuras, se avisa y se confirma aparte. */
function BajaMenor({ menor, onCerrar, onHecha }: { menor: Menor | null; onCerrar: () => void; onHecha: (nombre: string) => void }) {
  const [conReservas, setConReservas] = useState(false);
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmar() {
    if (!menor) return;
    setProcesando(true);
    setError(null);
    try {
      await api.delete(`/api/usuarios/menores/${menor.id}${conReservas ? "?confirmar=true" : ""}`);
      onHecha(menor.nombre);
    } catch (err) {
      if (!conReservas && err instanceof ApiError && (err.status === 409 || err.status === 422)) setConReservas(true);
      else setError(mensajeDeError(err, "No pudimos dar de baja la cuenta."));
    } finally {
      setProcesando(false);
    }
  }

  return (
    <ModalConfirmacion
      abierto={menor !== null}
      onCerrar={onCerrar}
      onConfirmar={confirmar}
      cargando={procesando}
      titulo={menor ? `¿Dar de baja a ${menor.nombre}?` : ""}
      textoConfirmar={conReservas ? "Confirmar baja" : menor ? `Dar de baja a ${menor.nombre}` : "Dar de baja"}
    >
      <p>
        Se borran sus datos personales y {menor?.nombre ?? "su cuenta"} no va a poder volver a entrar. Sus pedidos pendientes se rechazan. No se puede deshacer.
      </p>
      {conReservas && (
        <Alerta tono="aviso" className="mt-4" titulo="Este menor tiene reservas futuras.">
          Se cancelan como una cancelación tuya: con {TIEMPOS.cancelacionSinPenalidadHoras} hs o más de anticipación te devolvemos el total; con menos, se le paga al tutor.
        </Alerta>
      )}
      {error && <Alerta tono="peligro" className="mt-4">{error}</Alerta>}
    </ModalConfirmacion>
  );
}

function AltaMenor({ abierto, onCerrar, onCreado }: { abierto: boolean; onCerrar: () => void; onCreado: (nombre: string) => void }) {
  const [paso, setPaso] = useState(0);
  const [nombre, setNombre] = useState("");
  const [apellido, setApellido] = useState("");
  const [dni, setDni] = useState("");
  const [fechaNac, setFechaNac] = useState("");
  const [fotoDni, setFotoDni] = useState<File | null>(null);
  const [password, setPassword] = useState("");
  const [consentimiento, setConsentimiento] = useState(false);
  const [procesando, setProcesando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!abierto) return;
    setPaso(0);
    setError(null);
  }, [abierto]);

  async function crear() {
    if (!fotoDni) return;
    setProcesando(true);
    setError(null);
    const datos = {
      dniDeclarado: dni,
      nombreDeclarado: nombre.trim(),
      apellidoDeclarado: apellido.trim(),
      fechaNacimientoDeclarada: fechaNac,
      password,
      consentimientoExplicito: true,
      versionTextoConsentimiento: VERSION_CONSENTIMIENTO,
    };
    const form = new FormData();
    form.append("datos", new Blob([JSON.stringify(datos)], { type: "application/json" }));
    form.append("fotoDni", fotoDni);
    try {
      await api.post("/api/usuarios/menores", form);
      onCreado(nombre.trim());
      setNombre("");
      setApellido("");
      setDni("");
      setFechaNac("");
      setFotoDni(null);
      setPassword("");
      setConsentimiento(false);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos crear la cuenta. Probá de nuevo."));
    } finally {
      setProcesando(false);
    }
  }

  const pasos = ["Datos", "DNI", "Acceso", "Consentimiento"];
  const puedeSeguir = [
    !!(nombre.trim() && apellido.trim() && dni.length >= 7 && fechaNac),
    !!fotoDni,
    passwordValida(password, dni),
    consentimiento,
  ][paso];

  return (
    <Modal
      abierto={abierto}
      onCerrar={onCerrar}
      variante="hoja"
      titulo="Sumar a un hijo o hija"
      pie={
        <>
          {paso > 0 && (
            <Boton variante="secundario" onClick={() => setPaso(paso - 1)} disabled={procesando}>
              Volver
            </Boton>
          )}
          {paso < 3 ? (
            <Boton disabled={!puedeSeguir} onClick={() => setPaso(paso + 1)}>
              Continuar
            </Boton>
          ) : (
            <Boton disabled={!puedeSeguir} cargando={procesando} textoCargando="Verificando…" onClick={crear}>
              Crear su cuenta
            </Boton>
          )}
        </>
      }
    >
      <div className="flex flex-col gap-5 pb-2">
        <Pasos pasos={pasos} actual={paso} />
        {paso === 0 && (
          <>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Campo id="nombreMenor" etiqueta="Nombre" value={nombre} onChange={(e) => setNombre(e.target.value)} required />
              <Campo id="apellidoMenor" etiqueta="Apellido" value={apellido} onChange={(e) => setApellido(e.target.value)} required />
            </div>
            <Campo id="dniMenor" etiqueta="DNI del menor" variante="dni" value={dni} onValor={setDni} required />
            <Campo
              id="fechaNacMenor"
              etiqueta="Fecha de nacimiento"
              type="date"
              value={fechaNac}
              onChange={(e) => setFechaNac(e.target.value)}
              ayuda={`Tiene que tener al menos ${TIEMPOS.edadMinimaMenor} años.`}
              required
            />
          </>
        )}
        {paso === 1 && (
          <SubidaArchivo
            id="fotoDniMenor"
            etiqueta="Foto del DNI del menor (frente)"
            formatosTexto="JPG o PNG"
            accept="image/jpeg,image/png"
            maxMb={5}
            capturar
            archivo={fotoDni}
            onCambio={setFotoDni}
            ayuda="Verificamos que los datos coincidan. La foto no se guarda."
          />
        )}
        {paso === 2 && (
          <>
            <p className="text-[15px] text-tinta-suave">
              {nombre || "Tu hijo o hija"} va a ingresar con su DNI y esta contraseña. No va a poder pagar ni elegir tutores sin vos.
            </p>
            <Campo
              id="passMenor"
              etiqueta="Contraseña para su cuenta"
              variante="password"
              minLength={LARGO_MINIMO_PASSWORD}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <RequisitosPassword password={password} dni={dni} />
          </>
        )}
        {paso === 3 && (
          <>
            <div className="rounded-2xl border border-borde bg-fondo p-4 text-[15px] leading-relaxed text-tinta">
              <p className="font-semibold">Consentimiento del adulto responsable</p>
              <p className="mt-2 text-tinta-suave">
                Para crear la cuenta de un menor necesitamos tu consentimiento explícito, separado de los términos generales. Sus datos se usan
                solo para su cuenta y sus clases, y podés darla de baja cuando quieras.
              </p>
              <p className="mt-2 text-xs text-tinta-tenue">Versión {VERSION_CONSENTIMIENTO}</p>
            </div>
            <CampoCheckbox id="consentimiento" etiqueta={TEXTO_CONSENTIMIENTO} checked={consentimiento} onChange={(e) => setConsentimiento(e.target.checked)} />
          </>
        )}
        {error && <Alerta tono="peligro">{error}</Alerta>}
      </div>
    </Modal>
  );
}
