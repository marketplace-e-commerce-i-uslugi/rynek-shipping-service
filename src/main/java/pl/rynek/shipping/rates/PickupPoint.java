package pl.rynek.shipping.rates;

/** Kształt {@code PickupPoint} z rynek-frontend (commerce.ts). */
public record PickupPoint(String id, String name, String address, double distanceKm) {
}
