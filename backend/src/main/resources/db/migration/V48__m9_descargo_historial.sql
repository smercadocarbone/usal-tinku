-- R-F F-4 (T4-H8, decisión D-6): un segundo descargo ya no pisa al primero. descargo_texto
-- queda con el último (lo que ve el denunciado); el historial, con todos y su fecha, para el admin.
ALTER TABLE seguridad.denuncias ADD COLUMN descargo_historial TEXT;
UPDATE seguridad.denuncias SET descargo_historial = descargo_texto WHERE descargo_texto IS NOT NULL;
