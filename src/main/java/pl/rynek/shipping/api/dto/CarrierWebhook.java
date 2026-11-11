package pl.rynek.shipping.api.dto;

import java.time.Instant;

/**
 * Treść webhooka przewoźnika (symulator): {@code {"trackingNumber": "...", "status": "PICKED_UP", "location": "...", "at": "..."}}.
 * {@code status} to nazwa stałej {@code ShipmentStatus}; {@code location} i {@code at} są opcjonalne.
 */
public record CarrierWebhook(String trackingNumber, String status, String location, Instant at) {
}
