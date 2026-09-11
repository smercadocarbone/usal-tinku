import type { NivelCatalogo } from "./api";

/**
 * Fixture local del catálogo (2 ramas) para que la UI del chunk M2-F se
 * pueda probar antes de que el backend aterrice la rama M2.
 *
 * ponytail: lo reemplaza el orquestador en FASE 3 por el GET /api/catalogos
 * real (que ya es la fuente cuando el backend responde 200; este fixture
 * solo actúa como fallback de red/404, ver `getCatalogos` en api.ts).
 */
export const catalogoMock: NivelCatalogo[] = [
  {
    nivel: "primario",
    cursos: [
      {
        nombre: "4°",
        materias: [
          {
            nombre: "Matemática",
            temas: [
              {
                id: "f1a1c111-1111-4111-8111-111111111101",
                nombre: "División",
                descripcion:
                  "Algoritmo de la división, cociente y resto, y divisiones por números de dos cifras.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111102",
                nombre: "Fracciones",
                descripcion:
                  "Sentido de la fracción, fracciones equivalentes y comparación.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111103",
                nombre: "Números decimales",
                descripcion:
                  "Décimos, centésimos y milésimos; suma y resta de decimales.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111104",
                nombre: "Medidas",
                descripcion:
                  "Unidades de longitud, peso y capacidad y sus equivalencias.",
              },
            ],
          },
          {
            nombre: "Lengua",
            temas: [
              {
                id: "f1a1c111-1111-4111-8111-111111111201",
                nombre: "Cuentos tradicionales",
                descripcion:
                  "Estructura del cuento, personajes típicos y distintas versiones.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111202",
                nombre: "Producción de textos",
                descripcion:
                  "Planificación y escritura de narraciones y descripciones.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111203",
                nombre: "Comprensión lectora",
                descripcion:
                  "Identificar ideas principales, secuencias e inferencias.",
              },
              {
                id: "f1a1c111-1111-4111-8111-111111111204",
                nombre: "Ortografía",
                descripcion: "Reglas de acentuación y uso de b/v y s/c/z.",
              },
            ],
          },
        ],
      },
    ],
  },
  {
    nivel: "secundario",
    cursos: [
      {
        nombre: "1°",
        materias: [
          {
            nombre: "Matemática",
            temas: [
              {
                id: "f2a2c222-2222-4222-8222-222222222301",
                nombre: "Números enteros",
                descripcion:
                  "Números negativos, orden y operaciones básicas: suma, resta y multiplicación.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222302",
                nombre: "Ecuaciones",
                descripcion:
                  "Ecuaciones lineales sencillas con una incógnita y su verificación.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222303",
                nombre: "Proporcionalidad",
                descripcion: "Razones, proporciones y regla de tres simple.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222304",
                nombre: "Figuras planas",
                descripcion:
                  "Clasificación de triángulos y cuadriláteros, perímetro y área.",
              },
            ],
          },
          {
            nombre: "Prácticas del Lenguaje",
            temas: [
              {
                id: "f2a2c222-2222-4222-8222-222222222401",
                nombre: "Cuentos y leyendas",
                descripcion:
                  "Narraciones tradicionales: estructura, personajes y versiones.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222402",
                nombre: "Texto expositivo",
                descripcion:
                  "Organización de la información y búsqueda de ideas principales.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222403",
                nombre: "Comprensión de textos",
                descripcion:
                  "Inferencias, secuencias y relación entre personajes en textos narrativos.",
              },
              {
                id: "f2a2c222-2222-4222-8222-222222222404",
                nombre: "Ortografía y puntuación",
                descripcion:
                  "Uso de mayúsculas, tildes y signos de puntuación básicos.",
              },
            ],
          },
        ],
      },
    ],
  },
];