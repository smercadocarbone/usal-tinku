# ADR-M1-09 — Video de presentación del Tutor, subido a Tinku y moderado

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-26; D-7 de
`docs/superpowers/plans/2026-09-26-mejoras-tutor-alumno.md`).

## Contexto
Antes de pagar, el alumno o la familia quiere ver al Tutor y cómo explica. El perfil ya tiene
foto y presentación escrita (U1, FR-ID-027..029).

## Opciones
1. **Link a YouTube o Drive.** Sin costo de almacenamiento, pero abre un canal de contacto fuera
   de la plataforma: descripción, comentarios, otros videos o el canal. Tampoco se puede moderar,
   porque el contenido cambia después de aprobado. Descartada.
2. **Video subido a Tinku y moderado.** Elegida.

## Decisión
1. **El video.**
   - Uno por Tutor, MP4 o WebM, hasta 90 segundos y 30 MB.
   - Se guarda con el mismo puerto `Almacenamiento` que la foto y las credenciales.
   - El tipo se valida por los primeros bytes; la duración la mide el navegador y el backend
     acota el tamaño.
2. **Moderación.**
   - Queda `pendiente` hasta que un Admin de Moderación y Seguridad lo aprueba o lo rechaza con
     motivo.
   - Solo `aprobado` se ve en el perfil público.
   - Reemplazarlo vuelve a `pendiente`: nunca se publica algo que no se revisó.
3. **Minimización (Art. V).**
   - Es contenido que el propio Tutor, adulto, publica con una única finalidad: presentarse.
   - Se borra al reemplazarlo, al borrarlo o con la baja de la cuenta.
   - **No es la grabación de una clase:** la prohibición de grabar clases (Art. V) y la de grabar
     a un Menor (Art. II) no cambian.
4. **Límite de subida.**
   - El límite global de multipart sube a 30 MB.
   - Las subidas existentes (credencial, CAP, foto, evidencia) pasan a validar su propio límite de
     5 MB en código, porque hoy dependían del límite global (AUD-007).

## Consecuencias
- Costo de almacenamiento: ~30 MB por Tutor en el peor caso. Con 1.000 Tutores son ~30 GB, dentro
  del disco del servidor actual. Si crece, este puerto se muda a object storage sin tocar el
  contrato.
- Una cola más para el Admin.
