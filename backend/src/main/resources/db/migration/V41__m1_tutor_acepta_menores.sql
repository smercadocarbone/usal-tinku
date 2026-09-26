-- UX de Mi cuenta: el Tutor elige si da clases a menores. Es una preferencia de exclusión: el
-- requisito de seguridad sigue siendo el CAP aprobado y vigente (FR-ID-026). Con false, no le
-- aparece a ningún menor en el matching, no se lo puede autorizar ni reservar para un menor.
-- Default true: ya sin CAP nadie da clases a menores, así que no abre nada nuevo.
ALTER TABLE identidad.usuarios ADD COLUMN acepta_menores BOOLEAN NOT NULL DEFAULT true;
