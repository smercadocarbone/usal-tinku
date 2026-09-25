-- T08/T09 (ADR-M3-04, Constitución v2.4 Art. V): adicional pago de resumen y grabación de solo audio.

-- T09: el adicional se contrata al reservar y su precio se congela como el de la sesión.
ALTER TABLE reservas.reservas ADD COLUMN resumen_contratado BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE reservas.reservas ADD COLUMN precio_adicional_resumen NUMERIC(10, 2);
ALTER TABLE reservas.reservas ADD CONSTRAINT reservas_adicional_resumen_check
    CHECK (resumen_contratado = (precio_adicional_resumen IS NOT NULL));

-- T09: el adicional va íntegro a la plataforma y se reembolsa aparte si el resumen falla (BR-PAG-11).
ALTER TABLE pagos.transacciones ADD COLUMN monto_adicional_resumen NUMERIC(10, 2) NOT NULL DEFAULT 0;
ALTER TABLE pagos.transacciones ADD COLUMN adicional_reembolsado_at TIMESTAMPTZ;

-- T08: una sesión con el adicional puede tener UN audio (referencia interna del almacenamiento).
ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN audio_referencia VARCHAR(300);
ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN audio_recibido_at TIMESTAMPTZ;
ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN audio_borrado_at TIMESTAMPTZ;

-- PT5: consentimiento expreso (Ley 25.326) — quién aceptó qué versión de qué cláusula y cuándo.
CREATE TABLE identidad.aceptaciones_clausula (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id  UUID NOT NULL REFERENCES identidad.usuarios(id),
    clausula    VARCHAR(50) NOT NULL,
    version     VARCHAR(20) NOT NULL,
    aceptada_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, clausula, version)
);
