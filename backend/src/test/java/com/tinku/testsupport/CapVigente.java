package com.tinku.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * T02: desde FR-ID-026 un Tutor necesita un CAP aprobado y vigente para cualquier cosa con
 * un menor (autorizar, reservar, abrir la sala). Los escenarios de menores que no prueban el
 * CAP le dan uno con esto, en vez de apagar el control.
 */
public final class CapVigente {

    private CapVigente() {
    }

    public static void para(JdbcTemplate jdbc, UUID tutorId) {
        jdbc.update("""
                INSERT INTO identidad.certificados_antecedentes_penales
                    (tutor_id, archivo_url, fecha_emision, vence_at, estado, revisado_at)
                VALUES (?, 'test/cap.pdf', CURRENT_DATE - 30, CURRENT_DATE + 335, 'APROBADO', now())
                """, tutorId);
    }
}
