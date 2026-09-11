package com.tinku.admin.web;

import jakarta.validation.constraints.NotNull;

/** Body de POST /api/admin/moderacion/credenciales/{id}/resolver (US-1, FR-ADM-001). */
public record RevisarCredencialRequest(
        @NotNull DecisionCredencial decision) {
}