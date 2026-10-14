# Modelos auto-hosteados del kill-switch (ADR-M3-01, ADR-M3-07)

Todo se sirve desde nuestro origen: ninguna clase carga código ni pesos de un CDN de terceros.

- `nsfw/model.json` y `nsfw/group1-shard1of1`: NSFWJS (MobileNetV2, entrada 224×224). Origen:
  `spikes/nsfw-classifier/models/` (se dejan los originales ahí).
- `tfjs-wasm/*.wasm`: binarios del backend WASM de TF.js. Salen de
  `node_modules/@tensorflow/tfjs-backend-wasm/dist/` de la versión **4.22.0**. **Se vuelven a copiar
  si se actualiza `@tensorflow/tfjs-backend-wasm`**, o el backend WASM no arranca (versión de JS y de
  `.wasm` desparejas).

`next.config.js` los sirve con `Cache-Control: public, max-age=31536000, immutable`: si cambian los
bytes, hay que cambiar también el nombre del archivo o la ruta.
