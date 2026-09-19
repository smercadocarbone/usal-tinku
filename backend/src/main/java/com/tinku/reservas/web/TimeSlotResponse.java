package com.tinku.reservas.web;

/**
 * T-M4-12: contrato ya fijado por {@code DynamicTimeSlotPicker.tsx} del
 * frontend — nombres de campo camelCase tal cual el componente los consume,
 * {@code startTime}/{@code endTime} en ISO-8601 con offset (mismo formato que
 * {@link java.time.Instant#toString()}).
 */
public record TimeSlotResponse(String id, String startTime, String endTime, boolean isAvailable) {
}
