# SPIKE — Especialidad del Tutor y verificación de títulos (M1 + M2)

**Fecha:** 2026-09-25 · **Estado:** investigación cerrada. **Decidido en ADR-M1-06** (1: sí; 2: sí, marcados; 3: triaje automático + Admin contra registro). Queda pendiente la cláusula (4).
**Preguntas:**
1. ¿Cuál es la mejor forma de que el Tutor declare su especialidad?
2. ¿Se pueden subir títulos secundarios, universitarios, docentes y otros? ¿Se verifican bien, sin que se pueda subir cualquier cosa?
3. ¿Cómo se verifica cada tipo en Argentina?
4. ¿Cómo se muestra "verificado"? ¿Hoy es manual?

---

## 1. Estado actual (verificado contra el código, no contra los javadocs)

**Especialidad.** El Tutor elige **temas del catálogo** (`matching.temas`: nivel → año/carrera →
materia → tema, sembrado desde los NAP y los planes de estudio en `docs/catalogo_seed/`) y escribe
una **bio libre**. El matching semántico usa esos temas (A1) y la bio. No existe un concepto de
"especialidad principal" ni de nivel máximo que puede enseñar.

**Credencial académica** (`identidad`, US-4, BR-ID-01 "lista cerrada, revisión manual estricta"):

| Aspecto | Hoy | Problema |
|---|---|---|
| Tipos | `TITULO`, `CERTIFICADO_ANALITICO`, `MATRICULA` | No distingue secundario, universitario, profesorado, alumno regular ni matrícula docente |
| Datos que se piden | Solo el archivo | No se pide institución, título, año, ni código de verificación |
| Control del archivo | PDF, PNG o JPG por magic bytes, ≤ 5 MB (AUD-007) | Bien contra archivos maliciosos; no dice nada de si el título es real |
| Verificación | **100 % manual**: el Admin mira el archivo y aprueba o rechaza | Sin guía, sin consulta a registros oficiales |
| Lo que ve el Admin | Nombre del Tutor, tipo, intento, el archivo | **No ve el DNI del Tutor**, que es lo que piden los registros oficiales para consultar |
| Nombre del título vs. identidad | No se compara | Se puede subir el título **de otra persona**: si el Admin no mira el nombre, pasa |
| Motivo de rechazo | No se registra | El Tutor no sabe qué corregir |
| Efecto de aprobar | `activoParaMatching = true` + insignia **"verificado"** genérica | **Una** credencial aprobada "verifica" **todas** las materias: un título secundario deja al Tutor como "verificado" dando Análisis Matemático II |

**Respuesta corta a la pregunta 4:** sí, hoy es **manual y sin método**. Se puede subir cualquier
imagen o PDF con aspecto de título. Solo lo frena que el Admin se dé cuenta a ojo. Y la insignia
"verificado" no dice **qué** se verificó.

## 2. Cómo se verifica cada tipo en Argentina

La regla que sale de la investigación: **el archivo no prueba nada, el registro oficial sí**.
Un PDF se falsifica en minutos. Lo que no se puede falsificar es la consulta al registro del
Estado o de la institución.

| Tipo | Registro / mecanismo oficial | Qué hace falta para consultar | ¿Automatizable? |
|---|---|---|---|
| **Universitario** (egresados desde 02/01/2012, incluye extranjeros convalidados) | **Registro Público de Graduados Universitarios** (SIU / DNGU), `registrograduados.siu.edu.ar`. Devuelve nombre, DNI, título e institución. Los títulos legalizados por SIDCER se registran además en Blockchain Federal Argentina | Nombre, apellido y DNI | **No hay API pública.** Es una app SIU-Toba con parámetros cifrados; scrapearla es frágil y no está autorizado → **consulta asistida por el Admin** |
| **Universitario anterior a 2012** | No está en el registro público | Legalización del Ministerio (sello o SIDCER) o constancia de la universidad | Manual |
| **Secundario y superior no universitario digital** (emitidos desde 01/11/2023) | **ReFE** (Registro Federal de Egreso, Res. CFE 440/23), en **Mi Argentina** con **QR** | El QR del título | **Semi**: el Tutor comparte el QR o el enlace y el Admin lo abre y lo compara |
| **Secundario en papel** (antes de 11/2023) | Sin registro público consultable. La validez nacional la informa la Dirección de Validez Nacional por mail, no el egreso de una persona | Copia legalizada | **No verificable online.** Se acepta como "declarado", no como "verificado" |
| **Profesorado / título docente** (Institutos de Formación Docente) | Si es digital desde 11/2023, **ReFE/Mi Argentina con QR**. Si no, la validez nacional del plan en el **ReNaV** / Registro Federal de Instituciones y Ofertas de Formación Docente (INFoD) | QR, o institución + denominación + cohorte | Semi (QR) o manual |
| **Estudiante universitario en curso** (no tiene título todavía) | **Certificado de alumno regular / analítico parcial de SIU-Guaraní**, con **código o QR de validación** en el propio sistema de la universidad | El código o QR del certificado | Semi: el Admin valida el código en el Guaraní de esa universidad |
| **Matrícula profesional** | Buscador de cada **colegio o consejo profesional** (por matrícula, DNI o nombre). Salud: **REFEPS/SISA**, nacional | Matrícula o DNI | Semi / manual, un buscador por colegio |

**Datos personales (Ley 25.326):** consultar un registro con el DNI del Tutor es tratar sus datos
con una finalidad nueva. Hace falta su **consentimiento expreso** al cargar la credencial. Es el
mismo patrón que la cláusula versionada de T08 (`identidad.aceptaciones_clausula`).

## 3. Especialidad: alternativas evaluadas

Criterio del proyecto: 1 desarrollador, USD 0-100/mes, lo más simple que cumpla (AGENTS §1.4).

| Opción | Cómo funciona | A favor | En contra |
|---|---|---|---|
| **A. Solo catálogo de temas (hoy)** | El Tutor tilda temas | Ya existe, alimenta el matching | Da una lista larga sin jerarquía; no hay "en qué es fuerte"; nada lo vincula a la formación |
| **B. Texto libre + IA** | Escribe su especialidad y un LLM la mapea al catálogo | Cómodo para el Tutor | Cuesta dinero por uso, es inexacto y no verifica nada |
| **C. Especialidad declarada + respaldada por credencial** *(recomendada)* | Elige 1 a 3 **especialidades** (materia + nivel máximo, del catálogo) y las **ata a una credencial** que las respalda. La insignia es **por especialidad** | Honesta ("Profesor de Matemática, verificado" ≠ "Matemática, sin verificar"); cero costo; reusa catálogo y matching | Hay que definir qué credencial respalda qué nivel (tabla simple, §4.3) |
| **D. Examen por materia** (como Wyzant) | Quiz por materia para habilitarla | Prueba conocimiento real | Hay que escribir y mantener bancos de preguntas por materia y nivel; fuera del alcance de 1 persona |

Referencia de mercado: **Preply** revisa a mano cada certificado y separa la insignia de
"credenciales" de la de "desempeño"; **Wyzant** exige un examen por materia. Tinku ya tiene
reputación (M7) para el desempeño; le falta la mitad de "credenciales, por especialidad".

## 4. Recomendación

### 4.1 Especialidad (opción C)
- El Tutor mantiene los **temas** (para el matching) y agrega **1 a 3 especialidades**: materia +
  nivel máximo (`PRIMARIO`, `SECUNDARIO`, `UNIVERSITARIO`), elegidas del catálogo.
- Cada especialidad tiene estado: **"verificada"** (respaldada por una credencial aprobada que la
  cubre) o **"declarada"**.
- El perfil público y los resultados muestran, por ejemplo, **"Matemática · secundario ✓ Profesorado
  verificado"**, en vez del "verificado" genérico.

### 4.2 Credenciales: datos estructurados y verificación contra el registro
**Nuevos tipos** (reemplazan la lista actual; migración nueva, nunca editar la V existente):
`SECUNDARIO`, `TERCIARIO_NO_DOCENTE`, `PROFESORADO`, `UNIVERSITARIO`, `ALUMNO_UNIVERSITARIO`,
`MATRICULA_PROFESIONAL`, `OTRO`.

**Datos que se piden además del archivo:**
- institución, denominación del título y año de egreso (o de cursada);
- **código o enlace de verificación**: obligatorio si el título es digital o si es un certificado de
  alumno regular; opcional en papel.

**Controles automáticos** (antes de que llegue al Admin):
1. Archivo: los de hoy (magic bytes, tamaño).
2. **Nombre del titular = nombre del DNI verificado del Tutor**: OCR del documento con el Tesseract
   que ya existe y la misma tolerancia de nombre que en el registro. Si no coincide, **no se
   rechaza**, se marca "nombre no coincide" para el Admin, porque el OCR de un título es menos
   confiable que el de un DNI.
3. Si trae enlace de verificación: tiene que ser **https** y de un **dominio permitido**
   (`registrograduados.siu.edu.ar`, `argentina.gob.ar` / Mi Argentina, dominios `.edu.ar`). El
   sistema **no** lo scrapea: lo muestra al Admin para abrir con un clic.

**Revisión del Admin (asistida):**
- La tarjeta muestra **DNI, nombre del Tutor, datos declarados y el paso a seguir según el tipo**:
  - universitario desde 2012 → "consultá el Registro Público de Graduados con este DNI";
  - digital → "abrí el QR o enlace y compará nombre y DNI";
  - alumno regular → "validá el código en el Guaraní de la universidad";
  - matrícula → buscador del colegio.
- Al aprobar, el Admin elige **cómo lo verificó**:
  - **"Registro oficial"** → especialidad **verificada**;
  - **"Solo documento"** (papel sin registro consultable) → queda **declarada con respaldo
    documental**, sin la insignia fuerte.
- **Rechazo con motivo** (lista cerrada + texto), visible para el Tutor.

### 4.3 Qué credencial respalda qué especialidad (tabla inicial, ajustable)
| Credencial | Respalda hasta |
|---|---|
| `SECUNDARIO` | Primario |
| `ALUMNO_UNIVERSITARIO` (en carreras afines) | Secundario en materias afines |
| `PROFESORADO` en X | X hasta secundario (el profesorado primario: primario) |
| `UNIVERSITARIO` en X / `MATRICULA_PROFESIONAL` | X hasta universitario |

"Materia afín" la decide el Admin al aprobar: elige qué especialidades del Tutor cubre esa
credencial. No hace falta automatizarlo.

## 5. Qué NO se recomienda
- **Scrapear** el Registro de Graduados u otros sitios oficiales: sin API, con parámetros
  cifrados, términos de uso no pensados para eso, y se rompe con cualquier cambio.
- **Pagar un servicio de verificación de antecedentes académicos**: va contra el presupuesto, y no
  cubre mejor que el registro público gratuito.
- **Exámenes por materia** (opción D) en el MVP.

## 6. Decisiones pendientes (del dueño del producto)
1. ¿Se adopta la opción C (especialidades con insignia por especialidad)?
2. ¿Un Tutor con especialidades solo "declaradas" puede aparecer en el matching (con la marca), o
   hace falta al menos una verificada? Hoy aprobar cualquier credencial lo activa.
3. ¿Se aceptan títulos secundarios en papel (no verificables) como respaldo para dar primario?
4. Texto de la cláusula de consentimiento para consultar registros con el DNI (asesoría legal,
   como la de T08).

## 7. Implementación estimada si se aprueba (chunks)
| Chunk | Contenido | Tamaño |
|---|---|---|
| E1 | Migración: especialidades del Tutor + nuevos tipos y campos de credencial + motivo de rechazo + "método de verificación". Spec M1 (US-4, BR-ID-01) y Spec M2 actualizados | M |
| E2 | Backend: carga estructurada, OCR de nombre, allowlist de enlaces, cola de Admin con DNI + guía por tipo, aprobación que elige especialidades cubiertas | M |
| E3 | Frontend: onboarding del Tutor (especialidades + credencial guiada por tipo), tarjeta del Admin, insignias por especialidad en perfil y resultados | M |
| E4 | Tests de integración por tipo + casos borde (nombre no coincide, enlace fuera de allowlist, credencial que no cubre la especialidad) | S |

Requiere **ADR-M1-06** (cambia BR-ID-01 y la semántica de "verificado") y la cláusula de
consentimiento. No toca la Constitución (Art. I ya exige verificación documental; esto la
refuerza).

## 8. Fuentes
- [Registro Público de Graduados Universitarios (SIU)](https://registrograduados.siu.edu.ar/)
- [Argentina.gob.ar: Registro Público de Graduados](https://www.argentina.gob.ar/noticias/registro-publico-de-graduados-se-podra-chequear-la-veracidad-de-los-titulos-universitarios)
- [Registros Públicos (DNGU)](https://www.argentina.gob.ar/educacion/universidades/direccion-nacional-de-gestion-universitaria/registros-publicos)
- [Registro Federal de Egreso (ReFE)](https://www.argentina.gob.ar/educacion/direccion-de-validez-nacional-de-titulos-y-estudios/registro-federal-de-egreso-refe)
- [Títulos secundarios y superiores en Mi Argentina](https://www.argentina.gob.ar/noticias/se-podra-acceder-al-titulo-de-educacion-secundaria-y-superior-no-universitaria-desde-la-app)
- [Mi Argentina: verificación con QR (iProfesional)](https://www.iprofesional.com/tecnologia/420861-mi-argentina-lanza-la-verificacion-digital-de-titulos-con-codigo-qr-asi-funciona)
- [ReNaV](https://www.argentina.gob.ar/educacion/validez-titulos/renav)
- [Consultar validez nacional de un título](https://www.argentina.gob.ar/servicio/consultar-si-un-titulo-o-certificado-cuenta-con-validez-nacional)
- [Registro Federal de títulos docentes (INFoD, Entre Ríos)](https://des-ers.infd.edu.ar/sitio/registro-federal-y-validez-de-titulos/)
- [Blockchain Federal Argentina: títulos académicos](https://bfa.ar/blockchain/casos-de-uso/titulos-academicos)
- [SIDCER (DNGU)](https://sicer.educacion.gob.ar/sidcer/index.html)
- [SIU-Guaraní: constancias con validación](https://documentacion.siu.edu.ar/wiki/SIU-Guarani/Version3.19.0/Documentacion_de_Autogestion/Solicitar_constancias_y_certificados)
- [Certificado de alumno regular con QR (UNT)](https://filo.unt.edu.ar/2018/05/21/certificado-alumno-regular-en-siu-guarani/)
- [REFEPS/SISA: buscador de profesionales de la salud](https://sisa.msal.gov.ar/sisadoc/docs/050102/refeps_buscador_publico_profesionales.jsp)
- [Consulta de matrículas de la salud (PBA)](https://www.gba.gob.ar/saludprovincia/consultas/consulta_de_profesionales_y_t%C3%A9cnicos_matriculados_en_la_provincia_de_buenos_aires)
- [Wyzant: materias y quizzes](https://support.wyzant.com/tutors/subjects/tutoring-subjects-faqs/)
- [Preply: Professional Tutor](https://help.preply.com/en/articles/9795272-what-is-a-professional-tutor)
