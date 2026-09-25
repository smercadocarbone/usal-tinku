"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { CalendarDays, ChevronLeft, ShieldCheck, UserRoundCheck } from "lucide-react";
import { api, getAutorizaciones, getMenores, marcarNoConfiable, mensajeDeError, type AutorizacionMenor, type Menor } from "@/lib/api";
import { ESTADOS_PROXIMOS, type Reserva, type Solicitud } from "@/lib/reservas";
import { fechaHoraCorta } from "@/lib/formatos";
import { nombreCorto } from "@/lib/tutores";
import Pedidos from "@/components/clases/Pedidos";
import { Alerta, Avatar, Boton, EstadoReserva, EstadoVacio, Insignia, PanelTab, SkeletonLista, Tabs, Tarjeta, useToast } from "@/components/ui";

type Pestana = "proximas" | "pasadas" | "tutores";

/**
 * Vista del Adulto Responsable por hijo (R5, diseño §7): todas sus clases, sus pedidos y los
 * tutores que autorizó. En las clases de menores nunca se graba ni hay resumen (Art. II).
 */
export default function MenorPage() {
  const { id } = useParams<{ id: string }>();
  const toast = useToast();
  const [menor, setMenor] = useState<Menor | null | undefined>(undefined);
  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [pedidos, setPedidos] = useState<Solicitud[]>([]);
  const [tutores, setTutores] = useState<AutorizacionMenor[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pestana, setPestana] = useState<Pestana>("proximas");

  const cargar = useCallback(async () => {
    setError(null);
    try {
      const [menores, todas, pendientes, autorizados] = await Promise.all([
        getMenores(),
        api.get<Reserva[]>("/api/reservas"),
        api.get<Solicitud[]>("/api/solicitudes/pendientes").catch(() => []),
        getAutorizaciones(id),
      ]);
      setMenor(menores.find((m) => m.id === id) ?? null);
      setReservas(todas.filter((r) => r.beneficiarioId === id));
      setPedidos(pendientes.filter((p) => p.menorId === id));
      setTutores(autorizados);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos cargar sus datos."));
    }
  }, [id]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const [proximas, pasadas] = useMemo(() => {
    const lista = reservas ?? [];
    return [
      lista.filter((r) => ESTADOS_PROXIMOS.has(r.estado)).sort((a, b) => a.horario.localeCompare(b.horario)),
      lista.filter((r) => !ESTADOS_PROXIMOS.has(r.estado)).sort((a, b) => b.horario.localeCompare(a.horario)),
    ];
  }, [reservas]);

  async function cambiarConfianza(t: AutorizacionMenor) {
    try {
      await marcarNoConfiable(t.tutorId, !t.noConfiable);
      toast.mostrar(t.noConfiable ? "Volviste a confiar en este tutor" : "Listo: ya no le va a aparecer a tus chicos");
      await cargar();
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar el cambio."), { tono: "error" });
    }
  }

  if (menor === null) {
    return (
      <EstadoVacio titulo="No encontramos a este chico o chica" accion={<Link href="/cuenta/menores">Volver a Mis chicos</Link>}>
        Puede que lo hayas dado de baja.
      </EstadoVacio>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/cuenta/menores"
        className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida"
      >
        <ChevronLeft className="size-5" aria-hidden /> Mis chicos
      </Link>
      {menor && (
        <div className="flex items-center gap-4">
          <Avatar nombre={menor.nombre} apellido={menor.apellido} semilla={menor.id} tamano="lg" />
          <h1 className="text-[28px] font-extrabold sm:text-[36px]">
            {menor.nombre} {menor.apellido}
          </h1>
        </div>
      )}
      {error && (
        <Alerta tono="peligro" className="mt-6">
          {error}
        </Alerta>
      )}

      <Alerta tono="info" className="mt-6" titulo="Sus clases no se graban">
        En las clases de menores nunca se graba ni se genera resumen, y si aparece algo inapropiado la clase se corta al instante.
      </Alerta>

      {pedidos.length > 0 && (
        <section className="mt-8">
          <h2 className="text-lg font-bold">Pedidos para aprobar</h2>
          <div className="mt-3">
            <Pedidos pedidos={pedidos} />
          </div>
        </section>
      )}

      <Tabs
        className="mt-8"
        etiqueta="Secciones"
        activo={pestana}
        onCambio={setPestana}
        opciones={[
          { id: "proximas", label: `Próximas (${proximas.length})` },
          { id: "pasadas", label: "Pasadas" },
          { id: "tutores", label: "Tutores autorizados" },
        ]}
      />

      {reservas === null && !error ? (
        <div className="mt-6">
          <SkeletonLista filas={2} />
        </div>
      ) : pestana === "tutores" ? (
        <PanelTab id="tutores" className="mt-6">
          {tutores && tutores.length === 0 ? (
            <EstadoVacio icono={<UserRoundCheck />} titulo="Todavía no autorizaste tutores" accion={<Link href="/buscar">Buscar un tutor</Link>}>
              {menor?.nombre ?? "Tu hijo o hija"} solo puede pedir clases a los tutores que vos autorices.
            </EstadoVacio>
          ) : (
            <ul className="flex list-none flex-col gap-3 p-0">
              {(tutores ?? []).map((t) => (
                <li key={t.tutorId}>
                  <Tarjeta className="flex flex-wrap items-center gap-4">
                    <Avatar nombre={t.tutorNombre} apellido={t.tutorApellido} semilla={t.tutorId} />
                    <div className="min-w-0 flex-1">
                      <Link href={`/tutores/${t.tutorId}`} className="font-bold">
                        {nombreCorto(t.tutorNombre, t.tutorApellido)}
                      </Link>
                      <p className="text-sm text-tinta-suave">Autorizado el {fechaHoraCorta(t.autorizadoAt)}</p>
                    </div>
                    {t.noConfiable && <Insignia tono="aviso">No confiás</Insignia>}
                    <Boton variante={t.noConfiable ? "secundario" : "fantasma"} tamano="sm" icono={<ShieldCheck />}
                      onClick={() => void cambiarConfianza(t)}>
                      {t.noConfiable ? "Volver a confiar" : "No confío en este tutor"}
                    </Boton>
                  </Tarjeta>
                </li>
              ))}
            </ul>
          )}
        </PanelTab>
      ) : (
        <PanelTab id={pestana} className="mt-6">
          {(pestana === "proximas" ? proximas : pasadas).length === 0 ? (
            <EstadoVacio icono={<CalendarDays />} titulo={pestana === "proximas" ? "Sin clases próximas" : "Todavía no tuvo clases"}>
              {pestana === "proximas" ? "Cuando le reserves una clase, aparece acá." : "Las clases que ya pasaron aparecen acá."}
            </EstadoVacio>
          ) : (
            <ul className="flex list-none flex-col gap-3 p-0">
              {(pestana === "proximas" ? proximas : pasadas).map((r) => (
                <li key={r.id}>
                  <Link href={`/cuenta/reservas/${r.id}`} className="block text-tinta no-underline">
                    <Tarjeta className="flex items-center justify-between gap-3 hover:bg-superficie-hundida">
                      <span>
                        <span className="font-semibold capitalize">{fechaHoraCorta(r.horario)}</span>
                        {r.tutorNombre && <> con {nombreCorto(r.tutorNombre, r.tutorApellido)}</>}
                      </span>
                      <EstadoReserva estado={r.estado} />
                    </Tarjeta>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </PanelTab>
      )}
    </div>
  );
}
