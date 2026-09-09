import Link from "next/link";

export default function HomePage() {
  return (
    <main className="pantalla">
      <div className="tarjeta tarjeta--ancha">
        <div className="marca">
          Tinku<span>.</span>
        </div>
        <h1>Tutorías en línea</h1>
        <p>
          Clases particulares con Tutores verificados, en un entorno seguro y
          supervisado para menores.
        </p>
        <div
          style={{
            display: "flex",
            gap: "0.75rem",
            flexWrap: "wrap",
          }}
        >
          <Link className="boton" href="/registro" style={{ textDecoration: "none" }}>
            Crear mi cuenta
          </Link>
          <Link
            className="boton boton--secundario"
            href="/login"
            style={{ textDecoration: "none" }}
          >
            Iniciar sesión
          </Link>
        </div>
      </div>
    </main>
  );
}