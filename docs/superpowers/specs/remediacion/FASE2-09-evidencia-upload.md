# FASE2-09 — Evidencia del kill-switch: upload propio, acceso acotado y borrado al vencer (AUD-021)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** medio (evidencia de seguridad con menores) ·
**Finding:** AUD-021 · **Decisión:** D2-bis.

## 1. Problema (verificado al 2026-09-22)

1. `aula/SesionService.subirEvidencia(usuario, sesionId, clipUrl, duracionSegundos)` acepta
   **cualquier URL `http://` o `https://` que mande el cliente** y la guarda en
   `AlertaSeguridad.clipUrl`. Esa "evidencia" es lo que un Admin abre para decidir si sanciona a
   alguien: un link controlado por un atacante es **phishing dirigido al Admin**, y `http://` viola
   el requisito de TLS del proyecto.
2. **Nadie borra el clip al vencer la retención.** `AlertaSeguridadService.resolver` completa
   `clipRetencionHasta` (hoy `RETENCION_CLIP = 30 días`, constante en código), pero no hay ningún
   job que borre el clip en esa fecha. El Artículo V exige minimización real, no una fecha de
   vencimiento decorativa.
3. `AlertaSeguridadResponse` expone datos de la Alerta al panel. Verificá con `rg -n "clipUrl"` que
   la referencia interna del almacenamiento **no** salga en ninguna respuesta HTTP.

Contexto: hoy **ningún cliente produce el clip** (T-M3-06, el clasificador on-device, sigue sin
implementar). Esta tarea deja el backend listo y seguro para cuando exista.

## 2. Decisiones

- El clip se **sube como archivo** por el puerto `identidad/port/Almacenamiento` (el mismo de las
  credenciales, que desde AUD-007 tiene `leer` con defensa contra path traversal). Se elimina la
  opción de URL declarada.
- **D2-bis:** el Admin de Moderación ve el clip siempre. El **Adulto Responsable** del menor de esa
  sesión lo ve **solo si la Alerta se resolvió `SANCIONAR`** (`RESUELTA_BAJA`). Con `REACTIVAR`
  (falso positivo), nunca. Nadie más.
- Al vencer `clipRetencionHasta`, un job de **Quartz persistido** (A4) borra el archivo y deja
  `clipUrl = null`.

## 3. PARAR si…

- **Retención:** verificá que BR-KS-02 (en `docs/specs/Spec_M3_Aula_Virtual.md` o en la
  Constitución) fija **30 días desde la resolución**. Si lo fija, agregá la fila "Retención del clip
  de evidencia del kill-switch — 30 días desde la resolución de la Alerta" a la Tabla de Tiempos y
  hacé que `RETENCION_CLIP` salga de configuración con ese default. Si BR-KS-02 **no** fija ese
  número, **PARAR** (A3).
- **Casos escalados:** `Spec_M9` dice que un caso escalado por contenido ilegal retiene el clip por
  un plazo **a definir por asesoría legal**. Si la Alerta tiene una Denuncia `ESCALADA` asociada,
  el job **no** borra el clip. Documentalo, **no inventes el plazo**.

## 4. Archivos

- `identidad/port/Almacenamiento.java` + `AlmacenamientoLocal.java`: agregar
  `void borrar(String referencia)` con la misma validación de ruta que `leer`.
- `aula/SesionService.java` (`subirEvidencia`), `aula/web/SesionController.java`,
  `aula/web/EvidenciaRequest.java` (se reemplaza por multipart), `aula/web/EvidenciaResponse.java`
  (no devolver la referencia interna).
- **Nuevo:** tipo de archivo de video, en el mismo espíritu que
  `identidad/model/TipoArchivoCredencial.java` (magic bytes): **WebM/Matroska**
  (`1A 45 DF A3`, que es lo que produce `MediaRecorder`) y **MP4** (`ftyp` en el offset 4).
- **Nuevo:** endpoint de lectura para el Admin, en `admin/web/ColasModeracionController`:
  `GET /api/admin/moderacion/alertas/{id}/clip`, gateado por `gate.requiereModeracion`. Mismas
  cabeceras que el endpoint de credenciales (`nosniff`, `CSP: sandbox`, `no-store`).
- **Nuevo:** endpoint del Adulto Responsable, `GET /api/alertas-seguridad/{id}/clip` en
  `seguridad/web/`, con las reglas de D2-bis.
- **Nuevo:** `seguridad/jobs/PurgaClipJob.java`, agendado al resolver la Alerta.
- **Límite de tamaño:** hoy `spring.servlet.multipart.max-file-size` es 5MB y
  `identidad/web/TutorController` lo reusa como límite de la credencial. Un clip de 30s puede
  superar 5MB. Separá los límites: `tinku.credencial.max-bytes` (5MB) y `tinku.evidencia.max-bytes`
  (20MB, más que suficiente para 30s de WebM a resolución de videollamada), y subí el global de
  multipart al mayor de los dos. `TutorController` pasa a leer su propiedad propia.

## 5. Pasos

1. `subirEvidencia` recibe `byte[]` + duración: valida participante (ya existe), tipo por magic
   bytes, tamaño, duración ≤ 30s (ya existe), que la Alerta exista (ya existe) y que **no tenga ya
   un clip** (no se pisa evidencia). Guarda por `Almacenamiento.guardar` y persiste la referencia.
2. Endpoint Admin y endpoint AR según §4 (el del AR: el usuario tiene que ser el Adulto
   Responsable del **beneficiario menor** de la Reserva de esa Sesión, y la Alerta tiene que estar
   `RESUELTA_BAJA`; en cualquier otro caso, **404**, para no revelar que existe).
3. `AlertaSeguridadService.resolver`: además de fijar `clipRetencionHasta`, agenda `PurgaClipJob` a
   esa fecha (mismo patrón de `programarSiFalta` que `SesionService`).
4. `PurgaClipJob`: `Almacenamiento.borrar(referencia)`, `clipUrl = null`. Idempotente (sin clip →
   no-op). Excepción de §3 para casos escalados.

## 6. Tests obligatorios (RED primero)

1. `subirEvidencia_conUrlDeclarada_yaNoSeAcepta` / `subirEvidencia_archivoQueNoEsVideo_422`.
2. `subirEvidencia_webmValido_seGuardaYNoSeExponeLaReferencia`.
3. `subirEvidencia_dosVeces_422` (no se pisa).
4. `clipAdmin_moderador200_soporte403_usuario403`.
5. `clipAR_alertaSancionada_200` / `clipAR_alertaReactivada_404` / `clipAR_otroAdulto_404`.
6. `purgaClip_borraElArchivoYLimpiaLaReferencia` (ejecutá el método del job directo).
7. Regresión de la credencial: el límite de 5MB sigue aplicando (`aud007_subidaDeCredencialMayorAlLimite_413`).

## 7. Criterios de aceptación

- Suite verde. `REGISTRO_FINDINGS.md` AUD-021 → `CERRADO`. Tasks T-AUD-022 tildada en los dos
  archivos.
- `Spec_M3` (US-6/T-M3-08) y `Spec_M9` (FR-SEC-004): documentar el upload, quién accede y el borrado.

## 8. NO tocar

- El buffer rotativo de 30s del cliente (T-M3-06, fuera de alcance).
- La lógica de resolución de la Alerta más allá de agendar la purga.
