/**
 * Librería de UI de Tinku (UX-01 §3). Ejemplos vivos en `/dev/componentes`
 * (solo en desarrollo).
 *
 * Regla: ninguna pantalla vuelve a escribir a mano las clases de un botón, un
 * input, una tarjeta o un banner. Si algo no entra en estos componentes, se
 * extiende ACÁ — no se copia una variante nueva en la pantalla de turno.
 */
export { default as Boton, clasesBoton } from "./Boton";
export type { BotonProps, VarianteBoton, TamanoBoton } from "./Boton";

export { default as Campo, CampoSelect, Selector, CampoCheckbox, AreaTexto, clasesControl, formatearDni } from "./Campo";
export type { CampoProps, CampoSelectProps, CampoCheckboxProps, AreaTextoProps } from "./Campo";

export { default as SubidaArchivo } from "./SubidaArchivo";
export type { SubidaArchivoProps } from "./SubidaArchivo";

export { default as Chip } from "./Chip";
export type { ChipProps } from "./Chip";

export { default as Skeleton, SkeletonLista, SkeletonTarjetas, SkeletonPerfil } from "./Skeleton";
export type { SkeletonProps } from "./Skeleton";

export { default as Alerta } from "./Alerta";
export type { AlertaProps, TonoAlerta } from "./Alerta";

export { default as Tarjeta, enlaceTarjeta } from "./Tarjeta";
export type { TarjetaProps, VarianteTarjeta } from "./Tarjeta";

export { default as Insignia } from "./Insignia";
export type { InsigniaProps, TonoInsignia } from "./Insignia";

export { default as EstadoReserva } from "./EstadoReserva";

export { default as Cargando } from "./Cargando";
export type { CargandoProps } from "./Cargando";

export { default as EstadoVacio } from "./EstadoVacio";
export type { EstadoVacioProps } from "./EstadoVacio";

export { default as Tabs, PanelTab } from "./Tabs";
export type { TabsProps, OpcionTab, PanelTabProps } from "./Tabs";

export { default as Modal } from "./Modal";
export type { ModalProps } from "./Modal";

export { default as ModalConfirmacion } from "./ModalConfirmacion";
export type { ModalConfirmacionProps } from "./ModalConfirmacion";

export { ProveedorToast, useToast } from "./Toast";

export { default as Avatar, iniciales } from "./Avatar";
export type { AvatarProps } from "./Avatar";

export { default as Pasos } from "./Pasos";
export type { PasosProps } from "./Pasos";

export { default as Precio } from "./Precio";
export type { PrecioProps } from "./Precio";

export { default as Estrellas, EntradaEstrellas } from "./Estrellas";
export type { EstrellasProps, EntradaEstrellasProps } from "./Estrellas";

export { default as FechaHora } from "./FechaHora";
export type { FechaHoraProps } from "./FechaHora";

export { default as Interruptor } from "./Interruptor";
export type { InterruptorProps } from "./Interruptor";

export { default as Menu } from "./Menu";
export type { MenuProps, ItemMenu } from "./Menu";

export { default as Acordeon } from "./Acordeon";
export type { ItemAcordeon } from "./Acordeon";

/** @deprecated Reemplazado por `Toast` (UX-01 §3). Alias hasta migrar sus dos usos. */
export { default as IndicadorGuardado } from "./IndicadorGuardado";
export type { IndicadorGuardadoProps, EstadoGuardado } from "./IndicadorGuardado";
