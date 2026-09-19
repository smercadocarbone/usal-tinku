/**
 * Librería de UI de Tinku.
 *
 * Regla: ninguna pantalla vuelve a escribir a mano las clases de un botón, un
 * input, una tarjeta o un banner. Si algo no entra en estos componentes, se
 * extiende ACÁ — no se copia una variante nueva en la pantalla de turno. Ese
 * copiado es exactamente lo que había dejado ~120 instancias duplicadas y tres
 * escalas de grises conviviendo.
 *
 * Todos son presentacionales y sirven desde server components — salvo `Tabs`,
 * que necesita estado de foco y navegación por teclado y por eso sí lleva
 * "use client".
 */
export { default as Boton, clasesBoton } from "./Boton";
export type { BotonProps, VarianteBoton, TamanoBoton } from "./Boton";

export { default as Campo, CampoSelect, CampoCheckbox, clasesControl } from "./Campo";
export type { CampoProps, CampoSelectProps, CampoCheckboxProps } from "./Campo";

export { default as Chip } from "./Chip";
export type { ChipProps } from "./Chip";

export { default as Skeleton } from "./Skeleton";
export type { SkeletonProps } from "./Skeleton";

export { default as Alerta } from "./Alerta";
export type { AlertaProps, TonoAlerta } from "./Alerta";

export { default as Tarjeta } from "./Tarjeta";
export type { TarjetaProps } from "./Tarjeta";

export { default as Insignia } from "./Insignia";
export type { InsigniaProps, TonoInsignia } from "./Insignia";

export { default as Cargando } from "./Cargando";
export type { CargandoProps } from "./Cargando";

export { default as EstadoVacio } from "./EstadoVacio";
export type { EstadoVacioProps } from "./EstadoVacio";

export { default as Tabs, PanelTab } from "./Tabs";
export type { TabsProps, OpcionTab, PanelTabProps } from "./Tabs";

export { default as IndicadorGuardado } from "./IndicadorGuardado";
export type { IndicadorGuardadoProps, EstadoGuardado } from "./IndicadorGuardado";
