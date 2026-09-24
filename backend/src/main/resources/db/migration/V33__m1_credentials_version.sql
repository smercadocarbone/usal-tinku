-- AUD-027: versión de credenciales. Cambia al resetear/cambiar la contraseña y deja inválidos
-- los JWT emitidos antes (claim cv).
ALTER TABLE identidad.usuarios ADD COLUMN credentials_version INT NOT NULL DEFAULT 0;
