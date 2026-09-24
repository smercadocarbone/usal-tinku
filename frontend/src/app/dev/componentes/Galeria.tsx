"use client";

import { useState, type ReactNode } from "react";
import { Search, Trash2, Flag } from "lucide-react";
import AppShell from "@/components/shell/AppShell";
import {
  Acordeon,
  Alerta,
  AreaTexto,
  Avatar,
  Boton,
  Campo,
  CampoCheckbox,
  Chip,
  EntradaEstrellas,
  EstadoReserva,
  EstadoVacio,
  Estrellas,
  FechaHora,
  Insignia,
  Interruptor,
  Menu,
  Modal,
  ModalConfirmacion,
  PanelTab,
  Pasos,
  Precio,
  Selector,
  SkeletonLista,
  SkeletonTarjetas,
  SubidaArchivo,
  Tabs,
  Tarjeta,
  useToast,
} from "@/components/ui";

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-4 border-t border-borde pt-8">
      <h2 className="text-2xl font-bold">{titulo}</h2>
      {children}
    </section>
  );
}

const COLORES = [
  ["marca-700", "bg-marca-700"],
  ["marca-500", "bg-marca-500"],
  ["marca-100", "bg-marca-100"],
  ["acento-500", "bg-acento-500"],
  ["tinta", "bg-tinta"],
  ["tinta-suave", "bg-tinta-suave"],
  ["fondo", "bg-fondo"],
  ["exito", "bg-exito"],
  ["aviso", "bg-aviso"],
  ["peligro", "bg-peligro"],
  ["info", "bg-info"],
] as const;

export default function Galeria() {
  const toast = useToast();
  const [dni, setDni] = useState("");
  const [pass, setPass] = useState("");
  const [bio, setBio] = useState("");
  const [archivo, setArchivo] = useState<File | null>(null);
  const [tab, setTab] = useState<"proximas" | "pasadas">("proximas");
  const [chip, setChip] = useState(true);
  const [sw, setSw] = useState(true);
  const [estrellas, setEstrellas] = useState(4);
  const [modal, setModal] = useState(false);
  const [confirmar, setConfirmar] = useState(false);

  return (
    <AppShell>
      <h1 className="text-4xl font-extrabold">Sistema visual de Tinku</h1>
      <p className="mt-2 text-tinta-suave">Página interna, solo en desarrollo. Cada componente con sus estados.</p>
      <div className="mt-8 flex flex-col gap-10">
        <Seccion titulo="Color">
          <ul className="grid list-none grid-cols-3 gap-3 p-0 sm:grid-cols-6">
            {COLORES.map(([n, c]) => (
              <li key={n} className="flex flex-col gap-1.5 text-xs font-semibold">
                <span className={`h-14 rounded-control border border-borde ${c}`} />
                {n}
              </li>
            ))}
          </ul>
        </Seccion>

        <Seccion titulo="Tipografía">
          <p className="text-4xl font-extrabold sm:text-[56px]">Display 36→56</p>
          <p className="text-[28px] font-bold sm:text-[40px]">Título h1 28→40</p>
          <p className="text-[22px] font-bold sm:text-[28px]">Título h2 22→28</p>
          <p className="text-lg font-bold sm:text-xl">Título h3 18→20</p>
          <p>Cuerpo 16 — clases particulares con tutores verificados.</p>
          <p className="text-sm text-tinta-suave">Chico 14 · <span className="text-xs">Mini 12</span></p>
        </Seccion>

        <Seccion titulo="Botones">
          <div className="flex flex-wrap items-center gap-3">
            <Boton>Reservar clase</Boton>
            <Boton variante="secundario">Ver perfil</Boton>
            <Boton variante="fantasma">Cancelar</Boton>
            <Boton variante="peligro" icono={<Trash2 />}>Dar de baja</Boton>
            <Boton variante="oscuro">Crear cuenta</Boton>
            <Boton cargando textoCargando="Guardando…">Guardar</Boton>
            <Boton disabled>Deshabilitado</Boton>
            <Boton tamano="sm">Chico</Boton>
            <Boton tamano="lg">Grande</Boton>
          </div>
        </Seccion>

        <Seccion titulo="Campos">
          <div className="grid max-w-[480px] gap-5">
            <Campo id="d-nombre" etiqueta="Nombre" placeholder="Como figura en tu DNI" />
            <Campo id="d-dni" etiqueta="DNI" variante="dni" value={dni} onValor={setDni} ayuda="Sin puntos ni espacios." />
            <Campo id="d-pass" etiqueta="Contraseña" variante="password" value={pass} onChange={(e) => setPass(e.target.value)} />
            <Campo id="d-buscar" etiqueta="Buscar" etiquetaOculta icono={<Search />} placeholder="¿Qué necesitás aprender?" />
            <Campo id="d-error" etiqueta="Email" defaultValue="hola@" error="Revisá el email: le falta el dominio." />
            <Selector id="d-sel" etiqueta="Provincia" defaultValue="cba">
              <option value="caba">CABA</option>
              <option value="cba">Córdoba</option>
            </Selector>
            <AreaTexto id="d-bio" etiqueta="Sobre mí" value={bio} onChange={(e) => setBio(e.target.value)} maxLength={500} contador />
            <CampoCheckbox id="d-check" etiqueta="Acepto los términos" />
            <SubidaArchivo
              etiqueta="Foto del frente del DNI"
              formatosTexto="JPG o PNG"
              accept="image/jpeg,image/png"
              maxMb={5}
              archivo={archivo}
              onCambio={setArchivo}
              ayuda="Con buena luz, sin reflejos y con el frente completo."
            />
            <Interruptor id="d-sw" etiqueta="Tomo clases" descripcion="Podés buscar tutores y reservar para vos." activo={sw} onCambio={setSw} />
          </div>
        </Seccion>

        <Seccion titulo="Chips, insignias y estados">
          <div className="flex flex-wrap gap-2">
            <Chip activo={chip} onClick={() => setChip(!chip)}>Secundario</Chip>
            <Chip>Universitario</Chip>
            <Chip removible>Matemática</Chip>
          </div>
          <div className="flex flex-wrap gap-2">
            <Insignia tono="exito">Verificado</Insignia>
            <Insignia tono="aviso">En revisión</Insignia>
            <Insignia tono="peligro">Rechazada</Insignia>
            <Insignia tono="acento">Nuevo</Insignia>
            <Insignia tono="info">En curso</Insignia>
            <Insignia>Neutro</Insignia>
          </div>
          <div className="flex flex-wrap gap-2">
            {["pendiente_pago", "confirmada", "en_curso", "finalizada", "cancelada", "no_show_doble"].map((e) => (
              <EstadoReserva key={e} estado={e} />
            ))}
          </div>
        </Seccion>

        <Seccion titulo="Alertas">
          <Alerta tono="info" titulo="Cómo funciona el pago">El dinero queda retenido hasta después de la clase.</Alerta>
          <Alerta tono="exito">Guardamos tus cambios.</Alerta>
          <Alerta tono="aviso" titulo="Falta tu precio" accion={<Boton tamano="sm" variante="secundario">Definir precio</Boton>}>
            Sin precio no aparecés en las búsquedas.
          </Alerta>
          <Alerta tono="peligro" cerrable>No pudimos cargar tus clases. Revisá tu conexión y probá de nuevo.</Alerta>
        </Seccion>

        <Seccion titulo="Datos">
          <div className="flex flex-wrap items-center gap-6">
            <Avatar nombre="Jorge" apellido="Martínez" semilla="1" tamano="lg" verificado />
            <Avatar nombre="Sofía" apellido="Pérez" semilla="2" />
            <Avatar nombre="Ana" semilla="3" tamano="sm" />
            <Precio valor={15000} porHora />
            <Precio valor={null} />
            <Estrellas valor={4.8} cantidad={12} />
            <FechaHora inicio="2026-09-24T21:00:00Z" />
            <FechaHora inicio="2026-09-24T21:00:00Z" fin="2026-09-24T22:00:00Z" formato="largo" />
          </div>
          <EntradaEstrellas valor={estrellas} onCambio={setEstrellas} />
        </Seccion>

        <Seccion titulo="Pasos y pestañas">
          <Pasos pasos={["Cuándo", "Duración", "Resumen"]} actual={1} />
          <Tabs
            etiqueta="Clases"
            activo={tab}
            onCambio={setTab}
            opciones={[
              { id: "proximas", label: "Próximas" },
              { id: "pasadas", label: "Pasadas" },
            ]}
          />
          <PanelTab id={tab}>Panel {tab}</PanelTab>
          <Tabs
            etiqueta="Vista"
            variante="segmentado"
            activo={tab}
            onCambio={setTab}
            opciones={[
              { id: "proximas", label: "Semana" },
              { id: "pasadas", label: "Calendario" },
            ]}
          />
        </Seccion>

        <Seccion titulo="Tarjetas y vacíos">
          <div className="grid gap-4 sm:grid-cols-3">
            <Tarjeta variante="plana">Plana</Tarjeta>
            <Tarjeta>Elevada</Tarjeta>
            <Tarjeta variante="interactiva">Interactiva</Tarjeta>
          </div>
          <EstadoVacio icono={<Search />} titulo="Todavía no tenés clases" accion={<Boton>Buscar un tutor</Boton>}>
            Buscá un tutor para reservar la primera.
          </EstadoVacio>
          <SkeletonLista filas={2} />
          <SkeletonTarjetas cantidad={3} />
        </Seccion>

        <Seccion titulo="Superposiciones">
          <div className="flex flex-wrap items-center gap-3">
            <Boton variante="secundario" onClick={() => setModal(true)}>Abrir hoja</Boton>
            <Boton variante="secundario" onClick={() => setConfirmar(true)}>Confirmación</Boton>
            <Boton variante="secundario" onClick={() => toast.mostrar("Guardado")}>Toast</Boton>
            <Menu etiqueta="Más opciones" items={[{ texto: "Reportar este perfil", icono: <Flag />, onClick: () => undefined, peligro: true }]} />
          </div>
          <Acordeon
            items={[
              { pregunta: "¿Cómo se paga?", respuesta: "Con MercadoPago. El dinero queda retenido hasta después de la clase." },
              { pregunta: "¿Desde qué edad?", respuesta: "Los chicos usan Tinku con su adulto responsable." },
            ]}
          />
          <Modal abierto={modal} onCerrar={() => setModal(false)} titulo="Filtros" variante="hoja" pie={<Boton onClick={() => setModal(false)}>Ver resultados</Boton>}>
            Contenido de la hoja.
          </Modal>
          <ModalConfirmacion
            abierto={confirmar}
            onCerrar={() => setConfirmar(false)}
            onConfirmar={() => setConfirmar(false)}
            titulo="¿Dar de baja a Sofía?"
            textoConfirmar="Dar de baja a Sofía"
          >
            Se anonimizan sus datos y no va a poder volver a entrar. Sus clases futuras se cancelan.
          </ModalConfirmacion>
        </Seccion>
      </div>
    </AppShell>
  );
}
