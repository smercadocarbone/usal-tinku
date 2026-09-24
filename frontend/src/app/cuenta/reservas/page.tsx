"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { BookOpenCheck, History } from "lucide-react";
import { api, ApiError } from "@/lib/api";
import { ESTADOS_PROXIMOS, type Reserva, type Solicitud } from "@/lib/reservas";
import { useSesion } from "@/lib/useSesion";
import TarjetaClase from "@/components/clases/TarjetaClase";
import Pedidos from "@/components/clases/Pedidos";
import { Alerta, Boton, EstadoVacio, PanelTab, SkeletonLista, Tabs, clasesBoton } from "@/components/ui";

type Pestana = "proximas" | "pasadas" | "pedidos";

export default function MisClasesPage() {
  const sesion = useSesion();
  const payload = sesion?.payload;
  const esTutor = payload?.tipo === "TUTOR";
  const esAR = payload?.tipo !== "MENOR" && payload?.cap_ar === true;

  const [reservas, setReservas] = useState<Reserva[] | null>(null);
  const [error, setError] = useState(false);
  const [pestana, setPestana] = useState<Pestana>("proximas");
  const [pedidos, setPedidos] = useState<Solicitud[] | null>(null);

  const cargar = useCallback(() => {
    setError(false);
    setReservas(null);
    api
      .get<Reserva[]>("/api/reservas")
      .then(setReservas)
      .catch((err) => {
        if (err instanceof ApiError && err.status === 404) setReservas([]);
        else setError(true);
      });
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  useEffect(() => {
    if (!esAR) return;
    api
      .get<Solicitud[]>("/api/solicitudes/pendientes")
      .then(setPedidos)
      .catch(() => setPedidos([]));
  }, [esAR]);

  const { proximas, pasadas } = useMemo(() => {
    const lista = reservas ?? [];
    const prox = lista.filter((r) => ESTADOS_PROXIMOS.has(r.estado)).sort((a, b) => a.horario.localeCompare(b.horario));
    const pas = lista.filter((r) => !ESTADOS_PROXIMOS.has(r.estado)).sort((a, b) => b.horario.localeCompare(a.horario));
    return { proximas: prox, pasadas: pas };
  }, [reservas]);

  const opciones = [
    { id: "proximas" as const, label: `Próximas${proximas.length ? ` · ${proximas.length}` : ""}` },
    { id: "pasadas" as const, label: "Pasadas" },
    ...(esAR ? [{ id: "pedidos" as const, label: `Pedidos${pedidos?.length ? ` · ${pedidos.length}` : ""}` }] : []),
  ];

  const lista = pestana === "proximas" ? proximas : pasadas;

  return (
    <div className="mx-auto max-w-3xl">
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mis clases</h1>
      <Tabs etiqueta="Clases" opciones={opciones} activo={pestana} onCambio={setPestana} className="mt-6" />

      <PanelTab id={pestana} className="mt-6 focus-visible:outline-none">
        {pestana === "pedidos" ? (
          <Pedidos pedidos={pedidos} />
        ) : error ? (
          <Alerta tono="peligro" accion={<Boton variante="secundario" tamano="sm" onClick={cargar}>Probar de nuevo</Boton>}>
            No pudimos cargar tus clases. Revisá tu conexión y probá de nuevo.
          </Alerta>
        ) : reservas === null ? (
          <SkeletonLista filas={3} etiqueta="Cargando tus clases…" />
        ) : lista.length === 0 ? (
          pestana === "proximas" ? (
            <EstadoVacio
              icono={<BookOpenCheck />}
              titulo="Todavía no tenés clases"
              accion={
                esTutor ? (
                  <Link href="/cuenta/horarios" className={clasesBoton("primario")}>Revisar mi agenda</Link>
                ) : (
                  <Link href="/buscar" className={clasesBoton("primario")}>Buscar un tutor</Link>
                )
              }
            >
              {esTutor
                ? "Cuando una familia reserve un horario tuyo, la clase aparece acá."
                : "Buscá un tutor para reservar la primera."}
            </EstadoVacio>
          ) : (
            <EstadoVacio icono={<History />} titulo="Sin clases pasadas">
              Acá vas a ver las clases que ya tomaste, para calificarlas y ver su resumen.
            </EstadoVacio>
          )
        ) : (
          <ul className="flex list-none flex-col gap-3 p-0">
            {lista.map((r) => (
              <li key={r.id}>
                <TarjetaClase reserva={r} vista={esTutor ? "tutor" : "alumno"} />
              </li>
            ))}
          </ul>
        )}
      </PanelTab>
    </div>
  );
}

