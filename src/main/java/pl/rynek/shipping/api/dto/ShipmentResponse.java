package pl.rynek.shipping.api.dto;

import java.time.Instant;
import java.util.UUID;

import pl.rynek.shipping.shipments.Carrier;
import pl.rynek.shipping.shipments.Shipment;
import pl.rynek.shipping.shipments.ShipmentStatus;

/**
 * Kształt {@code Shipment} z rynek-frontend plus {@code orderId}, {@code status}, {@code createdAt} i {@code simulatorUrl}
 * (strona symulatora przewoźnika, tylko w środowisku testowym).
 */
public record ShipmentResponse(UUID id, Carrier carrier, String trackingNumber, UUID orderId, ShipmentStatus status, Instant createdAt,
                               String simulatorUrl) {

    public static ShipmentResponse of(Shipment s, String simulatorUrl) {
        return new ShipmentResponse(s.getId(), s.getCarrier(), s.getTrackingNumber(), s.getOrderId(), s.getStatus(), s.getCreatedAt(),
                simulatorUrl);
    }
}
