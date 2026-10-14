# ADR-M5-06 — Precio del adicional de resumen: costo + 50 % según la duración (modifica BR-PAG-11)

**Estado:** Aceptado (decisión del dueño del producto, 2026-10-14; tesis Cap. 5 vigente). Implementa
T-TES-17 (`docs/superpowers/specs/tesis/T17-precio-resumen-por-duracion.md`). Reemplaza el monto fijo
de $770 de PT5/BR-PAG-11; no cambia quién puede contratar ni grabar (ADR-M3-04, ADR-M3-05).

## Decisión
1. `precio(d) = ceil10( margen × (c_t·d + c_e·τ·d + c_s·σ) × tipo_cambio )`, con `d` en minutos.
   Parámetros por defecto (tesis, tarifas de lista de OpenAI): `c_t` 0,0030 USD/min, `c_e` 2,5·10⁻⁶,
   `τ` 217 tokens/min, `c_s` 1·10⁻⁵, `σ` 840 tokens, margen 1,50, tipo de cambio $1.535/USD.
   Todos son configuración (`tinku.resumen.adicional.*`), no constantes.
2. `d` es `Reserva.duracionMinutos` (la agendada). Se congela al reservar, como el precio de la sesión.
3. Redondeo hacia arriba a la decena de pesos, en `BigDecimal`.
4. Resultado: $270 (30 min), $510 (60), $760 (90), $1.000 (120), $1.490 (180).
5. El tipo de cambio del adicional es independiente del piso tarifario (PT3, $6.140); ambos se revisan
   juntos una vez por mes.
6. Las tarifas de OpenAI cambian: verificarlas en la página oficial antes de habilitar el adicional.
7. Sin backfill: las Reservas anteriores conservan el monto que tenían congelado.

## Lo que no cambia
Menores nunca (Art. II), las cuatro condiciones para grabar, el reembolso íntegro del adicional si el
resumen falla (BR-PAG-11, R4) y la regla de que la comisión de plataforma no se calcula sobre el adicional.
