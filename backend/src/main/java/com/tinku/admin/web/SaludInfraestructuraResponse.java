package com.tinku.admin.web;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * DTO de la pestaña "Salud de Infraestructura" del panel M8 — el contrato que el
 * frontend {@code AdminInfrastructurePanel} consume tal cual
 * ({@code SystemHealthDTO / ServiceStatus} del componente). Nombres {@code
 * isTestMode}/{@code hasSeedData} fijos para no romper el contrato del panel.
 */
public record SaludInfraestructuraResponse(
        @JsonProperty("isTestMode") boolean isTestMode,
        @JsonProperty("hasSeedData") boolean hasSeedData,
        ServiceStatus ocrEngine,
        ServiceStatus mercadoPago,
        ServiceStatus liveKit,
        @JsonProperty("iaMatching") ServiceStatus iaMatching,
        ServiceStatus database) {

    public record ServiceStatus(String name, String status, Long latencyMs, String lastChecked) {

        /** status: "operational" | "degraded" | "offline" (contrato del panel). */
        public static ServiceStatus of(String name, String status, Long latencyMs, Instant lastChecked) {
            return new ServiceStatus(name, status, latencyMs, lastChecked == null ? null : lastChecked.toString());
        }
    }
}