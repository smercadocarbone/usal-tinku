# ADR-M5-02 — Modelo de cobro A: cada Tutor conecta su MercadoPago por OAuth

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-25). Implementado en R3 de
`docs/spikes/DISENO-soluciones-revision-por-rol.md` §2.2. Cambia la fila "Pagos" del Registro
de Decisiones Técnicas de la Constitución.

## Contexto
- La preferencia de Checkout Pro se creaba con el token **de Tinku** y llevaba `marketplace_fee`.
- MercadoPago solo reparte con `marketplace_fee` cuando la preferencia se crea con el token del
  **vendedor**, obtenido por OAuth. Con el token propio, Tinku era el vendedor y cobraba el 100 %.
- Por eso `liberarAlTutor` solo verificaba el pago: **no le pagaba a nadie**.
- La revisión por rol lo encontró. Había dos caminos:
  - **A:** OAuth por Tutor.
  - **B:** recaudar todo y transferir a mano.
- El dueño eligió A: cada Tutor cobra en su propia cuenta, y Tinku factura solo su comisión.

## Decisión
1. **App de marketplace de MP.** Se configura con `MP_CLIENT_ID`, `MP_CLIENT_SECRET` y
   `MP_OAUTH_REDIRECT_URI` (= `https://<api>/api/pagos/mp/callback`).
   - Con `client-id` configurado, el modelo A está **activo**.
   - Sin él (dev, tests, piloto antes de crear la app), todo sigue con el token de la plataforma
     y nadie queda bloqueado.
2. **Conexión del Tutor.**
   - `GET /api/pagos/mp/conectar` arma la URL de `auth.mercadopago.com.ar/authorization` con
     `state` de un solo uso (10 min, en `pagos.oauth_estados`) y PKCE S256.
   - La vuelta (`/callback`, pública) canjea el `code` en `POST /oauth/token` y guarda los tokens.
   - Una cuenta de MP no se conecta a dos Tutores (409).
3. **Tokens cifrados.** AES-256-GCM con `TINKU_CLAVE_CIFRADO` (32 bytes en base64), fuera de la
   base, solo JDK. Sin clave, fail-closed.
4. **Token por operación.** Cada llamada a MP (preferencia, consulta, búsqueda, reembolsos) lleva el
   token del Tutor de esa Reserva. El webhook lo resuelve por el `user_id` del aviso; la
   conciliación y la vuelta del navegador, por la Reserva.
5. **Gate.** Con el modelo activo, un Tutor sin cuenta `CONECTADA`:
   - no aparece en el matching;
   - no se le puede reservar (422).
6. **Refresco.** Job Quartz diario (04:00) que renueva los tokens que vencen en menos de 30 días
   (duran ~180). Si falla, la cuenta queda `ERROR` y el Tutor recibe el aviso
   `MP_CUENTA_DESCONECTADA` (in-app y email).
7. **Escrow de 24 hs.** Pasa a ser una **ventana lógica**:
   - la plata entra a la cuenta del Tutor con los plazos de *su* cuenta;
   - durante la ventana, Tinku todavía reembolsa con el token del Tutor;
   - `liberado` = la ventana cerró.
8. **Desconexión bloqueada (409)** mientras haya reservas pendientes o futuras, o escrows en la
   ventana o pausados.

## Riesgos y verificación pendiente (sandbox, con dos usuarios de prueba)
- El contrato de `/oauth/token` y de `/authorization` se implementó según la documentación de MP.
  No se pudo consultar desde el entorno de desarrollo: **verificarlo en sandbox**.
- **Reembolso total:** confirmar que devuelve también el `marketplace_fee`.
- **Parcial del adicional (BR-PAG-11):** si MP prorratea el parcial entre vendedor y marketplace,
  parte del $770 (que es de Tinku) se le descuenta al Tutor. Si pasa, se devuelve por la cola
  manual de R4 con plata de Tinku.
- **Tutor que ya retiró la plata:** confirmar que MP le debita el saldo al reembolsar.
- **Revocación desde MP:** si el Tutor revoca el acceso desde MP con una ventana abierta, Tinku no
  puede reembolsar con su token. Va a la cola manual de Soporte, y Tinku pone la plata.

## Alternativa descartada
- **B, recaudación centralizada y transferencias manuales:** más simple de construir, pero:
  - todo el bruto entra a la cuenta de Tinku (riesgo fiscal: ingreso bruto, retenciones,
    Monotributo);
  - obliga a transferir a mano cada semana.
