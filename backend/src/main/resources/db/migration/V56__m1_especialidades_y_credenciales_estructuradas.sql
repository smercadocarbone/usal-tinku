-- ADR-M1-06 (chunk E1, T-ESP-01): especialidades del Tutor y verificación de títulos contra el
-- registro oficial. Todo el uso de estas columnas queda detrás de tinku.especialidades.habilitado
-- (apagado por defecto): con el flag apagado nada las lee y el sistema se comporta como antes.

-- 1. Especialidades: materia (del catálogo de temas de M2) + nivel máximo que puede enseñar.
--    De 1 a 3 por Tutor (lo controla el servicio). El estado (verificada / respaldo documental /
--    declarada) no se guarda: sale de las credenciales aprobadas que la cubren.
CREATE TABLE identidad.especialidades_tutor (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id      UUID NOT NULL REFERENCES identidad.usuarios(id),
    materia       VARCHAR(100) NOT NULL,
    nivel_maximo  VARCHAR(20) NOT NULL CHECK (nivel_maximo IN ('PRIMARIO', 'SECUNDARIO', 'UNIVERSITARIO')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_especialidad_tutor_materia UNIQUE (tutor_id, materia)
);

CREATE INDEX idx_especialidades_tutor ON identidad.especialidades_tutor(tutor_id);

-- 2. Tipos nuevos de credencial (SPIKE §4.2). Los viejos se siguen admitiendo en la base: las
--    credenciales ya cargadas no se tocan.
ALTER TABLE identidad.credenciales_academicas
    DROP CONSTRAINT credenciales_academicas_tipo_documento_check;
ALTER TABLE identidad.credenciales_academicas
    ADD CONSTRAINT credenciales_academicas_tipo_documento_check CHECK (tipo_documento IN (
        'TITULO', 'CERTIFICADO_ANALITICO', 'MATRICULA',
        'SECUNDARIO', 'TERCIARIO_NO_DOCENTE', 'PROFESORADO', 'UNIVERSITARIO',
        'ALUMNO_UNIVERSITARIO', 'MATRICULA_PROFESIONAL', 'OTRO'));

-- 3. Datos estructurados, triaje automático y decisión del Admin.
ALTER TABLE identidad.credenciales_academicas
    ADD COLUMN institucion          VARCHAR(200),
    ADD COLUMN denominacion         VARCHAR(200),
    ADD COLUMN anio_egreso          INT CHECK (anio_egreso BETWEEN 1900 AND 2100),
    ADD COLUMN es_digital           BOOLEAN,
    ADD COLUMN enlace_verificacion  VARCHAR(500),
    -- OCR del documento contra el nombre del DNI verificado. NULL = no se pudo comparar
    -- (p. ej. un PDF sin texto); FALSE = no coincide (marca para el Admin, nunca rechaza sola).
    ADD COLUMN nombre_coincide      BOOLEAN,
    ADD COLUMN metodo_verificacion  VARCHAR(20)
        CHECK (metodo_verificacion IN ('REGISTRO_OFICIAL', 'SOLO_DOCUMENTO')),
    ADD COLUMN motivo_rechazo       VARCHAR(30)
        CHECK (motivo_rechazo IN ('ILEGIBLE', 'NO_ES_TITULO', 'NOMBRE_NO_COINCIDE',
                                  'NO_FIGURA_EN_REGISTRO', 'DATOS_NO_COINCIDEN',
                                  'ENLACE_INVALIDO', 'OTRO')),
    ADD COLUMN detalle_rechazo      VARCHAR(500);

-- 4. Qué especialidades del Tutor cubre una credencial aprobada (lo elige el Admin al aprobar).
--    Si el Tutor borra o cambia la especialidad, la cobertura se va con ella.
CREATE TABLE identidad.credencial_especialidades (
    credencial_id    UUID NOT NULL REFERENCES identidad.credenciales_academicas(id),
    especialidad_id  UUID NOT NULL REFERENCES identidad.especialidades_tutor(id) ON DELETE CASCADE,
    PRIMARY KEY (credencial_id, especialidad_id)
);

CREATE INDEX idx_credencial_especialidades_esp ON identidad.credencial_especialidades(especialidad_id);

-- 5. ADR-M1-06, Consecuencias: las credenciales ya aprobadas quedan como "revisadas
--    manualmente (solo documento)" hasta que el Admin las re-verifique contra el registro.
UPDATE identidad.credenciales_academicas
   SET metodo_verificacion = 'SOLO_DOCUMENTO'
 WHERE estado = 'APROBADO';
