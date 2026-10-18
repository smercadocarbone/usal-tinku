# Cambios propuestos a la tesis: evaluación sin piloto

**Fecha:** 2026-10-18 · **Tarea:** T-TES-11b · **Spec:** `docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md`
**Repositorio de la tesis:** `usal-tinku-docs` (no está en este repo; las citas `NN:línea` son las de
`DESALINEACIONES_tesis_codigo.md`, contra `b3a21a4`). **Este documento propone; no modifica la tesis.**
El dueño revisa cada bloque y lo pega.

**Decisión del dueño (2026-10-18):** el piloto con usuarios reales del 27/10/2026 no se hace. La
hipótesis se valida con (1) una evaluación técnica reproducible sobre el sistema real y (2) sesiones
moderadas con 5 a 8 personas adultas sobre la demo pública, con datos de prueba y sin dinero real.

Los bloques `latex` están escritos para pegar en los `.tex` (con `\%` escapado). Donde hay un
**[COMPLETAR]**, el dato sale de las sesiones moderadas o del CI y todavía no existe: no se inventa.

---

## Capítulo 1 — Introducción: alcance y limitaciones

**Qué cambia:** `01-introduccion.tex:170-172` y `:328-330` dicen que el sistema se valida con un piloto
"con alumnos reales" y con público desde primaria; las limitaciones (`:380-383`) no mencionan que no
hay menores en la validación (desalineación A-07).

Texto sugerido para reemplazar la mención del piloto (`:170-172` y `:328-330`):

```latex
La validación del sistema se realiza mediante una evaluación técnica reproducible sobre la
plataforma desplegada y mediante sesiones moderadas con usuarios adultos, en las que cada
participante recorre el circuito completo (registro, búsqueda, reserva, pago de prueba, clase y
calificación) sobre una versión de demostración con datos de prueba y sin transacciones reales.
```

Texto sugerido para agregar a las limitaciones (`:380-383`):

```latex
\item \textbf{Validación sin usuarios reales en operación.} No se realizó un piloto con alumnos y
tutores reales. La evaluación con usuarios se limita a entre cinco y ocho sesiones moderadas con
personas adultas, sobre datos de prueba y con pagos simulados; sus resultados son evidencia
exploratoria y no permiten estimar indicadores de operación como la recompra o el volumen anual de
sesiones.
\item \textbf{Sin menores de edad en la validación.} Aunque el sistema está diseñado para alumnos
desde el nivel primario, las clases con menores permanecen deshabilitadas hasta completar los
controles de seguridad previstos (verificación de antecedentes del tutor y monitoreo en el cliente).
Ninguna persona menor de edad participó de la evaluación.
\item \textbf{Sin transacciones reales.} Los pagos de la evaluación se hacen en modo de prueba
(\emph{bypass}); la comisión de la plataforma no se validó con cobros reales.
```

## Capítulo 2 — Hipótesis e indicadores

**Qué cambia:** la tabla de indicadores (`02:128-129`) supone un piloto. Se mantienen los cuatro
indicadores con el mismo sentido; cambia la fuente y el umbral. (Resuelve también B-09 si el
capítulo 7 se reescribe con estos mismos nombres; ver más abajo.)

```latex
\begin{table}[h]
\centering
\small
\begin{tabular}{p{1cm}p{4cm}p{5cm}p{2.5cm}}
\hline
\textbf{ID} & \textbf{Indicador} & \textbf{Cómo se mide} & \textbf{Umbral} \\
\hline
I-01 & Aceptación de las sugerencias del motor de coincidencias &
(a) Evaluación offline con 120 búsquedas etiquetadas: MRR y \emph{recall@5}.
(b) Sesiones moderadas: búsquedas con resultados en las que el usuario abrió el perfil o reservó
a un tutor de los cinco primeros, sobre el total de búsquedas con resultados. &
(a) MRR $\geq$ 0,80 y \emph{recall@5} $\geq$ 0,60; (b) $>$ 60\,\% \\
I-02 & Calidad percibida de la sesión & Calificación de 1 a 5 del alumno al tutor en las clases de
las sesiones moderadas & $\geq$ 4,0 \\
I-03 & Alcance geográfico & Provincia declarada por los participantes en el cuestionario & Al menos
una clase entre provincias distintas, una del interior \\
I-04 & Intención de recomendación (NPS) & Pregunta de 0 a 10, una vez por persona, después de su
primera clase & Positivo \\
\hline
\end{tabular}
\caption{Indicadores de la hipótesis y su medición sin piloto}
\end{table}
```

Párrafo sugerido a continuación de la tabla:

```latex
Los indicadores de operación previstos originalmente para un piloto ---la recompra dentro de la
plataforma (R-02) y el volumen anualizado de sesiones (R-04)--- requieren usuarios que vuelvan a
usar el sistema durante semanas y no pueden inferirse de sesiones moderadas; se presentan como
trabajo futuro (Capítulo~8). El supuesto de adopción del adicional de resumen (S-04) ya no forma
parte del modelo económico: el adicional no entra en el flujo de fondos.
```

**Entrevistas (B-10):** `02:150-155` promete dos tutores y cuatro familias, y el apéndice B dice "Sprint 3"
con dos especialistas, todavía pendientes. Propuesta: que las sesiones moderadas sean el instrumento con
usuarios, y que el **apéndice B** contenga su protocolo (consentimiento, guion de tareas, cuestionario
SUS y NPS) copiado de T11b §2. Si el dueño igual hace las entrevistas, se reportan aparte; si no, quitar
la promesa de `02:150-155`:

```latex
La validación con usuarios se realiza mediante sesiones moderadas de tareas (Apéndice~B): cada
participante adulto completa un guion de tareas sobre la plataforma mientras piensa en voz alta, y al
final responde el cuestionario de usabilidad SUS y la pregunta de recomendación.
```

## Capítulo 6 — Riesgos

**Qué cambia:** la matriz es la de la plantilla (D-02, D-03). La rama sin mergear
`revision/encuesta-modelo-riesgos` ata R-11 y R-15 al piloto (`DESALINEACIONES` §D, rama).

- **R-15 ("migración a VPS antes del piloto"):** ya no hay piloto y la migración ya está hecha
  (`RUNBOOK_produccion.md`). Propuesta: cerrar el riesgo o reformularlo como continuidad de la demo.

```latex
\textbf{R-15 --- Disponibilidad de la demostración durante la evaluación.} La plataforma corre en un
servidor propio con Coolify. Un corte durante una sesión moderada invalida esa sesión.
\emph{Mitigación:} las sesiones se agendan fuera de las ventanas de despliegue, se verifica el estado
del sistema antes de cada una y se registra la disponibilidad del período en el panel de Coolify.
```

- **R-11 (MercadoPago "solo probado con usuarios de prueba"):** el 2026-10-14 producción estaba en cobro
  real (`HALLAZGOS_pruebas.md` §7), pero la evaluación no usa dinero real. Propuesta:

```latex
\textbf{R-11 --- Cobros reales no validados con usuarios.} La integración con MercadoPago está
implementada y desplegada, pero la evaluación usa pagos simulados. Los errores que solo aparecen con
cobros reales (rechazos, contracargos, demoras de acreditación) no se observan.
\emph{Mitigación:} el modo de prueba usa el mismo circuito de confirmación y de retención de fondos
que el cobro real; la conciliación y los reintentos están cubiertos por pruebas automáticas. La
validación con cobros reales queda como trabajo futuro.
```

- **Riesgo nuevo — validez de la evaluación** (R-16 y R-17 son números sugeridos: ajustarlos a la
  numeración final de la matriz):

```latex
\textbf{R-16 --- Validez limitada de la evaluación con usuarios.} Con entre cinco y ocho
participantes elegidos por conveniencia, los resultados no son representativos de la población
objetivo. \emph{Mitigación:} los indicadores de calidad del motor de coincidencias se apoyan además en
una evaluación offline con 120 búsquedas etiquetadas, y los resultados con usuarios se presentan como
evidencia exploratoria, sin generalizar.
```

- **Riesgo nuevo — calibraciones sin datos reales:** umbral de relevancia, peso de la reputación,
  comisión, tasa de DNI ilegible y latencia del clasificador en un celular real se fijaron sin datos de
  operación (ver las notas fechadas 2026-10-18 en los ADR correspondientes).

```latex
\textbf{R-17 --- Parámetros calibrados sin datos de operación.} El umbral de relevancia de la
búsqueda, el peso de la reputación en el ranking, la comisión y el umbral del clasificador de
contenido se fijaron con datos de prueba. \emph{Mitigación:} todos son configuración (no código), cada
uno tiene documentado el disparador para recalibrarlo y la evaluación offline del motor de
coincidencias puede repetirse con un solo comando.
```

## Capítulo 7 — Resultados

**Qué cambia:** el capítulo es la plantilla (D-01), usa I-01/I-02 con otro significado (B-09: latencia
p95 y F1), trae RNF de ejemplo (D-05: p95 < 5 s, F1 ≥ 0,70) y una fila de feedback inventada (D-06).
Propuesta de estructura, con lo que **ya se puede reportar** y lo que sale de las sesiones:

### 7.1 Calidad del software (ya reportable, salvo los números del CI)

```latex
\section{Calidad del software}
La auditoría técnica independiente del 21 de septiembre de 2026 registró 36 hallazgos, de los cuales
los 36 figuran cerrados, cada uno con un commit y una prueba de regresión que fallaba antes de la
corrección. La suite de pruebas del backend tiene [COMPLETAR: cantidad] pruebas sin fallas, con una
cobertura de líneas de [COMPLETAR]\,\% medida con JaCoCo (último run de integración continua de la
rama principal, [COMPLETAR: fecha y commit]).
```

> Dato de referencia, **no** para pegar como resultado final: `AGENTS.md` §5 registra 661 tests y 85,6 %
> de cobertura al 2026-10-14. El número final se copia del CI el día que se cierre el capítulo.

### 7.2 Motor de coincidencias (ya reportable)

```latex
\section{Motor de coincidencias}
Se construyó un conjunto de 120 búsquedas en lenguaje natural: 85 con al menos un tutor del conjunto de
prueba que las cubre (etiquetadas leyendo el catálogo de temas, sin usar el modelo), 20 pedidos con
sentido que ningún tutor cubre y 15 textos sin sentido. Sobre los 37 tutores de prueba, con el umbral
de relevancia vigente (0,46), el primer resultado es pertinente en el 88,2\,\% de las búsquedas
relevantes (precisión@1 = 0,882; MRR = 0,920) y, en promedio, el 94,9\,\% de los tutores relevantes
de cada búsqueda figura entre los cinco primeros
(\emph{recall@5} = 0,949); todas las búsquedas relevantes devolvieron al menos un resultado. En
cambio, el 40\,\% de los pedidos irrelevantes y el 20\,\% de los textos sin sentido también
devolvieron resultados, por debajo de las metas (80\,\% y 100\,\% sin resultados). Un barrido del
umbral muestra que 0,54 cumple las cinco metas (MRR = 0,900, \emph{recall@5} = 0,913, 97,6\,\% de
búsquedas relevantes con resultado, 95\,\% de irrelevantes y 100\,\% de textos sin sentido sin
resultados).
```

> Si el dueño cambia el umbral a 0,54 antes de cerrar el capítulo, reemplazar el párrafo por los valores
> de esa fila (y repetir el script). Fuente: `docs/operacion/EVALUACION_tecnica.md` §1.a.

### 7.3 Rendimiento (ya reportable, con su alcance)

```latex
\section{Rendimiento}
Con cinco usuarios concurrentes y cien solicitudes por operación, en un entorno local con los datos
de prueba, el percentil 95 de la latencia fue de 360\,ms para la búsqueda (incluye el cálculo de
similitud semántica en CPU), 74\,ms para crear una reserva, 195\,ms para confirmar un pago en modo de
prueba y 69\,ms para obtener el acceso a la sala de clase, sin errores. La medición no incluye la
latencia de red hasta el servidor desplegado.
```

> **[COMPLETAR, opcional]:** repetir contra el entorno desplegado (comando en `EVALUACION_tecnica.md` §1.b)
> y reemplazar la cifra. La disponibilidad (RNF-01 de T13) se reporta con el panel de Coolify del período
> de las sesiones.

### 7.4 Seguridad del menor: corte automático (ya reportable)

```latex
\section{Corte automático de la clase}
Las pruebas de extremo a extremo contra el servidor de video real (nueve casos) verificaron las dos
ramas del corte ---con un menor presente la clase se corta sin preguntar; entre adultos se pregunta
antes de cortar--- y el comportamiento cuando el clasificador no carga. En una máquina sin GPU con la CPU frenada cuatro veces (perfil de gama media de
Lighthouse), el clasificador tardó 609\,ms por imagen en el percentil 95, lo que da un tiempo de
detección estimado de 1,8\,s con una cámara, dentro del objetivo de 3\,s; el ciclo no perdió turnos
ni memoria. Falta la medición en un teléfono físico de gama media.
```

### 7.5 Sesiones moderadas con usuarios (sale de las sesiones)

```latex
\section{Sesiones moderadas con usuarios}
Participaron [COMPLETAR: n] personas adultas de [COMPLETAR: k] provincias, [COMPLETAR] en el rol de
tutor y [COMPLETAR] en el de alumno o adulto responsable. La Tabla~\ref{tab:tareas} resume la tasa de
éxito sin ayuda y el tiempo mediano de cada tarea. El puntaje SUS promedio fue [COMPLETAR] (desvío
[COMPLETAR]), [COMPLETAR: por encima / por debajo] del promedio de referencia de 68. El NPS de
intención fue [COMPLETAR] ([COMPLETAR] promotores, [COMPLETAR] pasivos y [COMPLETAR] detractores).

En cuanto a los indicadores de la hipótesis: la aceptación de sugerencias (I-01b) fue [COMPLETAR]\,\%
de [COMPLETAR] búsquedas con resultados; la calificación promedio de las clases (I-02) fue
[COMPLETAR] sobre 5 en [COMPLETAR] calificaciones; y hubo [COMPLETAR] clases entre participantes de
provincias distintas, [COMPLETAR: con / sin] al menos uno del interior (I-03).
```

Tabla de tareas (rellenar desde la planilla de T11b §2.7): T1–T7 y U1–U6, con "éxito sin ayuda (n/N)" y
"tiempo mediano (s)".

### 7.6 Discusión y amenazas a la validez

```latex
\section{Amenazas a la validez}
La muestra de usuarios es chica y de conveniencia; los participantes usaron cuentas y datos de prueba,
con pagos simulados y sin menores de edad, y sabían que estaban siendo observados. El conjunto de
búsquedas de la evaluación offline lo armó el propio autor, con un criterio documentado, y se evaluó
contra los tutores de prueba y no contra una oferta real. Los resultados sostienen la viabilidad
técnica y una buena experiencia inicial, pero no permiten afirmar la adopción ni la recompra en
operación.
```

**Además:** quitar la fila de feedback inventada (D-06, `07:103`) y reemplazar los RNF de ejemplo de
`07:61-62` (D-05) por los de la Constitución (disponibilidad 95 % mensual, latencia de videollamada
< 200 ms) y los umbrales de T11b §1.

## Capítulo 8 — Conclusiones y trabajo futuro

**Qué cambia:** `08-conclusiones.tex:50-51` declara OE-1 "Alcanzado" citando un capítulo vacío (D-04).

Propuesta: declarar cada objetivo según la evidencia del capítulo 7, sin "alcanzado" hasta tener los
números. Texto base para OE-1 (ajustar al enunciado real del objetivo):

```latex
El objetivo específico OE-1 se considera [COMPLETAR: alcanzado / alcanzado parcialmente]: el motor de
coincidencias ordena primero un tutor pertinente en la mayoría de las búsquedas (MRR = 0,920 sobre 120
búsquedas etiquetadas) y [COMPLETAR: resultado de I-01b en las sesiones moderadas]. La principal
debilidad observada es que el umbral de relevancia vigente deja pasar resultados para pedidos que
ningún tutor cubre, lo que se corrige ajustando un parámetro de configuración.
```

Trabajo futuro sugerido:

```latex
\begin{itemize}
\item \textbf{Piloto con usuarios reales.} Medir en operación la recompra (R-02) y el volumen anual
de sesiones (R-04), que no pueden estimarse con sesiones moderadas.
\item \textbf{Clases con menores.} Habilitarlas cuando se cumplan los controles previstos y medir la
latencia del clasificador en teléfonos de gama media y la tasa de cortes revisados.
\item \textbf{Recalibraciones con datos de operación.} Peso de la reputación en el ranking (a partir de
50 calificaciones reales), umbral de relevancia con búsquedas reales, comisión de la plataforma (tras
un trimestre de cobros reales) y tasa de documentos ilegibles en el registro.
\item \textbf{Cobros reales.} Validar con usuarios el circuito completo de MercadoPago, incluidos
rechazos y contracargos.
\end{itemize}
```

---

## Qué tiene que hacer el dueño

1. Revisar y pegar cada bloque en `usal-tinku-docs` (capítulos 1, 2, 6, 7, 8 y apéndice B).
2. Decidir el umbral de relevancia (0,46 vigente o 0,54) antes de cerrar 7.2.
3. Conducir las sesiones moderadas (T11b §2) y completar los **[COMPLETAR]** de 7.5 y 8.
4. Copiar del CI de `main` los números de 7.1.
5. Actualizar `DESALINEACIONES_tesis_codigo.md` (A-07, B-09, B-10, D-01, D-04, D-05, D-06 y la rama) cuando
   los cambios estén en la tesis: hoy quedan marcadas como "propuesta en `TESIS_sin_piloto.md`".
