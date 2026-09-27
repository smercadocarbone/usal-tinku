# ADR-M3-06 — Pizarra compartida propia sobre el canal de datos de LiveKit, solo entre adultos

**Estado:** Aceptado (2026-09-26). Pedido del dueño del producto (D-5 de
`docs/superpowers/plans/2026-09-26-mejoras-tutor-alumno.md`).

## Contexto
- En matemática, física o química, compartir pantalla no alcanza: el Tutor y el alumno necesitan
  escribir sobre lo mismo.
- El aula ya usa LiveKit y su canal de datos, que hoy se usa solo para el modo texto de emergencia.

## Opciones
| Opción | Peso en el cliente | Licencia | Servidor extra | Veredicto |
| --- | --- | --- | --- | --- |
| Excalidraw | ~1 MB gzip | MIT | No (con adaptador propio) | Descartada: pesa más que el resto del aula junta; la mayoría de sus funciones no se usan |
| tldraw | ~700 KB gzip | Comercial (marca de agua sin licencia paga) | No | Descartada: licencia |
| Yjs + y-websocket | chico | MIT | Sí (servidor de colaboración) | Descartada: un proceso más que operar (Art. VII) |
| **Canvas propio + `publishData`** | ~5 KB | propia | No | **Elegida** |

## Decisión
1. **Herramientas.** Un `<canvas>` con lápiz, 4 colores, goma, deshacer y borrar todo.
2. **Trazos.**
   - Viajan como segmentos con coordenadas normalizadas 0..1, así se ven igual en pantallas
     distintas.
   - Van por `publishData` confiable con `topic: "pizarra"`, agrupados cada ~50 ms.
3. **Quien entra tarde.** Pide `pizarra:sync` y el otro participante le responde con el estado
   completo.
4. **Nada se persiste** (Art. V):
   - ni en el servidor, ni en la base, ni en LiveKit;
   - al cerrar la clase, la pizarra desaparece;
   - "Descargar PNG" guarda una imagen local en la máquina de quien la pide, sin pasar por Tinku.
5. **Solo adultos (Art. II, D-5).**
   - El token de la sala devuelve `pizarraHabilitada = ningún participante es Menor`.
   - Con `false`, el botón no existe y los paquetes `pizarra` que lleguen se descartan en el
     cliente.
   - Se habilita con Menores recién cuando el kill-switch del cliente (T-M3-06) también analice
     el lienzo, por ejemplo publicándolo como una pista de video más.

## Consecuencias
- La pizarra es básica a propósito (sin formas, texto ni imágenes). Si hace falta más, se revisa
  este ADR.
- El canal de datos deja de ser exclusivo del modo texto: cada mensaje lleva `tipo` y cada
  receptor ignora lo que no espera (ya lo hacía).
