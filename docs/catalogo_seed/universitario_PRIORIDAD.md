# Universitario — Prioridad de carreras (rama universitaria del seed M2-F)

Criterio: qué carreras entraron al seed, cuáles quedaron afuera y por qué. El corte es de
**demanda de tutoría y reconocibilidad de cátedra por un tutor real**, priorizando materias y
temas que un tutor del ciclo básico de esa carrera reconoce al instante.

## Criterios aplicados
1. **Demanda de tutoría universitaria real** (ciclo básico = 1°–2° año es donde más tutoría hay: filtros tipo Análisis Matemático, Anatomía, Teoría Contable, Contratos/Civil, Química General).
2. **Reconocibilidad del plan**: se cargó solo donde había plan/programa real citable (UBA, UTN, UNLP).
3. **Cobertura sin duplicar temas dentro de la misma materia/carrera** (la UNIQUE `(nivel, anio_o_carrera, materia)` del contrato 2a). Las variantes de ingeniería comparten tronco básico; se eligió diferenciar las materias por carrera para no repetir el mismo bloque de temas.
4. **Presupuesto del chunk**: 300–400 temas total. Con 13 carreras × ~3,5 materias × ~8 temas se llegó a 392.

## Carreras que entraron (13) y por qué
| # | Carrera | Materias | Por qué |
|---|---|---|---|
| 1 | Medicina | Anatomía, Histología..., Química Biológica, Fisiología y Biofísica | Demanda de tutoría #1 en Argentina (CBC + ciclo biomédico); plan UBA 7591/09 citable. |
| 2 | Abogacía | Civil PG, Constitucional, Obligaciones, Penal | Materias del CPC (1°–2° año) con enorme demanda de apuntes/parciales; plan UBA 3798/04 citable. |
| 3 | Contador Público | Teoría Contable, Análisis Matemático I, Economía, Administración General | El filtro clásico de FCE-UBA (partida doble, límites/derivadas); plan 2019 citable. |
| 4 | Ingeniería Civil | AM I, Álgebra y GA, Física I, Química General | 1° año UTN, el tronco de ciencias básicas; arquitectos/ingenieros piden tutoría desde el primer cuatri. |
| 5 | Ingeniería Industrial | AM II, Física II, Prob. y Estadística, Economía | 2° año UTN; suma cobertura de matemática/física avanzada sin duplicar el 1° año de Civil. |
| 6 | Ing. en Sistemas de Información | Algoritmos, Matemática Discreta, Arquitectura de Computadoras, Sistemas y Organizaciones | 1° año UTN (Ord. 1150); programación y arquitectura tienen tutoría propia y creciente. |
| 7 | Lic. en Economía | Microeconomía I, Macroeconomía I, Estadística I, Análisis Matemático I | FCE-UBA; micro/macro son las materias "puente" del segundo tramo. |
| 8 | Lic. en Administración de Empresas | Administración General, Teoría Contable, Microeconomía I | FCE-UBA; comparte tramo común con Contador/Economía (documentado en FUENTES). |
| 9 | Lic. en Psicología | Procesos Psicológicos Básicos, Historia de la Psicología, Neurofisiología, Metodología | UBA-Psicología, 1°–2° año; muy tutorada (resúmenes, parciales, finales). |
| 10 | Arquitectura | Matemática, ICP I/II, Sistemas de Representación Geométrica | FADU-UBA; geometría/dibujo y CBC son territorio clásico de tutoría. |
| 11 | Profesorado de Matemática | Análisis Matemático I, Álgebra, Lógica, Geometría Analítica | UNLP-FaHCE; el ingreso/profesorado de matemática es de las carreras con más tutoría per cápita (materias formales). |
| 12 | Odontología | Anatomía General y Estomatológica, Histología y Embriología, Bioquímica General | UBA-Odontología; materias de 2° año (primer año del Ciclo Profesional), muy tutoradas por la cabeza de la cursada. |
| 13 | Lic. en Enfermería | Estructura y Función del Cuerpo Humano, Microbiología y Parasitología, Fundamentos de Enfermería, Cultura Estado y Salud | UNLP Cs. Médicas; la tecnicatura/licenciatura tiene cursada masiva y tutoría sostenida en el ciclo inicial. |

## Carreras que quedaron afuera (y motivo para re-entrar después)
- **Farmacia y Bioquímica — UB** (exactas): quedó afuera por solapamiento importante de materias
  básicas con Medicina en el ciclo 1°–2° año (Química General/Orgánica, Bioquímica, Histología). Si el
  catálogo crece (V13+), es candidata de re-entrada con sus materias propias (Química Orgánica, Química
  Analítica, Farmacología básica).
- **Profesorado de Lengua/Literatura y de Historia**: representantes de la rama "profesorados" ya cubiertos
  por el Profesorado de Matemática (mismo esquema FaHCE-UNLP). Re-entrada natural con Gramática/Lingüística
  y con Historiografía cuando se abra el presupuesto.
- **Ciencias de la Educación**: ciclo básico casi enteramente de materias teórico-sociales (Filosofía,
  Historia de la Educación); demanda de tutoría menor y materias menos estandarizadas. Diferida.
- **Comunicación Social**: ciclo básico con perfil de taller (Periodismo, Semiótica, Análisis de los
  discursos); tutoría individual baja en 1°–2° año. Diferida.
- **Diseño Gráfico / Industrial**: ciclo inicial donde la carga es de taller y proyecto (Strength de
  la FADU); la tutoría se concentra solo en las materias técnicas (tipografía, ergonomía) que no justifican
  un bloque propio en este chunk. Diferida.
- **Notariado**: no es carrera autónoma en UBA; es una orientación del CPO de Abogacía (cubierta por la
  fila Abogacía en sus materias de ciclo básico).

> Decisión de variantes por carrera (regla "sin duplicar temas"): para las tres ingenierías se eligió
> una carrera por fila con **materias diferentes por carrera** (Civil = 1° año; Industrial = 2° año;
> Sistemas = materias de sistemas) en lugar de repetir el tronco común, porque el contrato persiste
> trayectos por `(nivel, carrera, materia)` y repetir AM I/Física I en las tres carreras duplicaría
> ~120 temas sin valor agregado para el Tutor.

## Totales
- Temas: **392**
- Carreras cubiertas: **13**
- Materias (trayectos): **49** (variable: 3–4 por carrera)