import { redirect } from "next/navigation";

/** "Mi perfil" se unificó en Mi cuenta (sección "Como tutor"); los enlaces viejos siguen andando. */
export default function PerfilTutorPage() {
  redirect("/cuenta/presentacion");
}
