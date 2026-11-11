package pl.rynek.shipping.api.dto;

import java.time.Instant;

import pl.rynek.shipping.shipments.Shipment.TrackingEvent;
import pl.rynek.shipping.shipments.ShipmentStatus;

/** Kształt {@code TrackingEvent} z rynek-frontend: {@code status} to polska etykieta, {@code location} bywa pusta. */
public record TrackingEventResponse(Instant at, ShipmentStatus status, String location) {

    public static TrackingEventResponse of(TrackingEvent e) {
        return new TrackingEventResponse(e.at(), e.status(), e.location());
    }
}
