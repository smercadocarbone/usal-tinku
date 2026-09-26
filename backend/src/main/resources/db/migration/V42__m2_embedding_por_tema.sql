-- M2 (2026-09-26): embedding por TEMA del catálogo. El score de un Tutor en /match pasa a ser
-- el de su tema más parecido a la consulta, en vez de un solo embedding hecho con todos sus
-- temas juntos (quien daba muchos temas quedaba "diluido" y salía más abajo en cada uno).
-- Lo llena el recompute del servicio Python (solo los temas elegidos por algún Tutor).
-- `embedding_fuente` guarda el texto con el que se hizo: si una migración cambia el nombre o
-- la descripción del tema, el recompute lo vuelve a embeber.
SET search_path TO matching, public;

ALTER TABLE matching.temas
    ADD COLUMN embedding VECTOR(384),
    ADD COLUMN embedding_fuente TEXT;
