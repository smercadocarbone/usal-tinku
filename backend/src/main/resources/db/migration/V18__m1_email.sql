-- M1 — email de contacto en usuarios (cambio de contrato del alta).
-- Ver Spec_M1_Identidad_Perfiles.md. La columna queda nullable para no
-- romper filas existentes; las altas nuevas exigen email (validación de API).
ALTER TABLE identidad.usuarios ADD COLUMN email VARCHAR(255);

-- Único para emails no nulos (Postgres permite múltiples NULL).
CREATE UNIQUE INDEX uq_usuarios_email ON identidad.usuarios(email) WHERE email IS NOT NULL;