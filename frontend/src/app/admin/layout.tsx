import type { ReactNode } from "react";
import LayoutAdmin from "@/components/admin/LayoutAdmin";

/** R-G G-3 (T7-5): título de la pestaña del panel; el layout con estado vive en LayoutAdmin (cliente). */
export const metadata = { title: "Administración" };

export default function AdminLayout({ children }: { children: ReactNode }) {
  return <LayoutAdmin>{children}</LayoutAdmin>;
}
