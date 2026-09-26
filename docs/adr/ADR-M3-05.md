# ADR-M3-05 — El consentimiento de la grabación del resumen se da una vez, al aceptar los Términos

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-26). Modifica el punto 6
(Consentimiento, PT5) de ADR-M3-04. No cambia qué se graba ni cuándo (Artículo V, enmienda v2.4).

## Contexto
ADR-M3-04 pedía aceptar la cláusula de grabación de solo audio por separado:
- el Tutor, en "Mi perfil";
- el alumno, al contratar el adicional.

El dueño pidió que el consentimiento se dé **una sola vez**, al aceptar los Términos y Condiciones
al crear la cuenta. La cuenta no se crea sin esa aceptación.

## Decisión
1. El registro (adulto y Tutor) exige `aceptaTerminos: true` (`@AssertTrue`, 400 si falta). Sin
   aceptar, no se crea el usuario.
2. Al crear la cuenta se registran en `identidad.aceptaciones_clausula`, con la versión vigente de
   cada una, dos cláusulas:
   - `TERMINOS_Y_CONDICIONES`;
   - `GRABACION_AUDIO_RESUMEN`.
   Un Menor nunca acepta la grabación: no se registra ni en su alta ni si acepta Términos nuevos.
3. **El texto lo dice explícitamente.** El paso "Condiciones" del registro y la tarjeta "Las clases
   no se graban" lo dicen en claro: al aceptar se consiente, una vez, que en las clases entre
   adultos con el resumen contratado se grabe solo el audio, nunca con menores, y se borre al
   transcribir. El consentimiento no queda escondido en el texto legal.
4. **Desaparecen las aceptaciones separadas:**
   - la tarjeta "Resumen automático" de "Mi perfil" del Tutor;
   - la casilla de grabación al reservar.
5. **Versión nueva o cuenta anterior.** Si cambia la versión de los Términos
   (`TINKU_CLAUSULA_TERMINOS_VERSION`), o la cuenta es anterior a esta decisión, "Mi cuenta" pide
   aceptar los Términos actualizados una vez. `POST /api/usuarios/me/clausulas/TERMINOS_Y_CONDICIONES`
   registra las dos cláusulas.
6. **Controles del backend sin cambios.** Siguen las cuatro condiciones de ADR-M3-04 para grabar,
   incluido "los dos aceptaron la cláusula vigente". Ahora eso se cumple desde el alta.

## Riesgo aceptado (a validar con la asesoría legal)
- La Ley 25.326 pide consentimiento libre, expreso e informado.
- **Qué lo hace expreso e informado:**
  - se muestra aparte, en lenguaje claro;
  - la casilla es explícita;
  - el adicional es opcional y lo contrata el alumno clase por clase.
- **Qué puede objetarse:** atar el consentimiento a la creación de la cuenta.
- **Plan si la asesoría lo objeta:** volver a una aceptación separada para el adicional sin tocar
  el pipeline. Es la misma tabla y la misma cláusula, solo cambia dónde se pide.
