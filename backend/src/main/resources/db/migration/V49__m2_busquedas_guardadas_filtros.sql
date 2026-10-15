-- R-E E-2 (T1-6): la búsqueda guardada conserva sus filtros (nivel, materia, nombre, precio
-- máximo) y no se duplica. huella = sha256 del texto normalizado + filtros ordenados (la calcula
-- la aplicación); para las filas existentes alcanza con el texto en minúsculas.
ALTER TABLE matching.busquedas_guardadas
    ADD COLUMN filtros JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN huella  VARCHAR(64);

UPDATE matching.busquedas_guardadas SET huella = md5(lower(trim(texto_busqueda))) WHERE huella IS NULL;

-- Antes del índice único: queda la más vieja de cada (usuario, huella).
DELETE FROM matching.busquedas_guardadas b
 USING matching.busquedas_guardadas vieja
 WHERE b.usuario_id = vieja.usuario_id
   AND b.huella = vieja.huella
   AND (b.created_at, b.id) > (vieja.created_at, vieja.id);

ALTER TABLE matching.busquedas_guardadas ALTER COLUMN huella SET NOT NULL;
CREATE UNIQUE INDEX uq_busquedas_guardadas_usuario_huella
    ON matching.busquedas_guardadas(usuario_id, huella);
