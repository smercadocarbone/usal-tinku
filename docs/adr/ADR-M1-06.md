# ADR-M1-06 — Especialidades del Tutor y verificación de credenciales por registro oficial

**Estado:** Aceptado (decisiones del dueño del producto, 2026-09-25). Implementación pendiente
(chunks E1–E4 de `docs/spikes/SPIKE-M1-M2-especialidad-y-credenciales.md`). Cambia BR-ID-01 y
el significado de la insignia "verificado".

## Contexto
Hoy la credencial académica es un archivo que el Admin aprueba a ojo (US-4, BR-ID-01), y
**una** credencial aprobada deja al Tutor "verificado" para **todas** sus materias. El spike
mostró que:
- el archivo no prueba nada;
- en Argentina lo que prueba un título es el **registro oficial**: Registro Público de Graduados
  Universitarios, ReFE/Mi Argentina con QR, validador de SIU-Guaraní, buscadores de colegios;
- ninguno de esos registros tiene API pública.

## Decisiones
1. **Especialidades con insignia por especialidad.** El Tutor declara 1 a 3 especialidades
   (materia + nivel máximo, del catálogo) además de sus temas. Cada una queda **verificada** (una
   credencial aprobada contra un registro oficial la cubre) o **declarada**. **Quien decide con
   quién aprender es el alumno o el Adulto Responsable**; las recomendaciones solo ayudan.
2. **Los Tutores con especialidades solo declaradas aparecen en el matching**, marcados como
   tales. Así arranca la plataforma: sin calificaciones ni historial.
   - **Las puntuaciones son un sistema de recomendación, no un aval.** Ordenan resultados
     combinando la similitud con lo que se busca y las calificaciones, y un Tutor nuevo arranca
     sin ellas.
   - Esto se declara en los Términos y en la tesis.
3. **Verificación = triaje automático + decisión del Admin contra el registro oficial.**

   **Triaje automático** (antes de la cola del Admin):
   - **Rechazo inmediato**, con motivo visible para el Tutor, de lo que claramente no sirve:
     formato inválido; imagen sin texto legible; documento que no es un título ni un certificado
     (por ejemplo, el DNI o una captura de otra cosa); enlace de verificación fuera de los
     dominios oficiales permitidos.
   - **Marca para el Admin** (no rechaza) cuando el nombre leído por OCR no coincide con el del
     DNI verificado del Tutor. El OCR de un título es menos confiable que el de un DNI y no debe
     rechazar solo.
   - Prellenado de institución, título y año por OCR, que el Tutor confirma.

   **Admin** (la tarjeta muestra DNI, datos y el paso a seguir según el tipo): consulta el
   registro oficial y aprueba indicando **cómo verificó** (registro oficial / solo documento) y
   **qué especialidades cubre**.

## Por qué no es 100 % automático
- **Qué automatiza cada parte.** La máquina puede decidir **qué no es** un título válido, que es
  la mayoría del ruido. No puede decidir **qué sí es** real: eso solo lo prueba el registro
  oficial.
- **Por qué no se automatiza la consulta al registro.** Los registros no tienen API, y scrapearlos
  es frágil y no está autorizado.
- **Resultado.** La automatización le ahorra tiempo al Admin (descarta lo inválido, prellena y le
  dice qué consultar), y la aprobación final sigue siendo de una persona.
- **Cuándo revisarlo.** Si algún registro publica una API o un QR verificable programáticamente, ese
  tipo puede pasar a aprobación automática con un nuevo ADR.

## Pendiente
- Texto de la cláusula de consentimiento para consultar registros con el DNI del Tutor
  (Ley 25.326), mismo mecanismo versionado que T08 (`identidad.aceptaciones_clausula`).
- Qué documentos se aceptan como respaldo de "primario". Decisión: un título secundario en papel
  sí, como **declarado con respaldo documental**, no como verificado.

## Consecuencias
- BR-ID-01 pasa de "lista cerrada, revisión manual estricta" a "tipos ampliados, triaje
  automático, decisión del Admin contra registro oficial". Se actualiza Spec M1 (US-4) al
  implementar.
- La insignia genérica "verificado" se reemplaza por insignias por especialidad (perfil público y
  resultados).
- Migración (nueva, nunca se edita una aplicada): las credenciales ya aprobadas quedan como
  "revisadas manualmente (solo documento)" hasta que el Admin las re-verifique contra el registro.
