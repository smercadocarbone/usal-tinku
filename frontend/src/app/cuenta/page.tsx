"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Lock } from "lucide-react";
import ChecklistTutor from "@/components/tutor/ChecklistTutor";
import { useSesion } from "@/lib/useSesion";
import { aceptarClausula, actualizarCapacidades, CLAUSULA_TERMINOS, getClausula, getPerfilPropio, mensajeDeError, type PerfilPropio } from "@/lib/api";
import { Alerta, Avatar, Boton, Interruptor, Skeleton, Tarjeta, useToast } from "@/components/ui";

export default function CuentaPerfilPage() {
  const sesion = useSesion();
  const payload = sesion?.payload;
  const toast = useToast();

  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);

  // ADR-M3-05: los Términos se aceptan al crear la cuenta. Solo si cambió la versión (o la
  // cuenta es anterior) se piden de nuevo acá, una vez.
  const [terminosPendientes, setTerminosPendientes] = useState(false);
  useEffect(() => {
    if (!payload || payload.tipo === "MENOR") return;
    getClausula(CLAUSULA_TERMINOS)
      .then((c) => setTerminosPendientes(!c.aceptada))
      .catch(() => setTerminosPendientes(false));
  }, [payload]);

  async function aceptarTerminos() {
    try {
      await aceptarClausula(CLAUSULA_TERMINOS);
      setTerminosPendientes(false);
      toast.mostrar("Gracias: aceptaste los Términos actualizados");
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar tu aceptación."), { tono: "error" });
    }
  }

  useEffect(() => {
    getPerfilPropio()
      .then(setPerfil)
      .catch(() => setPerfil(null))
      .finally(() => setCargando(false));
  }, []);

  /** "¿Cómo usás Tinku?" se aplica al toque (UX-05 §2); si el backend lo rechaza, vuelve atrás. */
  async function cambiar(capEst: boolean, capAr: boolean) {
    if (!perfil) return;
    const anterior = perfil;
    setPerfil({ ...perfil, capacidadEstudiante: capEst, capacidadAdultoResponsable: capAr });
    setGuardando(true);
    try {
      setPerfil(await actualizarCapacidades(capEst, capAr));
      toast.mostrar(
        capAr && !anterior.capacidadAdultoResponsable
          ? "Listo. Volvé a ingresar para ver Mis chicos en el menú."
          : "Guardamos tus cambios"
      );
    } catch (err) {
      setPerfil(anterior);
      toast.mostrar(mensajeDeError(err, "No pudimos guardar el cambio."), { tono: "error" });
    } finally {
      setGuardando(false);
    }
  }

  const esMenor = (perfil?.tipo ?? payload?.tipo) === "MENOR";
  const esTutor = (perfil?.tipo ?? payload?.tipo) === "TUTOR";

  return (
    <section className="flex flex-col gap-6">
      {terminosPendientes && (
        <Alerta
          tono="info"
          titulo="Actualizamos los Términos y Condiciones"
          accion={<Boton tamano="sm" onClick={() => void aceptarTerminos()}>Aceptar</Boton>}
        >
          Incluyen el resumen automático: en las clases entre adultos en las que se contrate, se graba solo el audio (nunca
          con menores) y se borra apenas se transcribe.
        </Alerta>
      )}
      <Tarjeta className="flex flex-col gap-5 sm:flex-row sm:items-center">
        {cargando ? (
          <div role="status" className="flex items-center gap-4">
            <span className="sr-only">Cargando tu perfil…</span>
            <Skeleton className="size-16 rounded-full" />
            <div className="flex flex-col gap-2">
              <Skeleton className="h-5 w-40" />
              <Skeleton className="h-4 w-56" />
            </div>
          </div>
        ) : (
          <>
            <Avatar nombre={perfil?.nombre ?? "?"} apellido={perfil?.apellido} semilla={perfil?.id} tamano="lg" />
            <div className="min-w-0">
              <h2 className="text-2xl font-bold">{perfil ? `${perfil.nombre} ${perfil.apellido}` : "Tu perfil"}</h2>
              {perfil?.email && <p className="truncate text-[15px] text-tinta-suave">{perfil.email}</p>}
            </div>
          </>
        )}
      </Tarjeta>

      <p className="flex gap-2 text-sm text-tinta-tenue">
        <Lock className="size-4 shrink-0 translate-y-0.5" aria-hidden />
        Tu nombre y tu DNI vienen de la verificación de identidad y no se pueden cambiar. El email y la contraseña los cambiás en{" "}
        <Link href="/cuenta/acceso" className="font-semibold underline">Seguridad y acceso</Link>.
      </p>

      {esMenor && (
        <Tarjeta variante="plana">
          <p className="text-[15px] text-tinta-suave">Tu cuenta la administra tu adulto responsable. Él o ella reserva y paga tus clases.</p>
        </Tarjeta>
      )}

      {esTutor && <ChecklistTutor tutorId={perfil?.id ?? null} />}

      {(perfil?.tipo === "ADULTO" || perfil?.tipo === "TUTOR") && (
        <Tarjeta>
          <h2 className="text-lg font-bold">{esTutor ? "¿También usás Tinku para aprender?" : "¿Cómo usás Tinku?"}</h2>
          {esTutor && (
            <p className="mt-1 text-sm text-tinta-suave">
              Podés tomar clases o sumar a tus chicos, siempre con otros tutores: nunca con vos mismo.
            </p>
          )}
          <div className="mt-5 flex flex-col gap-5">
            <Interruptor
              id="capEstudiante"
              etiqueta="Tomo clases"
              descripcion="Podés buscar tutores y reservar clases para vos."
              activo={perfil.capacidadEstudiante}
              disabled={guardando}
              onCambio={(v) => void cambiar(v, perfil.capacidadAdultoResponsable)}
            />
            <div className="border-t border-borde" />
            <Interruptor
              id="capAr"
              etiqueta="Tengo hijos o hijas a cargo"
              descripcion="Suma la sección Mis chicos: les creás su acceso, autorizás a sus tutores y pagás sus clases."
              activo={perfil.capacidadAdultoResponsable}
              disabled={guardando}
              onCambio={(v) => void cambiar(perfil.capacidadEstudiante, v)}
            />
          </div>
        </Tarjeta>
      )}
    </section>
  );
}
